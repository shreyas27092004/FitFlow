package com.fitflow.agent.agents;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class PlanBuilderAgent implements SpecialistAgent {

    private final String systemPrompt;

    public PlanBuilderAgent(AgentPromptLoader promptLoader) {
        this.systemPrompt = promptLoader.loadAgentPrompt("plan_builder");
    }

    @Override
    public String id() {
        return "plan_builder";
    }

    @Override
    public String name() {
        return "PlanBuilderAgent";
    }

    @Override
    public String systemPrompt() {
        return systemPrompt;
    }

    @Override
    public Set<String> allowedTools() {
        return Set.of(
                "build_weekly_plan_candidates",
                "propose_weekly_plan"
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
