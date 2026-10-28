package com.fitflow.membership.dto;

import java.math.BigDecimal;
import java.util.List;

public record ChangePreviewResponse(
        String currentPlan,
        String targetPlan,
        Long amountPaidPaise,
        Integer daysRemaining,
        Integer totalDays,
        Integer sessionsRemaining,
        Integer sessionsTotal,
        BigDecimal daysRatio,
        BigDecimal sessionsRatio,
        BigDecimal refundableFraction,
        Long creditPaise,
        Long amountDuePaise,
        Long refundPaise,
        String policy,
        List<String> explanation,
        Long cappedByCashPaise
) {
}
