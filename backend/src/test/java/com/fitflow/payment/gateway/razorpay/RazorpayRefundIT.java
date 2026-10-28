package com.fitflow.payment.gateway.razorpay;

import com.fitflow.payment.gateway.PaymentGateway.RefundRequest;
import com.fitflow.payment.gateway.PaymentGateway.RefundResult;
import com.fitflow.payment.model.RefundStatus;
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

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles("test")
public class RazorpayRefundIT {

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
    private RazorpayPaymentGateway razorpayPaymentGateway;

    @Autowired
    private RazorpayClient razorpayClient;

    private static final String TEST_KEY_ID = "rzp_test_ScYoa7HPECrM5A";
    private static final String TEST_KEY_SECRET = "FAKEsecret1234567890abcd";

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        razorpayClient.setCredentials(TEST_KEY_ID, TEST_KEY_SECRET, "test_webhook_sec", null);
    }

    @AfterEach
    void tearDown() {
        razorpayClient.deleteCredentials();
    }

    @Test
    @DisplayName("refund: successfully creates refund via POST /v1/payments/{id}/refund and passes receipt for idempotency")
    void testRefund_success() {
        String paymentId = "pay_TEST_REFUND";
        String expectedRefundId = "rfnd_XYZ123";

        wireMockServer.stubFor(post(urlEqualTo("/v1/payments/" + paymentId + "/refund"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "%s",
                                  "entity": "refund",
                                  "amount": 25000,
                                  "currency": "INR",
                                  "payment_id": "%s",
                                  "receipt": "ref_55",
                                  "status": "processed",
                                  "created_at": 1600000000
                                }
                                """.formatted(expectedRefundId, paymentId))));

        RefundRequest request = new RefundRequest(
                paymentId,
                25000L,
                "ref_55",
                Map.of("refundId", 55L)
        );

        RefundResult result = razorpayPaymentGateway.refund(request);

        assertNotNull(result);
        assertEquals(RefundStatus.PAID, result.status());
        assertEquals(expectedRefundId, result.gatewayRefundId());
        assertNull(result.failureReason());

        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/payments/" + paymentId + "/refund")));
    }

    @Test
    @DisplayName("refund: maps gateway failure to RefundResult with status FAILED")
    void testRefund_gatewayFailure() {
        String paymentId = "pay_TEST_FAIL";

        wireMockServer.stubFor(post(urlEqualTo("/v1/payments/" + paymentId + "/refund"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "error": {
                                    "code": "BAD_REQUEST_ERROR",
                                    "description": "Payment has already been refunded fully"
                                  }
                                }
                                """)));

        RefundRequest request = new RefundRequest(
                paymentId,
                10000L,
                "ref_56",
                Map.of("refundId", 56L)
        );

        RefundResult result = razorpayPaymentGateway.refund(request);

        assertNotNull(result);
        assertEquals(RefundStatus.FAILED, result.status());
        assertNotNull(result.failureReason());
    }

    @Test
    @DisplayName("fetchRefundStatus: returns PAID when Razorpay status is 'processed'")
    void testFetchRefundStatus_processed() {
        String refundId = "rfnd_PROCESSED_99";

        wireMockServer.stubFor(get(urlEqualTo("/v1/refunds/" + refundId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "%s",
                                  "entity": "refund",
                                  "amount": 49900,
                                  "status": "processed"
                                }
                                """.formatted(refundId))));

        RefundResult result = razorpayPaymentGateway.fetchRefundStatus(refundId);

        assertNotNull(result);
        assertEquals(RefundStatus.PAID, result.status());
        assertEquals(refundId, result.gatewayRefundId());
    }

    @Test
    @DisplayName("fetchRefundStatus: returns FAILED when Razorpay status is 'failed'")
    void testFetchRefundStatus_failed() {
        String refundId = "rfnd_FAILED_99";

        wireMockServer.stubFor(get(urlEqualTo("/v1/refunds/" + refundId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "%s",
                                  "entity": "refund",
                                  "amount": 49900,
                                  "status": "failed"
                                }
                                """.formatted(refundId))));

        RefundResult result = razorpayPaymentGateway.fetchRefundStatus(refundId);

        assertNotNull(result);
        assertEquals(RefundStatus.FAILED, result.status());
    }

    @Test
    @DisplayName("fetchRefundStatus: returns APPROVED when status is pending")
    void testFetchRefundStatus_pending() {
        String refundId = "rfnd_PENDING_99";

        wireMockServer.stubFor(get(urlEqualTo("/v1/refunds/" + refundId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "%s",
                                  "entity": "refund",
                                  "amount": 49900,
                                  "status": "pending"
                                }
                                """.formatted(refundId))));

        RefundResult result = razorpayPaymentGateway.fetchRefundStatus(refundId);

        assertNotNull(result);
        assertEquals(RefundStatus.APPROVED, result.status());
    }

    @Test
    @DisplayName("fetchRefundStatus: returns FAILED on null or blank refundId")
    void testFetchRefundStatus_nullOrBlank() {
        RefundResult resNull = razorpayPaymentGateway.fetchRefundStatus(null);
        assertEquals(RefundStatus.FAILED, resNull.status());

        RefundResult resBlank = razorpayPaymentGateway.fetchRefundStatus("   ");
        assertEquals(RefundStatus.FAILED, resBlank.status());
    }
}
