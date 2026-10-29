package com.fitflow.payment.dto;

import java.time.Instant;

public record InvoiceDetailResponse(
        Long invoiceId,
        Long paymentId,
        String invoiceNumber,
        Long totalPaise,
        Instant issuedAt
) {
}
