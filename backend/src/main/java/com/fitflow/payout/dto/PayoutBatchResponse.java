package com.fitflow.payout.dto;

import com.fitflow.payout.model.PayoutStatus;

import java.time.Instant;
import java.time.LocalDate;

public record PayoutBatchResponse(
        Long id,
        PayoutStatus status,
        LocalDate periodStart,
        LocalDate periodEnd,
        Long totalPaise,
        Long createdBy,
        Long approvedBy,
        Integer version,
        Instant createdAt,
        Instant updatedAt,
        int sessionCount
) {
}
