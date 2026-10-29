package com.fitflow.membership.proration;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record ProrationResult(
        int daysRemaining,
        int totalDays,
        Integer sessionsRemaining,
        Integer sessionsTotal,
        BigDecimal daysRatio,
        BigDecimal sessionsRatio,
        BigDecimal refundableFraction,
        long creditPaise,
        long amountDuePaise,
        long refundPaise,
        Long cappedByCashPaise,
        String policy,
        List<String> explanation
) {
}
