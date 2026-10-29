package com.fitflow.agent.dto;

import java.time.Instant;

public record PendingActionProposal(
        Long id,
        String toolName,
        String summary,
        Instant expiresAt
) {}
