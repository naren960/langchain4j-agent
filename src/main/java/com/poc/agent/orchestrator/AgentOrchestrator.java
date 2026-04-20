package com.poc.agent.orchestrator;

import com.poc.agent.agent.CoderAgent;
import com.poc.agent.agent.ReviewerAgent;
import com.poc.agent.agent.TesterAgent;
import com.poc.agent.model.TestExecutionResult;
import com.poc.agent.service.GitService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import com.poc.agent.model.InstructionPlan;
import com.poc.agent.model.TaskResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.poc.agent.service.FileWriterService;
import com.poc.agent.service.TestExecutorService;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class AgentOrchestrator {

    private final CoderAgent coderAgent;
    private final TesterAgent testerAgent;
    private final FileWriterService fileWriter;
    private final TestExecutorService testExecutor;
    private final GitService gitService;
    private final ReviewerAgent reviewerAgent;
    private final Map<String, TaskResult> taskResults = new LinkedHashMap<>();
    private final Map<String, String> generatedCode = new HashMap<>();

    public AgentOrchestrator(
            @Qualifier("coderChatModel") ChatLanguageModel coderChatModel,
            @Qualifier("testerChatModel") ChatLanguageModel testerChatModel,
            @Qualifier("reviewerChatModel") ChatLanguageModel reviewerChatModel,
            FileWriterService fileWriter, GitService gitService,
            TestExecutorService testExecutor) {

        this.coderAgent = AiServices.create(CoderAgent.class, coderChatModel);
        this.testerAgent = AiServices.create(TesterAgent.class, testerChatModel);
        this.fileWriter = fileWriter;
        this.testExecutor = testExecutor;
        this.gitService = gitService;
        this.reviewerAgent = AiServices.create(ReviewerAgent.class, reviewerChatModel);
    }

    public void execute(InstructionPlan plan) {
        log.info("Starting orchestration for project: {}",
                plan.getProject().getName());

        int maxRetries = plan.getPipeline().getMaxRetries();

        for (InstructionPlan.Task task : plan.getTasks()) {

            // Wait for dependencies to complete
            if (!areDependenciesMet(task)) {
                log.warn("Skipping {} — dependencies not met", task.getId());
                continue;
            }

            log.info("Executing task: {} [{}]", task.getId(), task.getType());

            switch (task.getType()) {
                case CODE_GENERATION -> handleWithRetry(task, plan.getProject(), maxRetries);
                case TEST_GENERATION -> handleWithRetry(task, plan.getProject(), maxRetries);
                case TEST_EXECUTION  -> handleTestExecution(task, plan, maxRetries);
                case GIT_PUSH -> handleGitPush(task, plan);
            }
        }
        printSummary();
    }

    // ── Retry loop for code + test generation ────────────────
    private void handleWithRetry(InstructionPlan.Task task,
                                 InstructionPlan.Project project, int maxRetries) {

        String lastCode = null;
        String lastErrors = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            log.info("Task: {} | Attempt {}/{}",
                    task.getId(), attempt, maxRetries);

            try {
                // First attempt → use CoderAgent/TesterAgent
                // Subsequent attempts → use ReviewerAgent to self-heal
                String code = (attempt == 1)
                        ? generateFresh(task, project)
                        : reviewerAgent.reviewAndFix(
                        task.getDescription(),
                        lastCode,
                        lastErrors,
                        task.getTargetClass());

                String filePath = writeFile(task, project, code);
                lastCode = code;
                generatedCode.put(task.getId(), code);

                // Quick compile check before marking success
                TestExecutionResult compileCheck =
                        testExecutor.compileOnly(project.getOutputDir());

                if (compileCheck.isPassed()) {
                    taskResults.put(task.getId(),
                            TaskResult.success(task.getId(), filePath));
                    log.info("Task {} succeeded on attempt {}",
                            task.getId(), attempt);
                    return;
                } else {
                    lastErrors = compileCheck.getSummary();
                    log.warn("Compile failed on attempt {}: {}",
                            attempt, lastErrors);
                }

            } catch (Exception e) {
                lastErrors = e.getMessage();
                log.error("Attempt {} failed: {}", attempt, e.getMessage());
            }
        }

        // All retries exhausted
        taskResults.put(task.getId(),
                TaskResult.failure(task.getId(),
                        "Failed after " + maxRetries + " attempts. Last error: "
                                + lastErrors));
        log.error("Task {} failed after all retries", task.getId());
    }

    private void handleCodeGeneration(
            InstructionPlan.Task task, InstructionPlan.Project project) {
        try {
            String code = coderAgent.generateCode(
                    project.getBasePackage(),
                    task.getLayer(),
                    task.getDescription(),
                    task.getTargetClass()
            );

            String filePath = fileWriter.writeJavaFile(
                    project.getOutputDir(),
                    project.getBasePackage(),
                    task.getLayer(),
                    task.getTargetClass(),
                    code
            );

            taskResults.put(task.getId(),
                    TaskResult.success(task.getId(), filePath));
            log.info("Code generated: {}", filePath);

        } catch (Exception e) {
            taskResults.put(task.getId(),
                    TaskResult.failure(task.getId(), e.getMessage()));
            log.error("Code generation failed for {}: {}",
                    task.getId(), e.getMessage());
        }
    }

    private void handleTestGeneration(
            InstructionPlan.Task task, InstructionPlan.Project project) {
        try {
            String testCode = testerAgent.generateTests(
                    project.getBasePackage(),
                    task.getDescription(),
                    task.getTargetClass()
            );

            String filePath = fileWriter.writeTestFile(
                    project.getOutputDir(),
                    project.getBasePackage(),
                    task.getTargetClass(),
                    testCode
            );

            taskResults.put(task.getId(),
                    TaskResult.success(task.getId(), filePath));
            log.info("Tests generated: {}", filePath);

        } catch (Exception e) {
            taskResults.put(task.getId(),
                    TaskResult.failure(task.getId(), e.getMessage()));
            log.error("Test generation failed: {}", e.getMessage());
        }
    }

    private void handleTestExecution(
            InstructionPlan.Task task, InstructionPlan plan, int maxRetries) {

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            log.info("Running tests | Attempt {}/{}", attempt, maxRetries);

            TestExecutionResult result = testExecutor.runTests(plan.getProject().getOutputDir());

            if (result.isPassed()) {
                taskResults.put(task.getId(),
                        TaskResult.success(task.getId(), result.getSummary()));
                log.info("All tests passed on attempt: {}", attempt);
                return;
            }

            log.warn("Test failed on attempt: {}, {}", attempt, result.getSummary());

            if (attempt < maxRetries) {
                log.info("Asking ReviewerAgent to fix failing tests...");

                plan.getTasks().stream()
                        .filter(t -> t.getType().equals(InstructionPlan.TaskType.TEST_GENERATION))
                        .forEach(testTask -> {
                            String fixedTest = reviewerAgent.reviewAndFix(
                                    testTask.getDescription(),
                                    generatedCode.getOrDefault(testTask.getId(), ""),
                                    result.getSummary(),
                                    testTask.getTargetClass());
                                try {
                                    writeFile(testTask, plan.getProject(), fixedTest);
                                    generatedCode.put(testTask.getId(), fixedTest);
                                } catch (Exception e) {
                                    log.error("Failed to write fixed test: {}", e.getMessage());
                                }
                        });
            }

            taskResults.put(task.getId(),
                    TaskResult.failure(task.getId(), "Tests failed after " + maxRetries + " attempts"));
        }
    }

    private boolean areDependenciesMet(InstructionPlan.Task task) {
        if (task.getDependsOn() == null) return true;
        return task.getDependsOn().stream()
                .allMatch(depId -> taskResults.containsKey(depId)
                        && taskResults.get(depId).isSuccess());
    }

    private String generateFresh(InstructionPlan.Task task,
                                 InstructionPlan.Project project) {
        return switch (task.getType()) {
            case CODE_GENERATION -> coderAgent.generateCode(
                    project.getBasePackage(), task.getLayer(),
                    task.getDescription(), task.getTargetClass());
            case TEST_GENERATION -> testerAgent.generateTests(
                    project.getBasePackage(),
                    task.getDescription(), task.getTargetClass());
            default -> throw new IllegalArgumentException(
                    "Cannot generate fresh for type: " + task.getType());
        };
    }

    private String writeFile(InstructionPlan.Task task,
                             InstructionPlan.Project project, String code) throws IOException {
        return switch (task.getType()) {
            case CODE_GENERATION -> fileWriter.writeJavaFile(
                    project.getOutputDir(), project.getBasePackage(),
                    task.getLayer(), task.getTargetClass(), code);
            case TEST_GENERATION -> fileWriter.writeTestFile(
                    project.getOutputDir(), project.getBasePackage(),
                    task.getTargetClass(), code);
            default -> throw new IllegalArgumentException(
                    "Cannot write file for type: " + task.getType());
        };
    }

    private void printSummary() {
        log.info("\n========= ORCHESTRATION SUMMARY =========");
        taskResults.forEach((id, result) ->
                log.info("{} — {} | {}",
                        result.isSuccess() ? "✅" : "❌", id, result.getDetail())
        );
    }

    private void handleGitPush(InstructionPlan.Task task, InstructionPlan plan) {
        try {
            if(plan.getPipeline().isPushToGithub()) {
                log.info("Git push is disabled in pipeline config");
                taskResults.put(task.getId(), TaskResult.success(task.getId(), "Git push skipped"));
                return;
            }

            String branchName = gitService.pushGeneratedCode(
                    plan.getProject().getOutputDir(), plan.getPipeline().getWorkingBranch());

            if (plan.getPipeline().isCreatePR()) {
                gitService.createPullRequest(
                        branchName,
                        plan.getPipeline().getTargetBranch(),
                        plan.getProject().getName());
            }

            taskResults.put(task.getId(), TaskResult.success(task.getId(), "Pushed to branch: " + branchName));
        } catch (Exception e) {
            taskResults.put(task.getId(), TaskResult.failure(task.getId(), e.getMessage()));
            log.error("Git push failed: {}", e.getMessage());
        }
    }

}