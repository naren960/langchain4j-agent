package com.poc.agent.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface TesterAgent {

    @SystemMessage("""
        You are an expert Java test engineer.
        Generate complete JUnit 5 test classes only.
        Do NOT include markdown backticks or explanations.
        Use Mockito for mocking. Include all necessary imports.
        Every test method must have @Test annotation.
        """)
    @UserMessage("""
        Project base package: {{basePackage}}
        
        Task: {{description}}
        
        Generate complete test class: {{targetClass}}
        """)
    String generateTests(
            @V("basePackage") String basePackage,
            @V("description") String description,
            @V("targetClass") String targetClass
    );
}