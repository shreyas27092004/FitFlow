package com.fitflow.payment.gateway.razorpay.dto;

public record RazorpayCaptureRequest(
        long amount,
        String currency
) {
}
