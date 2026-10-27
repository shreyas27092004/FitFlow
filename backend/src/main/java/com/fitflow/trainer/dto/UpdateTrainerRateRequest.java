package com.fitflow.trainer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateTrainerRateRequest(
        @NotNull(message = "Rate in paise is required")
        @Min(value = 0, message = "Rate must be non-negative")
        Long ratePerSessionPaise,

        @NotNull(message = "Version is required for optimistic locking")
        Integer version
) {}
