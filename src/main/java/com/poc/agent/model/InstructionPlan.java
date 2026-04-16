package com.poc.agent.model;

import lombok.Data;

import java.util.List;

@Data
public class InstructionPlan {
    private Project project;
    private List<Task> tasks;
    private Pipeline pipeline;

    @Data
    public static class Project {
        private String name;
        private String basePackage;
        private String outputDir;
    }

    @Data
    public static class Pipeline {
        private int maxRetries = 3;
        private boolean pushToGithub = false;
        private boolean createPR = false;
        private String targetBranch = "main";
        private String workingBranch = "agent/generated-";
    }

    @Data
    public static class Task {
        private String id;
        private TaskType type;
        private String description;
        private String targetClass;
        private String layer;
        private String testsFor;
        private List<String> dependsOn;
    }

    public enum TaskType {
        CODE_GENERATION, TEST_GENERATION, TEST_EXECUTION, GIT_PUSH
    }
}