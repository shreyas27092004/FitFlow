package com.fitflow.payment.gateway.razorpay.dto;

import java.util.Map;

public record RazorpayRefundRequest(
        long amount,
        String speed,
        String receipt,
        Map<String, ?> notes
) {
    public RazorpayRefundRequest(long amount, String receipt, Map<String, ?> notes) {
        this(amount, "normal", receipt, notes);
    }
}
