package com.fitflow.payment.gateway;

import com.fitflow.payment.model.PaymentStatus;
import com.fitflow.payment.model.RefundStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component("mockPaymentGateway")
@Slf4j
@RequiredArgsConstructor
public class MockPaymentGateway implements PaymentGateway {

    public static final String GATEWAY_NAME = "MOCK";

    private final Clock clock;
    private final Map<String, MockOrder> orders = new ConcurrentHashMap<>();

    private record MockOrder(
            String gatewayOrderId,
            long amountPaise,
            String currency,
            String receipt,
            Instant createdAt,
            PaymentStatus status,
            String gatewayPaymentId
    ) {}

    @Override
    public String name() {
        return GATEWAY_NAME;
    }

    @Override
    public PaymentIntent createIntent(CreateIntentRequest request) {
        String gatewayOrderId = "mock_order_" + UUID.randomUUID();
        MockOrder order = new MockOrder(
                gatewayOrderId,
                request.amountPaise(),
                request.currency() != null ? request.currency() : "INR",
                request.receipt(),
                clock.instant(),
                PaymentStatus.PENDING,
                null
        );
        orders.put(gatewayOrderId, order);
        log.info("MockPaymentGateway: Created intent orderId={} for amount={} paise", gatewayOrderId, request.amountPaise());

        return new PaymentIntent(
                GATEWAY_NAME,
                gatewayOrderId,
                request.amountPaise(),
                order.currency(),
                Map.of("gatewayOrderId", gatewayOrderId, "mockEnv", true)
        );
    }

    @Override
    public ConfirmResult confirm(ConfirmRequest request) {
        String gatewayOrderId = request.gatewayOrderId();
        MockOrder order = orders.get(gatewayOrderId);
        long amount = order != null ? order.amountPaise() : 0L;

        Object tokenObj = request.clientPayload() != null ? request.clientPayload().get("paymentMethodToken") : null;
        String token = tokenObj != null ? tokenObj.toString() : "";

        log.info("MockPaymentGateway: Confirming orderId={} with token={}", gatewayOrderId, token);

        if ("tok_ok".equals(token)) {
            String payId = "mock_pay_" + UUID.randomUUID();
            orders.put(gatewayOrderId, new MockOrder(
                    gatewayOrderId, amount, "INR", order != null ? order.receipt() : null,
                    order != null ? order.createdAt() : clock.instant(), PaymentStatus.SUCCEEDED, payId
            ));
            return new ConfirmResult(PaymentStatus.SUCCEEDED, payId, null, amount);
        } else if ("tok_fail".equals(token)) {
            orders.put(gatewayOrderId, new MockOrder(
                    gatewayOrderId, amount, "INR", order != null ? order.receipt() : null,
                    order != null ? order.createdAt() : clock.instant(), PaymentStatus.FAILED, null
            ));
            return new ConfirmResult(PaymentStatus.FAILED, null, "card_declined", amount);
        } else if ("tok_timeout".equals(token)) {
            orders.put(gatewayOrderId, new MockOrder(
                    gatewayOrderId, amount, "INR", order != null ? order.receipt() : null,
                    order != null ? order.createdAt() : clock.instant(), PaymentStatus.PENDING, null
            ));
            return new ConfirmResult(PaymentStatus.PENDING, null, "gateway_timeout", amount);
        } else {
            return new ConfirmResult(PaymentStatus.FAILED, null, "invalid_payment_method", amount);
        }
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        String refundId = "mock_ref_" + UUID.randomUUID();
        log.info("MockPaymentGateway: Processed refund for paymentId={}, amount={} paise -> refundId={}",
                request.gatewayPaymentId(), request.amountPaise(), refundId);
        return new RefundResult(RefundStatus.PAID, refundId, null);
    }

    @Override
    public GatewayPaymentStatus fetchStatus(String gatewayOrderId) {
        MockOrder order = orders.get(gatewayOrderId);
        if (order == null) {
            return new GatewayPaymentStatus(PaymentStatus.PENDING, null, null, 0L);
        }

        if (order.status() == PaymentStatus.PENDING) {
            // Resolves PENDING mock payments to SUCCEEDED on reconcile fetch
            String payId = "mock_pay_" + UUID.randomUUID();
            MockOrder resolved = new MockOrder(
                    gatewayOrderId, order.amountPaise(), order.currency(), order.receipt(),
                    order.createdAt(), PaymentStatus.SUCCEEDED, payId
            );
            orders.put(gatewayOrderId, resolved);
            log.info("MockPaymentGateway: Reconciled pending orderId={} to SUCCEEDED", gatewayOrderId);
            return new GatewayPaymentStatus(PaymentStatus.SUCCEEDED, payId, null, order.amountPaise());
        }

        return new GatewayPaymentStatus(order.status(), order.gatewayPaymentId(), null, order.amountPaise());
    }
}
