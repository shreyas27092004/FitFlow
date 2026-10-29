package com.fitflow.agent.agents;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class WellnessCoachAgent implements SpecialistAgent {

    private final String systemPrompt;

    public WellnessCoachAgent(AgentPromptLoader promptLoader) {
        this.systemPrompt = promptLoader.loadAgentPrompt("wellness_coach");
    }

    @Override
    public String id() {
        return "wellness_coach";
    }

    @Override
    public String name() {
        return "WellnessCoachAgent";
    }

    @Override
    public String systemPrompt() {
        return systemPrompt;
    }

    @Override
    public Set<String> allowedTools() {
        return Set.of(
                "get_weekly_progress",
                "get_missed_sessions_catchup"
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
