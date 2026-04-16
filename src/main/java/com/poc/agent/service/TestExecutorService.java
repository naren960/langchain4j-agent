package com.poc.agent.service;

import com.poc.agent.model.TestExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.shared.invoker.*;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
@Slf4j
public class TestExecutorService {

    public com.poc.agent.model.TestExecutionResult runTests(String projectDir) {
        try {
            InvocationRequest request = new DefaultInvocationRequest();
            request.setPomFile(new File(projectDir + "/pom.xml"));
            request.setGoals(List.of("test"));
            request.setBatchMode(true);

            InvocationOutputHandler outputHandler = log::info;
            request.setOutputHandler(outputHandler);

            Invoker invoker = new DefaultInvoker();
            InvocationResult result = invoker.execute(request);

            boolean passed = result.getExitCode() == 0;
            return new com.poc.agent.model.TestExecutionResult(passed,
                    passed ? "All tests passed ✅" : "Tests failed ❌");

        } catch (Exception e) {
            return new com.poc.agent.model.TestExecutionResult(false, "Execution error: " + e.getMessage());
        }
    }

    public TestExecutionResult compileOnly(String projectDir) {
        try {
            InvocationRequest request = new DefaultInvocationRequest();
            request.setPomFile(new File(projectDir + "/pom.xml"));
            request.setGoals(List.of("compile"));   // ← compile only, no tests
            request.setBatchMode(true);

            InvocationResult result = new DefaultInvoker().execute(request);
            boolean passed = result.getExitCode() == 0;
            return new TestExecutionResult(passed,
                    passed ? "Compilation successful" : "Compilation failed");

        } catch (Exception e) {
            return new TestExecutionResult(false,
                    "Compile error: " + e.getMessage());
        }
    }
}