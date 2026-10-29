package com.fitflow.agent.dto;

public record AgentStatusResponse(
        String mode,
        String model,
        boolean configured
) {}
