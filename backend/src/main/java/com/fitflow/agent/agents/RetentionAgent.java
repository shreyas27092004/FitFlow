package com.fitflow.agent.agents;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class RetentionAgent implements SpecialistAgent {

    private final String systemPrompt;

    public RetentionAgent(AgentPromptLoader promptLoader) {
        this.systemPrompt = promptLoader.loadAgentPrompt("retention");
    }

    @Override
    public String id() {
        return "retention";
    }

    @Override
    public String name() {
        return "RetentionAgent";
    }

    @Override
    public String systemPrompt() {
        return systemPrompt;
    }

    @Override
    public Set<String> allowedTools() {
        return Set.of(
                "list_at_risk_members",
                "propose_retention_offer"
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
