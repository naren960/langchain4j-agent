package com.poc.agent.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

// CoderAgent.java
public interface CoderAgent {

    @SystemMessage("""
            You are an expert Java developer.
                    STRICT OUTPUT RULES — FOLLOW EXACTLY:
                    - Output RAW Java code only
                    - Do NOT wrap code in markdown backticks
                    - Do NOT include ```java or ``` anywhere
                    - Do NOT add any explanation, comments, or notes after the class
                    - Do NOT add placeholder comments like "Code goes here"
                    - The FIRST line of your response must be the package declaration
                    - The LAST line of your response must be the closing brace of the class
                   
                    CODE RULES:
                    - Always use the EXACT package: {{basePackage}}.{{layer}}
                    - Include all necessary imports
                    - Follow Spring Boot best practices
                    - Include full method implementations, not placeholders
        """)
    @UserMessage("""
        Project base package: {{basePackage}}
        Layer: {{layer}}
        
        Task: {{description}}
        
        Generate the complete Java class for: {{targetClass}}
        """)
    String generateCode(
            @V("basePackage") String basePackage,
            @V("layer") String layer,
            @V("description") String description,
            @V("targetClass") String targetClass
    );
}