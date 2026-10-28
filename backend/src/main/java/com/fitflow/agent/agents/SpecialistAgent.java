package com.fitflow.agent.agents;

import java.util.Set;

public interface SpecialistAgent {
    String id();
    String name();
    String systemPrompt();
    Set<String> allowedTools();
    int maxSteps();
    int maxOutputTokens();
    double temperature();
}
