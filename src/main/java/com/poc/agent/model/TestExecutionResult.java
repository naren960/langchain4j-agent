package com.poc.agent.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TestExecutionResult {

    private final boolean passed;
    private final String summary;
}