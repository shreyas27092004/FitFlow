package com.fitflow.agent.dto;

import java.time.Instant;

public record AgentConversationDto(
        Long id,
        Long userId,
        String role,
        Instant createdAt,
        Instant lastActiveAt
) {}
