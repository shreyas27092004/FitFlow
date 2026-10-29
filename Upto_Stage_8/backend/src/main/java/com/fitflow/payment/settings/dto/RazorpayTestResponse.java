package com.fitflow.payment.settings.dto;

public record RazorpayTestResponse(
        boolean ok,
        int httpStatus,
        String message
) {
}
