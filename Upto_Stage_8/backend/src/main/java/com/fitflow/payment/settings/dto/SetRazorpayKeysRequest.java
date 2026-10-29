package com.fitflow.payment.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SetRazorpayKeysRequest(
        @NotBlank(message = "keyId is required")
        @Pattern(regexp = "^rzp_(test|live)_[A-Za-z0-9]{8,40}$", message = "keyId must match ^rzp_(test|live)_[A-Za-z0-9]{8,40}$")
        String keyId,

        @NotBlank(message = "keySecret is required")
        @Pattern(regexp = "^[^\\s]{16,80}$", message = "keySecret must be 16-80 non-whitespace characters")
        String keySecret,

        @Pattern(regexp = "^(|[^\\s]{8,100})$", message = "webhookSecret must be 8-100 non-whitespace characters if provided")
        String webhookSecret
) {
}
