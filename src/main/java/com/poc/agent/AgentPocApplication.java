package com.poc.agent;


import com.poc.agent.model.InstructionPlan;
import com.poc.agent.orchestrator.AgentOrchestrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.poc.agent.parser.YamlInstructionParser;

@SpringBootApplication
public class AgentPocApplication implements CommandLineRunner {

    @Autowired private YamlInstructionParser parser;
    @Autowired
    private AgentOrchestrator orchestrator;

    public static void main(String[] args) {
        SpringApplication.run(AgentPocApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        String yamlPath = args.length > 0 ? args[0] : "instructions.yaml";
        InstructionPlan plan = parser.parse(yamlPath);
        orchestrator.execute(plan);
    }
}