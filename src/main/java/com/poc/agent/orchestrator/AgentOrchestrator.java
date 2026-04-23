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
    private InstructionPlan currentPlan;

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
        this.currentPlan = plan;
        log.info("🚀 Starting orchestration: {}",
                plan.getProject().getName());

        taskResults.clear();
        generatedCode.clear();

        int maxRetries = plan.getPipeline().getMaxRetries();

        for (InstructionPlan.Task task : plan.getTasks()) {

            if (!areDependenciesMet(task)) {
                log.warn("⏭️ Skipping {} — dependencies not met",
                        task.getId());
                taskResults.put(task.getId(),
                        TaskResult.failure(task.getId(),
                                "Dependencies not met"));
                continue;
            }

            log.info("▶️ Executing task: {} [{}]",
                    task.getId(), task.getType());

            switch (task.getType()) {
                case CODE_GENERATION ->
                        handleGeneration(task, plan.getProject(), maxRetries);
                case TEST_GENERATION ->
                        handleGeneration(task, plan.getProject(), maxRetries);
                case TEST_EXECUTION ->
                        handleTestExecution(task, plan.getProject(), maxRetries);
                case GIT_PUSH ->
                        handleGitPush(task, plan);
            }
        }

        printSummary();
    }

    // ── Generation — no compile check per task ────────────────
    // Just generate and write. Compilation happens in TEST_EXECUTION.
    private void handleGeneration(InstructionPlan.Task task,
                                  InstructionPlan.Project project, int maxRetries) {

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            log.info("▶️ Task: {} | Attempt {}/{}",
                    task.getId(), attempt, maxRetries);

            try {
                String code = (attempt == 1)
                        ? generateFresh(task, project)
                        : reviewerAgent.reviewAndFix(
                        task.getDescription(),
                        generatedCode.getOrDefault(task.getId(), ""),
                        "Previous generation was invalid Java code.",
                        task.getTargetClass());

                // sanitize() throws if LLM returned explanation text
                String filePath = writeFile(task, project, code);
                generatedCode.put(task.getId(), code);

                taskResults.put(task.getId(),
                        TaskResult.success(task.getId(), filePath));
                log.info("✅ Task {} generated on attempt {}",
                        task.getId(), attempt);
                return;

            } catch (IllegalArgumentException e) {
                // LLM returned explanation text — retry
                log.warn("⚠️ Invalid LLM output on attempt {}: {}",
                        attempt, e.getMessage());

            } catch (Exception e) {
                log.error("❌ Attempt {} failed: {}",
                        attempt, e.getMessage());
            }
        }

        taskResults.put(task.getId(),
                TaskResult.failure(task.getId(),
                        "Failed to generate valid code after "
                                + maxRetries + " attempts"));
        log.error("❌ Task {} failed after all retries", task.getId());
    }

    // ── Test execution — compile + test + self-heal loop ─────
    private void handleTestExecution(InstructionPlan.Task task,
                                     InstructionPlan.Project project, int maxRetries) {

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            log.info("🧪 Compile + Test | Attempt {}/{}",
                    attempt, maxRetries);

            // Step 1 — compile all generated classes together
            TestExecutionResult compileResult =
                    testExecutor.compileOnly(project.getOutputDir());

            if (!compileResult.isPassed()) {
                log.warn("⚠️ Compile failed on attempt {}: {}",
                        attempt, compileResult.getSummary());

                if (attempt < maxRetries) {
                    healCompileErrors(compileResult.getSummary(),
                            project);
                }
                continue;
            }

            // Step 2 — run tests only if compile passed
            TestExecutionResult testResult =
                    testExecutor.runTests(project.getOutputDir());

            if (testResult.isPassed()) {
                taskResults.put(task.getId(),
                        TaskResult.success(task.getId(),
                                testResult.getSummary()));
                log.info("✅ All tests passed on attempt {}", attempt);
                return;
            }

            log.warn("⚠️ Tests failed on attempt {}: {}",
                    attempt, testResult.getSummary());

            if (attempt < maxRetries) {
                healTestErrors(testResult.getSummary(), project);
            }
        }

        taskResults.put(task.getId(),
                TaskResult.failure(task.getId(),
                        "Compile + test failed after "
                                + maxRetries + " attempts"));
        log.error("❌ TEST_EXECUTION failed after all retries");
    }

    // ── Heal compile errors across all generated classes ──────
    private void healCompileErrors(String errors,
                                   InstructionPlan.Project project) {
        log.info("🔁 Healing compile errors...");

        // Ask reviewer to fix each generated code file
        // that is referenced in the error output
        currentPlan.getTasks().stream()
                .filter(t -> t.getType() ==
                        InstructionPlan.TaskType.CODE_GENERATION)
                .filter(t -> errors.contains(t.getTargetClass()))
                .forEach(t -> {
                    try {
                        log.info("🔧 Fixing {} due to compile error",
                                t.getTargetClass());
                        String fixed = reviewerAgent.reviewAndFix(
                                t.getDescription(),
                                generatedCode.getOrDefault(t.getId(), ""),
                                errors,
                                t.getTargetClass());
                        writeFile(t, project, fixed);
                        generatedCode.put(t.getId(), fixed);
                    } catch (Exception e) {
                        log.error("Failed to heal {}: {}",
                                t.getTargetClass(), e.getMessage());
                    }
                });
    }

    // ── Heal test errors ──────────────────────────────────────
    private void healTestErrors(String errors,
                                InstructionPlan.Project project) {
        log.info("🔁 Healing test errors...");

        currentPlan.getTasks().stream()
                .filter(t -> t.getType() ==
                        InstructionPlan.TaskType.TEST_GENERATION)
                .forEach(t -> {
                    try {
                        log.info("🔧 Fixing {} due to test failure",
                                t.getTargetClass());
                        String fixed = reviewerAgent.reviewAndFix(
                                t.getDescription(),
                                generatedCode.getOrDefault(t.getId(), ""),
                                errors,
                                t.getTargetClass());
                        writeFile(t, project, fixed);
                        generatedCode.put(t.getId(), fixed);
                    } catch (Exception e) {
                        log.error("Failed to heal {}: {}",
                                t.getTargetClass(), e.getMessage());
                    }
                });
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
            case TEST_GENERATION -> {

                String serviceClass = findClassNameByLayer(
                        "service", project);
                String modelClass = findClassNameByLayer(
                        "model", project);

                yield testerAgent.generateTests(
                    project.getBasePackage(),
                    task.getTargetClass(),
                    task.getDescription(),
                    serviceClass, modelClass
                    );
            }
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
            case TEST_GENERATION ->  {
                String serviceClass = findClassNameByLayer(
                        "service", project);
                String modelClass = findClassNameByLayer(
                        "model", project);
                yield fileWriter.writeTestFile(
                    project.getOutputDir(), project.getBasePackage(),
                    task.getTargetClass(), code, serviceClass, modelClass);
            }
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

    private String findClassNameByLayer(String layer,
                                        InstructionPlan.Project project) {
        return currentPlan.getTasks().stream()
                .filter(t -> t.getType() ==
                        InstructionPlan.TaskType.CODE_GENERATION)
                .filter(t -> layer.equalsIgnoreCase(t.getLayer()))
                .map(InstructionPlan.Task::getTargetClass)
                .findFirst()
                .orElse("");
    }

}