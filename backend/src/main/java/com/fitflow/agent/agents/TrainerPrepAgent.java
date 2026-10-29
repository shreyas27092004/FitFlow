package com.fitflow.agent.agents;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class TrainerPrepAgent implements SpecialistAgent {

    private final String systemPrompt;

    public TrainerPrepAgent(AgentPromptLoader promptLoader) {
        this.systemPrompt = promptLoader.loadAgentPrompt("trainer_prep");
    }

    @Override
    public String id() {
        return "trainer_prep";
    }

    @Override
    public String name() {
        return "TrainerPrepAgent";
    }

    @Override
    public String systemPrompt() {
        return systemPrompt;
    }

    @Override
    public Set<String> allowedTools() {
        return Set.of(
                "get_session_prep_summary"
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
