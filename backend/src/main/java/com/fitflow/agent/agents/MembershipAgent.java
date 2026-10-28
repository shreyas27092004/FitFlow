package com.fitflow.agent.agents;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class MembershipAgent implements SpecialistAgent {

    private final String systemPrompt;

    public MembershipAgent(AgentPromptLoader promptLoader) {
        this.systemPrompt = promptLoader.loadAgentPrompt("membership");
    }

    @Override
    public String id() {
        return "membership";
    }

    @Override
    public String name() {
        return "MembershipAgent";
    }

    @Override
    public String systemPrompt() {
        return systemPrompt;
    }

    @Override
    public Set<String> allowedTools() {
        return Set.of(
                "get_membership_status",
                "list_plans",
                "preview_membership_change",
                "propose_membership_change"
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
