package com.fitflow.payment.dto;

import com.fitflow.payment.model.PaymentStatus;

public record PaymentDetailResponse(
        Long paymentId,
        PaymentStatus status,
        String gateway,
        Long amountPaise,
        boolean membershipChangeApplied
) {
}
