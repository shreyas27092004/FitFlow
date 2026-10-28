package com.fitflow.membership.proration;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record ProrationInput(
        Instant now,
        Instant endsAt,
        int totalDays,
        Integer sessionsTotal,
        int sessionsUsed,
        long amountPaidPaise,
        long newPlanPricePaise,
        long totalCashRemainingInChain,
        BigDecimal feePercent,
        boolean isCancel
) {
}
