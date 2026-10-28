package com.fitflow.payment.dto;

import com.fitflow.payment.model.PaymentStatus;

import java.time.Instant;
import java.util.Map;

public record PaymentIntentResponse(
        Long paymentId,
        String status,
        String gateway,
        String gatewayOrderId,
        Long amountPaise,
        String currency,
        Map<String, Object> clientPayload,
        Instant expiresAt
) {
}
