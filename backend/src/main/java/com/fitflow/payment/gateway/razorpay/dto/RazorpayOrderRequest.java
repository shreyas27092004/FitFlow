package com.fitflow.payment.gateway.razorpay.dto;

import java.util.Map;

public record RazorpayOrderRequest(
        long amount,
        String currency,
        String receipt,
        Map<String, ?> notes
) {
}
