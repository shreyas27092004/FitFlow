package com.fitflow.agent.agents;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SafeClassMatcherAgent implements SpecialistAgent {

    private final String systemPrompt;

    public SafeClassMatcherAgent(AgentPromptLoader promptLoader) {
        this.systemPrompt = promptLoader.loadAgentPrompt("safe_class_matcher");
    }

    @Override
    public String id() {
        return "safe_class_matcher";
    }

    @Override
    public String name() {
        return "SafeClassMatcherAgent";
    }

    @Override
    public String systemPrompt() {
        return systemPrompt;
    }

    @Override
    public Set<String> allowedTools() {
        return Set.of(
                "search_sessions",
                "get_class_details",
                "get_safe_classes",
                "get_llm_safe_profile"
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
