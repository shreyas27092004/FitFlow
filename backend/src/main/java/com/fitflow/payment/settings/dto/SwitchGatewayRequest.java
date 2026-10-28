package com.fitflow.payment.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SwitchGatewayRequest(
        @NotBlank(message = "gateway is required")
        @Pattern(regexp = "^(?i)(MOCK|RAZORPAY)$", message = "gateway must be MOCK or RAZORPAY")
        String gateway
) {
}
