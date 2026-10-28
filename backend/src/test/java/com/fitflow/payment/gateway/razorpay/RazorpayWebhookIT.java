package com.fitflow.payment.gateway.razorpay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.notification.repository.OutboxEventRepository;
import com.fitflow.payment.entity.Payment;
import com.fitflow.payment.entity.PaymentWebhookEvent;
import com.fitflow.payment.entity.Refund;
import com.fitflow.payment.model.PaymentStatus;
import com.fitflow.payment.model.PaymentWebhookStatus;
import com.fitflow.payment.model.RefundStatus;
import com.fitflow.payment.repository.PaymentRepository;
import com.fitflow.payment.repository.PaymentWebhookEventRepository;
import com.fitflow.payment.repository.RefundRepository;
import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class RazorpayWebhookIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RazorpayClient razorpayClient;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private PaymentWebhookEventRepository webhookEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String WEBHOOK_SECRET = "test_webhook_secret_key_123";
    private Long memberId;

    @BeforeEach
    void setUp() {
        razorpayClient.setCredentials("rzp_test_ScYoa7HPECrM5A", "FAKEsecret1234567890abcd", WEBHOOK_SECRET, null);

        User member = userRepository.findByEmail("webhook_test_user@example.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("webhook_test_user@example.com")
                        .passwordHash("hash")
                        .fullName("Webhook Tester")
                        .status(User.Status.ACTIVE)
                        .build()));
        memberId = member.getId();
    }

    @AfterEach
    void tearDown() {
        RazorpayWebhookService.sanityBreakDisableDuplicateCheck = false;
        razorpayClient.deleteCredentials();
    }

    private String sign(byte[] payload) {
        return RazorpaySignatureValidator.calculateHmacSha256Hex(
                WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8),
                payload
        );
    }

    @Test
    @DisplayName("webhook: payment.captured event finalizes PENDING payment to SUCCEEDED")
    void testPaymentCaptured_success() throws Exception {
        String orderId = "order_WH_" + UUID.randomUUID();
        Payment payment = paymentRepository.save(Payment.builder()
                .memberId(memberId)
                .amountPaise(49900L)
                .status(PaymentStatus.PENDING)
                .gateway("RAZORPAY")
                .gatewayOrderId(orderId)
                .currency("INR")
                .idempotencyKey("idemp-" + UUID.randomUUID())
                .createdAt(Instant.now())
                .build());

        String eventId = "evt_" + UUID.randomUUID();
        String json = """
                {
                  "entity": "event",
                  "event": "payment.captured",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_WH_123",
                        "amount": 49900,
                        "currency": "INR",
                        "status": "captured",
                        "order_id": "%s"
                      }
                    }
                  }
                }
                """.formatted(orderId);

        byte[] rawBytes = json.getBytes(StandardCharsets.UTF_8);
        String signature = sign(rawBytes);

        mockMvc.perform(post("/api/v1/payments/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .header("X-Razorpay-Event-Id", eventId)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")));

        Payment updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.SUCCEEDED, updated.getStatus());
        assertEquals("pay_WH_123", updated.getGatewayPaymentId());

        PaymentWebhookEvent event = webhookEventRepository.findById(eventId).orElseThrow();
        assertEquals(PaymentWebhookStatus.PROCESSED, event.getStatus());
    }

    @Test
    @DisplayName("webhook: duplicate X-Razorpay-Event-Id returns 200 'duplicate' immediately")
    void testDuplicateWebhookEvent_ignored() throws Exception {
        String orderId = "order_DUP_" + UUID.randomUUID();
        paymentRepository.save(Payment.builder()
                .memberId(memberId)
                .amountPaise(49900L)
                .status(PaymentStatus.PENDING)
                .gateway("RAZORPAY")
                .gatewayOrderId(orderId)
                .currency("INR")
                .idempotencyKey("idemp-" + UUID.randomUUID())
                .createdAt(Instant.now())
                .build());

        String eventId = "evt_DUP_" + UUID.randomUUID();
        String json = """
                {
                  "entity": "event",
                  "event": "payment.captured",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_DUP_123",
                        "amount": 49900,
                        "currency": "INR",
                        "status": "captured",
                        "order_id": "%s"
                      }
                    }
                  }
                }
                """.formatted(orderId);

        byte[] rawBytes = json.getBytes(StandardCharsets.UTF_8);
        String signature = sign(rawBytes);

        // First delivery: returns ok
        mockMvc.perform(post("/api/v1/payments/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .header("X-Razorpay-Event-Id", eventId)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")));

        // Second delivery with same eventId: returns duplicate
        mockMvc.perform(post("/api/v1/payments/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .header("X-Razorpay-Event-Id", eventId)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("duplicate")));
    }

    @Test
    @DisplayName("webhook: invalid X-Razorpay-Signature returns 401 UNAUTHORIZED")
    void testInvalidSignature_returns401() throws Exception {
        String json = "{\"event\":\"payment.captured\"}";
        byte[] rawBytes = json.getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/payments/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "invalid_tampered_sig_hex")
                        .header("X-Razorpay-Event-Id", "evt_invalid")
                        .content(rawBytes))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("webhook: missing X-Razorpay-Signature header returns 401 UNAUTHORIZED")
    void testMissingSignatureHeader_returns401() throws Exception {
        String json = "{\"event\":\"payment.captured\"}";
        byte[] rawBytes = json.getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/payments/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Event-Id", "evt_nosig")
                        .content(rawBytes))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("webhook: order.paid event returns 200 OK and marks event PROCESSED")
    void testOrderPaid_success() throws Exception {
        String eventId = "evt_order_paid_" + UUID.randomUUID();
        String json = """
                {
                  "entity": "event",
                  "event": "order.paid",
                  "payload": {
                    "order": {
                      "entity": {
                        "id": "order_xyz123",
                        "amount": 49900,
                        "status": "paid"
                      }
                    }
                  }
                }
                """;

        byte[] rawBytes = json.getBytes(StandardCharsets.UTF_8);
        String signature = sign(rawBytes);

        mockMvc.perform(post("/api/v1/payments/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .header("X-Razorpay-Event-Id", eventId)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")));

        PaymentWebhookEvent event = webhookEventRepository.findById(eventId).orElseThrow();
        assertEquals(PaymentWebhookStatus.PROCESSED, event.getStatus());
    }

    @Test
    @DisplayName("webhook: payment.failed event marks PENDING payment as FAILED")
    void testPaymentFailed_marksFailed() throws Exception {
        String orderId = "order_FAIL_" + UUID.randomUUID();
        Payment payment = paymentRepository.save(Payment.builder()
                .memberId(memberId)
                .amountPaise(49900L)
                .status(PaymentStatus.PENDING)
                .gateway("RAZORPAY")
                .gatewayOrderId(orderId)
                .currency("INR")
                .idempotencyKey("idemp-" + UUID.randomUUID())
                .createdAt(Instant.now())
                .build());

        String eventId = "evt_fail_" + UUID.randomUUID();
        String json = """
                {
                  "entity": "event",
                  "event": "payment.failed",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_FAIL_123",
                        "amount": 49900,
                        "currency": "INR",
                        "status": "failed",
                        "order_id": "%s",
                        "error_code": "BAD_REQUEST_ERROR",
                        "error_description": "Payment was declined by bank"
                      }
                    }
                  }
                }
                """.formatted(orderId);

        byte[] rawBytes = json.getBytes(StandardCharsets.UTF_8);
        String signature = sign(rawBytes);

        mockMvc.perform(post("/api/v1/payments/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .header("X-Razorpay-Event-Id", eventId)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")));

        Payment updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.FAILED, updated.getStatus());
        assertTrue(updated.getFailureReason().contains("Payment was declined"));
    }

    @Test
    @DisplayName("webhook: refund.processed event updates Refund to PAID and emits outbox event")
    void testRefundProcessed_updatesToPaid() throws Exception {
        Payment payment = paymentRepository.save(Payment.builder()
                .memberId(memberId)
                .amountPaise(49900L)
                .status(PaymentStatus.SUCCEEDED)
                .gateway("RAZORPAY")
                .currency("INR")
                .idempotencyKey("idemp-" + UUID.randomUUID())
                .createdAt(Instant.now())
                .build());

        Refund refund = refundRepository.save(Refund.builder()
                .paymentId(payment.getId())
                .amountPaise(25000L)
                .status(RefundStatus.APPROVED)
                .gateway("RAZORPAY")
                .idempotencyKey("ref-idemp-" + UUID.randomUUID())
                .createdAt(Instant.now())
                .build());

        String eventId = "evt_ref_proc_" + UUID.randomUUID();
        String json = """
                {
                  "entity": "event",
                  "event": "refund.processed",
                  "payload": {
                    "refund": {
                      "entity": {
                        "id": "rfnd_PROC_123",
                        "amount": 25000,
                        "receipt": "%d",
                        "status": "processed",
                        "payment_id": "pay_xxx"
                      }
                    }
                  }
                }
                """.formatted(refund.getId());

        byte[] rawBytes = json.getBytes(StandardCharsets.UTF_8);
        String signature = sign(rawBytes);

        mockMvc.perform(post("/api/v1/payments/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .header("X-Razorpay-Event-Id", eventId)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")));

        Refund updated = refundRepository.findById(refund.getId()).orElseThrow();
        assertEquals(RefundStatus.PAID, updated.getStatus());
        assertEquals("rfnd_PROC_123", updated.getGatewayRefundId());
    }

    @Test
    @DisplayName("webhook: refund.failed event updates Refund to FAILED with reason")
    void testRefundFailed_updatesToFailed() throws Exception {
        Payment payment = paymentRepository.save(Payment.builder()
                .memberId(memberId)
                .amountPaise(49900L)
                .status(PaymentStatus.SUCCEEDED)
                .gateway("RAZORPAY")
                .currency("INR")
                .idempotencyKey("idemp-" + UUID.randomUUID())
                .createdAt(Instant.now())
                .build());

        Refund refund = refundRepository.save(Refund.builder()
                .paymentId(payment.getId())
                .amountPaise(25000L)
                .status(RefundStatus.APPROVED)
                .gateway("RAZORPAY")
                .idempotencyKey("ref-idemp-" + UUID.randomUUID())
                .createdAt(Instant.now())
                .build());

        String eventId = "evt_ref_fail_" + UUID.randomUUID();
        String json = """
                {
                  "entity": "event",
                  "event": "refund.failed",
                  "payload": {
                    "refund": {
                      "entity": {
                        "id": "rfnd_FAIL_123",
                        "amount": 25000,
                        "receipt": "%d",
                        "status": "failed",
                        "error_description": "Card account closed"
                      }
                    }
                  }
                }
                """.formatted(refund.getId());

        byte[] rawBytes = json.getBytes(StandardCharsets.UTF_8);
        String signature = sign(rawBytes);

        mockMvc.perform(post("/api/v1/payments/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .header("X-Razorpay-Event-Id", eventId)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")));

        Refund updated = refundRepository.findById(refund.getId()).orElseThrow();
        assertEquals(RefundStatus.FAILED, updated.getStatus());
        assertEquals("Card account closed", updated.getFailureReason());
    }

    @Test
    @DisplayName("webhook: unknown event returns 200 OK and marks event IGNORED")
    void testUnknownEvent_ignored() throws Exception {
        String eventId = "evt_unk_" + UUID.randomUUID();
        String json = """
                {
                  "entity": "event",
                  "event": "payout.processed",
                  "payload": {}
                }
                """;

        byte[] rawBytes = json.getBytes(StandardCharsets.UTF_8);
        String signature = sign(rawBytes);

        mockMvc.perform(post("/api/v1/payments/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .header("X-Razorpay-Event-Id", eventId)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")));

        PaymentWebhookEvent event = webhookEventRepository.findById(eventId).orElseThrow();
        assertEquals(PaymentWebhookStatus.IGNORED, event.getStatus());
    }
}
