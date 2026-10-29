package com.fitflow.payment.gateway.razorpay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RazorpayPaymentResponse(
        String id,
        String entity,
        long amount,
        String currency,
        String status,
        @JsonProperty("order_id") String orderId,
        boolean captured,
        String method,
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("error_description") String errorDescription,
        Map<String, String> notes,
        @JsonProperty("created_at") Long createdAt
) {
}
