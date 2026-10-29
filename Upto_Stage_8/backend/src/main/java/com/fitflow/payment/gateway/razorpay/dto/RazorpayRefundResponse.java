package com.fitflow.payment.gateway.razorpay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RazorpayRefundResponse(
        String id,
        String entity,
        long amount,
        String currency,
        @JsonProperty("payment_id") String paymentId,
        String status,
        String receipt,
        @JsonProperty("created_at") Long createdAt
) {
}
