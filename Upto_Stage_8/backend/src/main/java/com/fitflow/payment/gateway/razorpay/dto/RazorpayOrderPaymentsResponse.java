package com.fitflow.payment.gateway.razorpay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RazorpayOrderPaymentsResponse(
        String entity,
        int count,
        List<RazorpayPaymentResponse> items
) {
}
