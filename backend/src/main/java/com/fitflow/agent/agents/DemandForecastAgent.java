package com.fitflow.agent.agents;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DemandForecastAgent implements SpecialistAgent {

    private final String systemPrompt;

    public DemandForecastAgent(AgentPromptLoader promptLoader) {
        this.systemPrompt = promptLoader.loadAgentPrompt("demand_forecast");
    }

    @Override
    public String id() {
        return "demand_forecast";
    }

    @Override
    public String name() {
        return "DemandForecastAgent";
    }

    @Override
    public String systemPrompt() {
        return systemPrompt;
    }

    @Override
    public Set<String> allowedTools() {
        return Set.of(
                "get_demand_insights",
                "propose_add_session"
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
