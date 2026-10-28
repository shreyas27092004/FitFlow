package com.fitflow.agent.agents;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class BookingAgent implements SpecialistAgent {

    private final String systemPrompt;

    public BookingAgent(AgentPromptLoader promptLoader) {
        this.systemPrompt = promptLoader.loadAgentPrompt("booking");
    }

    @Override
    public String id() {
        return "booking";
    }

    @Override
    public String name() {
        return "BookingAgent";
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
                "get_my_bookings",
                "get_membership_status",
                "propose_booking",
                "propose_cancel_booking"
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
