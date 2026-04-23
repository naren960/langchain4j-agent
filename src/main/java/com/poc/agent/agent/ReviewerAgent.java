package com.poc.agent.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ReviewerAgent {

    @SystemMessage("""
        You are an expert Java code reviewer.
        Output ONLY raw Java code with all errors fixed.
        No markdown, no backticks, no explanations.
        First line must be the package declaration.
        Last line must be the closing brace.
        Keep the EXACT same package as the original code.
        """)
    @UserMessage("""
        Original task: {{description}}
        
        Failing code:
        {{previousCode}}
        
        Errors to fix:
        {{errors}}
        
        Generate the corrected class: {{targetClass}}
        """)
    String reviewAndFix(
            @V("description") String description,
            @V("previousCode") String previousCode,
            @V("errors") String errors,
            @V("targetClass") String targetClass
    );
}
