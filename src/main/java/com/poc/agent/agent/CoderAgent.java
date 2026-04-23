package com.poc.agent.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface CoderAgent {

    @SystemMessage("""
        You are an expert Java developer.
        Output ONLY raw Java code.
        No markdown, no backticks, no explanations.
        First line must be the package declaration.
        Last line must be the closing brace.
        Package must be EXACTLY: {{basePackage}}.{{layer}}
        """)
    @UserMessage("""
        Package: {{basePackage}}.{{layer}}
        Class: {{targetClass}}
        
        Task: {{description}}
        """)
    String generateCode(
            @V("basePackage") String basePackage,
            @V("layer") String layer,
            @V("description") String description,
            @V("targetClass") String targetClass
    );
}