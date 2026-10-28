package com.fitflow.payout.dto;

import java.util.List;

public record TrainerEarningsResponse(
        Long totalPaise,
        Long pendingPaise,
        Long paidPaise,
        List<TrainerEarningItemDto> items
) {
}
