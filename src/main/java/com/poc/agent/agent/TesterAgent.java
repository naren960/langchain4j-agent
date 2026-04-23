package com.poc.agent.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface TesterAgent {

    @SystemMessage("""
        You are an expert Java test engineer.
        Output ONLY raw Java code.
        No markdown, no backticks, no explanations.
        First line must be the package declaration.
        Last line must be the closing brace.
        
        IMPORT RULES — USE EXACTLY THESE, NO OTHERS:
        - package {{basePackage}};
        - import {{basePackage}}.service.{{serviceClass}};
        - import {{basePackage}}.model.{{modelClass}};
        
        CRITICAL RULES:
        - Do NOT import {{basePackage}}.{{serviceClass}} — this is WRONG
        - Do NOT import {{basePackage}}.{{modelClass}} — this is WRONG
        - Do NOT import anything from {{basePackage}}.repository
        - Do NOT mock any repository class
        - The service under test has NO external dependencies
        - Instantiate the service directly with new {{serviceClass}}()
        - Do NOT use @InjectMocks or @Mock for any repository
                
        MODEL OBJECT CREATION — CRITICAL:
        The {{modelClass}} class is defined below in the MODEL CLASS CONTENT.
        Read it carefully before creating any {{modelClass}} instances.
        ALWAYS use the builder pattern to create {{modelClass}} instances.
        NEVER call new {{modelClass}}() with any arguments.
        NEVER guess the constructor — use builder only.
        """)
    @UserMessage("""
        Package for test class (USE EXACTLY THIS): {{basePackage}}
        Service class full path: {{basePackage}}.service.{{serviceClass}}
        Model class full path: {{basePackage}}.model.{{modelClass}}
        
        The {{serviceClass}} class:
        - Has NO constructor arguments
        - Has NO repository or database dependency
        - Uses only a private in-memory List internally
        - Must be instantiated as: new {{serviceClass}}()
        
        SERVICE CLASS CONTENT — ONLY TEST METHODS THAT EXIST HERE:
        {{serviceClassContent}}
        
        MODEL CLASS CONTENT — READ THIS BEFORE WRITING ANY TEST:
        {{modelClassContent}}

        Based on the model class above:
        - Use ONLY the builder pattern to create {{modelClass}} instances
        - Example: {{modelClass}}.builder().id(1L).name("John").email("john@test.com").build()
        - DO NOT use any constructor — builder only
        
        Based on the service class above:
        - ONLY write tests for methods that are explicitly defined in SERVICE CLASS CONTENT
        - Do NOT test any method not present in SERVICE CLASS CONTENT

        Exact file header to use — DO NOT DEVIATE:
        package {{basePackage}};
        
        import {{basePackage}}.service.{{serviceClass}};
        import {{basePackage}}.model.{{modelClass}};
        import org.junit.jupiter.api.BeforeEach;
        import org.junit.jupiter.api.Test;
        import static org.junit.jupiter.api.Assertions.*;
        
        Task: {{description}}
        
        Generate the COMPLETE test class: {{targetClass}}
        """)
    String generateTests(
            @V("basePackage") String basePackage,
            @V("targetClass") String targetClass,
            @V("description") String description,
            @V("serviceClass") String serviceClass,
            @V("modelClass") String modelClass
    );
}