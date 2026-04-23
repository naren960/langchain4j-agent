package com.poc.agent.service;

import com.poc.agent.model.TestExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;

@Service
@Slf4j
public class TestExecutorService {

    @Value("${gradle.installation-dir:}")
    private String gradleInstallationDir;

    public TestExecutionResult runTests(String projectDir) {
        return invoke(projectDir, "test");
    }

    public TestExecutionResult compileOnly(String projectDir) {
        return invoke(projectDir, "compileJava");
    }


    public TestExecutionResult invoke(String projectDir, String task) {

        File projectDirFile = new File(projectDir);

        if (!projectDirFile.exists()) {
            return new TestExecutionResult(false,
                    "Project dir not found: " + projectDir);
        }

        File gradlew = new File(projectDirFile,"gradlew");

        if (!gradlew.exists()) {
            return new TestExecutionResult(false,
                    "gradlew not found in: " + projectDir);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        try(ProjectConnection connection = buildConnection(projectDirFile)) {

            connection.newBuild()
                    .forTasks(task)
                    .setStandardOutput(outputStream)
                    .setStandardError(error)
                    .withArguments("--info", "--stacktrace")
                    .run();

            log.debug("Gradle {} output:\n{}", task, outputStream);
            return new TestExecutionResult(true, task + " successful");
        } catch (BuildException e) {

            String fullOutput = outputStream.toString();
            String errorOutput = error.toString();

            log.warn("Gradle {} failed" +
                            "\n--- STDOUT ---\n{}" +
                            "\n--- STDERR ---\n{}" +
                            "\n--- EXCEPTION ---\n{}",
                    task, fullOutput, errorOutput, e.getMessage());

            return new TestExecutionResult(false,
                    task + " failed \n" + errorOutput);

        } catch (Exception e) {
            log.error(" Gradle invocation error: {}", e.getMessage());
            return new TestExecutionResult(false,
                    "Gradle error: " + e.getMessage());
        }
    }

    private ProjectConnection buildConnection(File projectDir) {
        GradleConnector connector = GradleConnector.newConnector()
                .forProjectDirectory(projectDir);


        // Use specific Gradle installation if configured
        // Otherwise Tooling API downloads the right version automatically
        if (gradleInstallationDir != null && !gradleInstallationDir.isBlank()) {
            connector.useInstallation(new File(gradleInstallationDir));
        } else {
            // Reads the version from generated project's gradle/wrapper/gradle-wrapper.properties
            connector.useBuildDistribution();
        }
        return connector.connect();
    }
}