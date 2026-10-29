package com.fitflow.payment.settings.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public record PaymentSettingsResponse(
        String gateway,
        RazorpaySettingsDto razorpay,
        String webhookUrlHint
) {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RazorpaySettingsDto(
            boolean configured,
            String keyId,
            String keySecretMaskedLast4,
            boolean webhookSecretConfigured,
            String mode
    ) {}
}
