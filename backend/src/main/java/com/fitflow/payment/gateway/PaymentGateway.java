package com.fitflow.payment.gateway;

import com.fitflow.payment.model.PaymentStatus;
import com.fitflow.payment.model.RefundStatus;

import java.util.Map;

public interface PaymentGateway {

    String name();

    PaymentIntent createIntent(CreateIntentRequest request);

    ConfirmResult confirm(ConfirmRequest request);

    RefundResult refund(RefundRequest request);

    GatewayPaymentStatus fetchStatus(String gatewayOrderId);

    record CreateIntentRequest(
            long amountPaise,
            String currency,
            String receipt,
            Map<String, Object> notes
    ) {}

    record PaymentIntent(
            String gateway,
            String gatewayOrderId,
            long amountPaise,
            String currency,
            Map<String, Object> clientPayload
    ) {}

    record ConfirmRequest(
            String gatewayOrderId,
            Map<String, Object> clientPayload
    ) {}

    record ConfirmResult(
            PaymentStatus status,
            String gatewayPaymentId,
            String failureReason,
            long amountPaise
    ) {}

    record RefundRequest(
            String gatewayPaymentId,
            long amountPaise,
            String receipt,
            Map<String, Object> notes
    ) {}

    record RefundResult(
            RefundStatus status,
            String gatewayRefundId,
            String failureReason
    ) {}

    record GatewayPaymentStatus(
            PaymentStatus status,
            String gatewayPaymentId,
            String failureReason,
            long amountPaise
    ) {}
}
