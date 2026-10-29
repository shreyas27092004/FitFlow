package com.fitflow.agent.agents;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class NutritionNumbersAgent implements SpecialistAgent {

    private final String systemPrompt;

    public NutritionNumbersAgent(AgentPromptLoader promptLoader) {
        this.systemPrompt = promptLoader.loadAgentPrompt("nutrition_numbers");
    }

    @Override
    public String id() {
        return "nutrition_numbers";
    }

    @Override
    public String name() {
        return "NutritionNumbersAgent";
    }

    @Override
    public String systemPrompt() {
        return systemPrompt;
    }

    @Override
    public Set<String> allowedTools() {
        return Set.of(
                "get_calorie_numbers",
                "get_class_burn"
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
