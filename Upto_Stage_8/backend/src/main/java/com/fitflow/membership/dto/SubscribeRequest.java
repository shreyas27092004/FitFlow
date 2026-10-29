package com.fitflow.membership.dto;

import jakarta.validation.constraints.NotBlank;

public record SubscribeRequest(
        @NotBlank String planCode
) {
}
