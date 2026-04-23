package com.poc.agent.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class OllamaConfig {

    @Value("${ollama.base-url}")
    private String baseUrl;

    @Value("${ollama.coder-model}")
    private String coderModelName;

    @Value("${ollama.tester-model}")
    private String testerModelName;

    @Value("${ollama.reviewer-model}")
    private String reviewerModelName;

    @Value("${ollama.timeout-minutes}")
    private int timeoutMinutes;

    @Bean(name = "coderChatModel")
    public ChatLanguageModel coderModel() {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(coderModelName)
                .temperature(0.0)
                .timeout(Duration.ofMinutes(timeoutMinutes))
                .build();
    }

    @Bean(name = "testerChatModel")
    public ChatLanguageModel testerModel() {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(testerModelName)
                .temperature(0.0)
                .timeout(Duration.ofMinutes(timeoutMinutes))
                .build();
    }

    @Bean(name = "reviewerChatModel")
    public ChatLanguageModel reviewerModel() {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(reviewerModelName)
                .temperature(0.0)
                .timeout(Duration.ofMinutes(timeoutMinutes))
                .build();
    }
}