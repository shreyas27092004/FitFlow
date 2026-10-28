package com.fitflow.payment.gateway.razorpay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.notification.OutboxService;
import com.fitflow.payment.PaymentFinalizer;
import com.fitflow.payment.entity.Payment;
import com.fitflow.payment.entity.PaymentWebhookEvent;
import com.fitflow.payment.entity.Refund;
import com.fitflow.payment.gateway.PaymentGateway.ConfirmResult;
import com.fitflow.payment.model.PaymentStatus;
import com.fitflow.payment.model.PaymentWebhookStatus;
import com.fitflow.payment.model.RefundStatus;
import com.fitflow.payment.repository.PaymentRepository;
import com.fitflow.payment.repository.PaymentWebhookEventRepository;
import com.fitflow.payment.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RazorpayWebhookService {

    public static volatile boolean sanityBreakDisableDuplicateCheck = false;

    private final RazorpayClient razorpayClient;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentWebhookEventRepository paymentWebhookEventRepository;
    private final PaymentFinalizer paymentFinalizer;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public boolean isSignatureValid(byte[] rawBody, String signature) {
        Optional<String> webhookSecret = razorpayClient.getWebhookSecret();
        if (webhookSecret.isEmpty() || signature == null || signature.isBlank()) {
            return false;
        }
        return RazorpaySignatureValidator.verifyWebhookSignature(rawBody, signature, webhookSecret.get());
    }

    public boolean isDuplicateEvent(String eventId) {
        if (sanityBreakDisableDuplicateCheck) {
            return false;
        }
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        return paymentWebhookEventRepository.existsById(eventId);
    }

    @Transactional
    public void processWebhook(byte[] rawBody, String eventId) {
        String effectiveEventId = (eventId != null && !eventId.isBlank()) ? eventId : UUID.randomUUID().toString();

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.error("Failed to parse Razorpay webhook payload: {}", e.getMessage());
            return;
        }

        String eventType = root.path("event").asText("");

        PaymentWebhookEvent webhookEvent = PaymentWebhookEvent.builder()
                .eventId(effectiveEventId)
                .gateway("RAZORPAY")
                .eventType(eventType)
                .receivedAt(clock.instant())
                .status(PaymentWebhookStatus.RECEIVED)
                .build();

        try {
            switch (eventType) {
                case "payment.captured" -> handlePaymentCaptured(root);
                case "order.paid" -> handleOrderPaid(root);
                case "payment.failed" -> handlePaymentFailed(root);
                case "refund.processed" -> handleRefundProcessed(root);
                case "refund.failed" -> handleRefundFailed(root);
                default -> {
                    log.info("Ignored unknown Razorpay webhook event: {}", eventType);
                    webhookEvent.setStatus(PaymentWebhookStatus.IGNORED);
                }
            }

            if (webhookEvent.getStatus() == PaymentWebhookStatus.RECEIVED) {
                webhookEvent.setStatus(PaymentWebhookStatus.PROCESSED);
            }
        } catch (Exception e) {
            log.error("Error processing webhook event {}: {}", eventType, e.getMessage(), e);
            webhookEvent.setStatus(PaymentWebhookStatus.FAILED);
            webhookEvent.setError(e.getMessage() != null && e.getMessage().length() > 300
                    ? e.getMessage().substring(0, 300)
                    : e.getMessage());
        }

        webhookEvent.setProcessedAt(clock.instant());
        paymentWebhookEventRepository.save(webhookEvent);
    }

    private void handlePaymentCaptured(JsonNode root) {
        JsonNode entity = root.path("payload").path("payment").path("entity");
        String orderId = entity.path("order_id").asText(null);
        String paymentId = entity.path("id").asText(null);
        long amount = entity.path("amount").asLong(0L);

        log.info("Processing Razorpay payment.captured for orderId={}, paymentId={}, amount={}", orderId, paymentId, amount);

        Optional<Payment> paymentOpt = Optional.empty();
        if (orderId != null && !orderId.isBlank()) {
            paymentOpt = paymentRepository.findByGatewayOrderId(orderId);
        }

        paymentOpt.ifPresent(p -> {
            if (p.getStatus() == PaymentStatus.PENDING) {
                paymentFinalizer.finalizePayment(
                        p.getId(),
                        new ConfirmResult(PaymentStatus.SUCCEEDED, paymentId, null, amount)
                );
            } else {
                log.info("Payment id={} already in final status={}", p.getId(), p.getStatus());
            }
        });
    }

    private void handleOrderPaid(JsonNode root) {
        JsonNode entity = root.path("payload").path("order").path("entity");
        String orderId = entity.path("id").asText(null);
        log.info("Processing Razorpay order.paid for orderId={}", orderId);
    }

    private void handlePaymentFailed(JsonNode root) {
        JsonNode entity = root.path("payload").path("payment").path("entity");
        String orderId = entity.path("order_id").asText(null);
        String paymentId = entity.path("id").asText(null);
        long amount = entity.path("amount").asLong(0L);
        String errorDesc = entity.path("error_description").asText(
                entity.path("error_code").asText("Payment failed")
        );

        log.info("Processing Razorpay payment.failed for orderId={}, paymentId={}, reason={}", orderId, paymentId, errorDesc);

        Optional<Payment> paymentOpt = Optional.empty();
        if (orderId != null && !orderId.isBlank()) {
            paymentOpt = paymentRepository.findByGatewayOrderId(orderId);
        }

        paymentOpt.ifPresent(p -> {
            if (p.getStatus() == PaymentStatus.PENDING) {
                paymentFinalizer.finalizePayment(
                        p.getId(),
                        new ConfirmResult(PaymentStatus.FAILED, paymentId, errorDesc, amount)
                );
            }
        });
    }

    private void handleRefundProcessed(JsonNode root) {
        JsonNode entity = root.path("payload").path("refund").path("entity");
        String refundId = entity.path("id").asText(null);
        String receipt = entity.path("receipt").asText(null);
        JsonNode notes = entity.path("notes");

        log.info("Processing Razorpay refund.processed for refundId={}, receipt={}", refundId, receipt);

        Optional<Refund> refundOpt = findRefund(receipt, notes, refundId);
        refundOpt.ifPresent(r -> {
            if (r.getStatus() != RefundStatus.PAID) {
                r.setStatus(RefundStatus.PAID);
                if (refundId != null) {
                    r.setGatewayRefundId(refundId);
                }
                Refund saved = refundRepository.save(r);

                paymentRepository.findById(saved.getPaymentId()).ifPresent(payment -> {
                    outboxService.append(
                            "refund",
                            saved.getId(),
                            "refund.issued",
                            Map.of(
                                    "refundId", saved.getId(),
                                    "paymentId", saved.getPaymentId(),
                                    "memberId", payment.getMemberId(),
                                    "amountPaise", saved.getAmountPaise(),
                                    "gatewayRefundId", saved.getGatewayRefundId() != null ? saved.getGatewayRefundId() : ""
                            )
                    );
                });
            }
        });
    }

    private void handleRefundFailed(JsonNode root) {
        JsonNode entity = root.path("payload").path("refund").path("entity");
        String refundId = entity.path("id").asText(null);
        String receipt = entity.path("receipt").asText(null);
        String reason = entity.path("error_description").asText("Refund failed");
        JsonNode notes = entity.path("notes");

        log.info("Processing Razorpay refund.failed for refundId={}, reason={}", refundId, reason);

        Optional<Refund> refundOpt = findRefund(receipt, notes, refundId);
        refundOpt.ifPresent(r -> {
            if (r.getStatus() != RefundStatus.FAILED && r.getStatus() != RefundStatus.PAID) {
                r.setStatus(RefundStatus.FAILED);
                r.setFailureReason(reason);
                refundRepository.save(r);
            }
        });
    }

    private Optional<Refund> findRefund(String receipt, JsonNode notes, String gatewayRefundId) {
        if (receipt != null && !receipt.isBlank()) {
            try {
                Optional<Refund> opt = refundRepository.findById(Long.valueOf(receipt));
                if (opt.isPresent()) return opt;
            } catch (NumberFormatException ignored) {}
        }
        if (notes != null && notes.has("refundId")) {
            try {
                Optional<Refund> opt = refundRepository.findById(notes.path("refundId").asLong());
                if (opt.isPresent()) return opt;
            } catch (Exception ignored) {}
        }
        if (gatewayRefundId != null && !gatewayRefundId.isBlank()) {
            return refundRepository.findByGatewayRefundId(gatewayRefundId);
        }
        return Optional.empty();
    }
}
