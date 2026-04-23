package com.poc.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class GitService {

    private String repoPath;

    private String githubToken;

    private String repoOwner;

    private String repoName;

    public String pushGeneratedCode(String outputDir, String workingBranch) throws Exception {

        String branchName = workingBranch + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

        runGitCommand("git", "init", outputDir);
        runGitCommand("git", "-C", outputDir,
                "remote", "add", "origin",
                buildRemoteUrl());
        runGitCommand("git", "-C", outputDir,
                "checkout", "-b", branchName);
        runGitCommand("git", "-C", outputDir,
                "add", ".");
        runGitCommand("git", "-C", outputDir,
                "commit", "-m",
                "feat: AI agent generated code [" + branchName + "]");
        runGitCommand("git", "-C", outputDir,
                "push", "origin", branchName);

        log.info("✅ Pushed to branch: {}", branchName);
        return branchName;


    }

    // Creates a PR via GitHub REST API
    public void createPullRequest(String branchName,
                                  String targetBranch, String projectName) throws Exception {

        String prBody = """
            ## AI Agent Generated PR

            **Project:** %s
            **Branch:** %s
            **Generated at:** %s

            ### Changes
            - Auto-generated code via LangChain4j + Ollama
            - All tests passed before this PR was created

            > Review carefully before merging.
            """.formatted(projectName, branchName,
                LocalDateTime.now().toString());

        ObjectMapper objectMapper = new ObjectMapper();
        String payload = """
            {
              "title": "feat: AI agent generated - %s",
              "body": %s,
              "head": "%s",
              "base": "%s"
            }
            """.formatted(
                projectName,
                objectMapper.writeValueAsString(prBody),
                branchName,
                targetBranch);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/"
                        + repoOwner + "/" + repoName + "/pulls"))
                .header("Authorization", "Bearer " + githubToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 201) {
            log.info(" PR created successfully");
            // Extract PR URL from response
            String prUrl = objectMapper.readTree(response.body())
                    .get("html_url").asText();
            log.info("🔗 PR URL: {}", prUrl);
        } else {
            throw new RuntimeException(
                    "PR creation failed: " + response.body());
        }
    }

    private void runGitCommand(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException(
                    "Git command failed: " + String.join(" ", command)
                            + "\n" + output);
        }
        log.debug("Git: {}", output.trim());
    }

    private String buildRemoteUrl() {
        return "https://" + githubToken
                + "@github.com/" + repoOwner + "/" + repoName + ".git";
    }

}
