package com.fitflow.agent.dto;

public record AgentChatResponseDto(
        Long conversationId,
        String reply,
        String agentName,
        boolean circuitBreakerTripped
) {}
