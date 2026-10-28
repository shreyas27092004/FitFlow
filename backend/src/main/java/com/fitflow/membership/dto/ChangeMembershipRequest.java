package com.fitflow.membership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChangeMembershipRequest(
        @NotBlank String targetPlanCode,
        @NotNull Long expectedAmountDuePaise
) {
}
