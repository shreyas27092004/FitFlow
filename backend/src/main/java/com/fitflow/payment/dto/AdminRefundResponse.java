package com.fitflow.payment.dto;

import com.fitflow.payment.model.RefundStatus;

import java.time.Instant;

public record AdminRefundResponse(
        Long refundId,
        Long paymentId,
        Long amountPaise,
        RefundStatus status,
        Long approvedBy,
        String gatewayRefundId,
        Instant createdAt
) {
}
