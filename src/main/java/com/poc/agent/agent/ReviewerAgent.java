package com.poc.agent.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;


public interface ReviewerAgent {

    @SystemMessage("""
        You are an expert Java code reviewer and debugger.
        You will be given:
        1. Original task description
        2. Previously generated code that failed to compile or pass tests
        3. The exact error messages
        
        Your job is to produce a FIXED version of the code.
        
        STRICT OUTPUT RULES:
        - Output RAW Java code only
        - No markdown backticks
        - No explanations after the class
        - First line must be package declaration
        - Last line must be closing brace
        """)
    @UserMessage("""
        Original task: {{description}}
        
        Previously generated code:
        {{previousCode}}
        
        Errors encountered:
        {{errors}}
        
        Generate the corrected Java class: {{targetClass}}
        Fix ALL errors listed above.
        """)
    String reviewAndFix(
            @V("description") String description,
            @V("previousCode") String previousCode,
            @V("errors") String errors,
            @V("targetClass") String targetClass
    );
}
