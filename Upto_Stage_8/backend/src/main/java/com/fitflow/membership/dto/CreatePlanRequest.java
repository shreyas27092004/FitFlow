package com.fitflow.membership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CreatePlanRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull @PositiveOrZero Long pricePaise,
        Integer durationDays,
        Integer sessionQuota,
        Boolean isFree,
        Integer freeSessions,
        Integer sortOrder
) {
}
