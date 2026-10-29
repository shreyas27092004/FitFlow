package com.fitflow.agent.dto;

import java.time.Instant;

public record AgentMessageDto(
        Long id,
        Long conversationId,
        String sender,
        String content,
        Instant createdAt
) {}
