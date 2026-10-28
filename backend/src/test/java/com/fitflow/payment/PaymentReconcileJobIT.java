package com.fitflow.payment;

import com.fitflow.payment.entity.Payment;
import com.fitflow.payment.entity.Refund;
import com.fitflow.payment.gateway.razorpay.RazorpayClient;
import com.fitflow.payment.model.PaymentStatus;
import com.fitflow.payment.model.RefundStatus;
import com.fitflow.payment.repository.PaymentRepository;
import com.fitflow.payment.repository.RefundRepository;
import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
public class PaymentReconcileJobIT {

    private static WireMockServer wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());

    @BeforeAll
    static void startWireMock() {
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("fitflow.payments.razorpay.base-url", wireMockServer::baseUrl);
    }

    @Autowired
    private PaymentReconcileJob paymentReconcileJob;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private RazorpayClient razorpayClient;

    @Autowired
    private UserRepository userRepository;

    private Long memberId;

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        razorpayClient.setCredentials("rzp_test_ScYoa7HPECrM5A", "FAKEsecret1234567890abcd", "whsec", null);

        User user = userRepository.findByEmail("reconcile_test_user@example.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("reconcile_test_user@example.com")
                        .passwordHash("hash")
                        .fullName("Reconcile Tester")
                        .status(User.Status.ACTIVE)
                        .build()));
        memberId = user.getId();
    }

    @AfterEach
    void tearDown() {
        razorpayClient.deleteCredentials();
    }

    @Test
    @DisplayName("reconcile: sweeps pending payment and finalizes to SUCCEEDED when Razorpay says captured")
    void testReconcile_pendingToCaptured() {
        String orderId = "order_REC_CAP_" + UUID.randomUUID();
        Payment payment = paymentRepository.save(Payment.builder()
                .memberId(memberId)
                .amountPaise(49900L)
                .status(PaymentStatus.PENDING)
                .gateway("RAZORPAY")
                .gatewayOrderId(orderId)
                .currency("INR")
                .idempotencyKey("idemp-" + UUID.randomUUID())
                .createdAt(Instant.now().minusSeconds(1000)) // 16 minutes old (>15m)
                .build());

        wireMockServer.stubFor(get(urlEqualTo("/v1/orders/" + orderId + "/payments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "entity": "collection",
                                  "count": 1,
                                  "items": [
                                    {
                                      "id": "pay_REC_123",
                                      "amount": 49900,
                                      "status": "captured",
                                      "created_at": 1600000000
                                    }
                                  ]
                                }
                                """)));

        paymentReconcileJob.runReconcile();

        Payment updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.SUCCEEDED, updated.getStatus());
        assertEquals("pay_REC_123", updated.getGatewayPaymentId());
    }

    @Test
    @DisplayName("reconcile: sweeps pending payment and finalizes to FAILED when Razorpay says failed")
    void testReconcile_pendingToFailed() {
        String orderId = "order_REC_FAIL_" + UUID.randomUUID();
        Payment payment = paymentRepository.save(Payment.builder()
                .memberId(memberId)
                .amountPaise(49900L)
                .status(PaymentStatus.PENDING)
                .gateway("RAZORPAY")
                .gatewayOrderId(orderId)
                .currency("INR")
                .idempotencyKey("idemp-" + UUID.randomUUID())
                .createdAt(Instant.now().minusSeconds(1000)) // 16 minutes old
                .build());

        wireMockServer.stubFor(get(urlEqualTo("/v1/orders/" + orderId + "/payments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "entity": "collection",
                                  "count": 1,
                                  "items": [
                                    {
                                      "id": "pay_REC_FAIL",
                                      "amount": 49900,
                                      "status": "failed",
                                      "error_description": "Card expired",
                                      "created_at": 1600000000
                                    }
                                  ]
                                }
                                """)));

        paymentReconcileJob.runReconcile();

        Payment updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.FAILED, updated.getStatus());
        assertNotNull(updated.getFailureReason());
    }

    @Test
    @DisplayName("reconcile: marks payment FAILED when created > 60m ago and still PENDING")
    void testReconcile_pendingExpiredAfter60Minutes() {
        String orderId = "order_REC_EXP_" + UUID.randomUUID();
        Payment payment = paymentRepository.save(Payment.builder()
                .memberId(memberId)
                .amountPaise(49900L)
                .status(PaymentStatus.PENDING)
                .gateway("RAZORPAY")
                .gatewayOrderId(orderId)
                .currency("INR")
                .idempotencyKey("idemp-" + UUID.randomUUID())
                .createdAt(Instant.now().minusSeconds(3700)) // 61 minutes old (>60m)
                .build());

        wireMockServer.stubFor(get(urlEqualTo("/v1/orders/" + orderId + "/payments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "entity": "collection",
                                  "count": 1,
                                  "items": [
                                    {
                                      "id": "pay_REC_PENDING",
                                      "amount": 49900,
                                      "status": "created",
                                      "created_at": 1600000000
                                    }
                                  ]
                                }
                                """)));

        paymentReconcileJob.runReconcile();

        Payment updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.FAILED, updated.getStatus());
        assertEquals("Payment expired (>60m)", updated.getFailureReason());
    }

    @Test
    @DisplayName("reconcile: sweeps approved refund and updates to PAID when Razorpay status is processed")
    void testReconcile_refundProcessed() {
        Payment payment = paymentRepository.save(Payment.builder()
                .memberId(memberId)
                .amountPaise(49900L)
                .status(PaymentStatus.SUCCEEDED)
                .gateway("RAZORPAY")
                .currency("INR")
                .idempotencyKey("idemp-" + UUID.randomUUID())
                .createdAt(Instant.now())
                .build());

        String gatewayRefundId = "rfnd_REC_PROC_" + UUID.randomUUID();
        Refund refund = refundRepository.save(Refund.builder()
                .paymentId(payment.getId())
                .amountPaise(25000L)
                .status(RefundStatus.APPROVED)
                .gateway("RAZORPAY")
                .gatewayRefundId(gatewayRefundId)
                .idempotencyKey("ref-idemp-" + UUID.randomUUID())
                .createdAt(Instant.now().minusSeconds(700)) // > 10m
                .build());

        wireMockServer.stubFor(get(urlEqualTo("/v1/refunds/" + gatewayRefundId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "%s",
                                  "entity": "refund",
                                  "amount": 25000,
                                  "status": "processed"
                                }
                                """.formatted(gatewayRefundId))));

        paymentReconcileJob.runReconcile();

        Refund updated = refundRepository.findById(refund.getId()).orElseThrow();
        assertEquals(RefundStatus.PAID, updated.getStatus());
    }

    @Test
    @DisplayName("reconcile: sweeps approved refund and updates to FAILED when Razorpay status is failed")
    void testReconcile_refundFailed() {
        Payment payment = paymentRepository.save(Payment.builder()
                .memberId(memberId)
                .amountPaise(49900L)
                .status(PaymentStatus.SUCCEEDED)
                .gateway("RAZORPAY")
                .currency("INR")
                .idempotencyKey("idemp-" + UUID.randomUUID())
                .createdAt(Instant.now())
                .build());

        String gatewayRefundId = "rfnd_REC_FAIL_" + UUID.randomUUID();
        Refund refund = refundRepository.save(Refund.builder()
                .paymentId(payment.getId())
                .amountPaise(25000L)
                .status(RefundStatus.APPROVED)
                .gateway("RAZORPAY")
                .gatewayRefundId(gatewayRefundId)
                .idempotencyKey("ref-idemp-" + UUID.randomUUID())
                .createdAt(Instant.now().minusSeconds(700)) // > 10m
                .build());

        wireMockServer.stubFor(get(urlEqualTo("/v1/refunds/" + gatewayRefundId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "%s",
                                  "entity": "refund",
                                  "amount": 25000,
                                  "status": "failed"
                                }
                                """.formatted(gatewayRefundId))));

        paymentReconcileJob.runReconcile();

        Refund updated = refundRepository.findById(refund.getId()).orElseThrow();
        assertEquals(RefundStatus.FAILED, updated.getStatus());
    }
}
