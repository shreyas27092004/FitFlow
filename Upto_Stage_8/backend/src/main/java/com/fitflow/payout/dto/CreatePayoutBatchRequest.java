package com.fitflow.payout.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreatePayoutBatchRequest(
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd
) {
}
