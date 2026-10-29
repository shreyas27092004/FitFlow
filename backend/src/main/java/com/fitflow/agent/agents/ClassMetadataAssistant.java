package com.fitflow.agent.agents;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ClassMetadataAssistant implements SpecialistAgent {

    private final String systemPrompt;

    public ClassMetadataAssistant(AgentPromptLoader promptLoader) {
        this.systemPrompt = promptLoader.loadAgentPrompt("class_metadata");
    }

    @Override
    public String id() {
        return "class_metadata";
    }

    @Override
    public String name() {
        return "ClassMetadataAssistant";
    }

    @Override
    public String systemPrompt() {
        return systemPrompt;
    }

    @Override
    public Set<String> allowedTools() {
        return Set.of(
                "draft_class_metadata",
                "propose_create_class"
        );
    }

    @Override
    public int maxSteps() {
        return 6;
    }

    @Override
    public int maxOutputTokens() {
        return 800;
    }

    @Override
    public double temperature() {
        return 0.2;
    }
}
