package com.fitflow.payout.dto;

import com.fitflow.payout.model.EarningsStatus;

import java.time.Instant;

public record TrainerEarningItemDto(
        Long id,
        Long sessionId,
        Long trainerId,
        Long amountPaise,
        EarningsStatus status,
        Long payoutId,
        Instant createdAt
) {
}
