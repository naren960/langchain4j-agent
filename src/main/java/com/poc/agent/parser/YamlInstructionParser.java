package com.poc.agent.parser;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.poc.agent.model.InstructionPlan;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

// YamlInstructionParser.java
@Component
public class YamlInstructionParser {

    private final ObjectMapper yamlMapper;

    public YamlInstructionParser() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public InstructionPlan parse(String yamlFilePath) throws IOException {
        return yamlMapper.readValue(
                new File(yamlFilePath), InstructionPlan.class);
    }
}