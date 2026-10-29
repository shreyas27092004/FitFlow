package com.fitflow.payment.dto;

import com.fitflow.payment.model.PaymentStatus;
import com.fitflow.payment.model.RefundStatus;

import java.time.Instant;
import java.util.List;

public record MemberPaymentSummaryDto(
        Long paymentId,
        Long amountPaise,
        String currency,
        PaymentStatus status,
        String gateway,
        Instant createdAt,
        List<RefundSummaryDto> refunds
) {
    public record RefundSummaryDto(
            Long refundId,
            Long amountPaise,
            RefundStatus status,
            Instant createdAt
    ) {}
}
