package com.poc.agent.model;

import lombok.Getter;

@Getter
public class TaskResult {

    private final String taskId;
    private final boolean success;
    private final String detail;

    private TaskResult(String taskId, boolean success, String detail) {
        this.taskId = taskId;
        this.success = success;
        this.detail = detail;
    }

    public static TaskResult success(String taskId, String detail) {
        return new TaskResult(taskId, true, detail);
    }

    public static TaskResult failure(String taskId, String detail) {
        return new TaskResult(taskId, false, detail);
    }

    @Override
    public String toString() {
        return String.format("[%s] taskId=%s | detail=%s",
                success ? "SUCCESS" : "FAILURE", taskId, detail);
    }
}