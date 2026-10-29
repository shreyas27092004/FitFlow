package com.fitflow.payment.gateway.razorpay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RazorpayOrderResponse(
        String id,
        String entity,
        long amount,
        @JsonProperty("amount_paid") long amountPaid,
        @JsonProperty("amount_due") long amountDue,
        String currency,
        String receipt,
        String status,
        int attempts,
        Map<String, String> notes,
        @JsonProperty("created_at") Long createdAt
) {
}
