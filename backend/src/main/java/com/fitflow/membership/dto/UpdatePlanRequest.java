package com.fitflow.membership.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdatePlanRequest(
        String name,
        @PositiveOrZero Long pricePaise,
        Integer durationDays,
        Integer sessionQuota,
        Boolean active,
        Integer sortOrder,
        @NotNull Integer version
) {
}
