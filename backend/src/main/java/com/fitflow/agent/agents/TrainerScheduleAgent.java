package com.fitflow.agent.agents;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class TrainerScheduleAgent implements SpecialistAgent {

    private final String systemPrompt;

    public TrainerScheduleAgent(AgentPromptLoader promptLoader) {
        this.systemPrompt = promptLoader.loadAgentPrompt("trainer_schedule");
    }

    @Override
    public String id() {
        return "trainer_schedule";
    }

    @Override
    public String name() {
        return "TrainerScheduleAgent";
    }

    @Override
    public String systemPrompt() {
        return systemPrompt;
    }

    @Override
    public Set<String> allowedTools() {
        return Set.of(
                "get_my_sessions",
                "get_my_earnings"
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
