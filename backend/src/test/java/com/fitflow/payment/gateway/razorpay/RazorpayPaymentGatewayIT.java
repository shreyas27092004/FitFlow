package com.fitflow.payment.gateway.razorpay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.common.error.ApiException;
import com.fitflow.common.error.ErrorCode;
import com.fitflow.payment.gateway.PaymentGateway.ConfirmRequest;
import com.fitflow.payment.gateway.PaymentGateway.ConfirmResult;
import com.fitflow.payment.gateway.PaymentGateway.CreateIntentRequest;
import com.fitflow.payment.gateway.PaymentGateway.GatewayPaymentStatus;
import com.fitflow.payment.gateway.PaymentGateway.PaymentIntent;
import com.fitflow.payment.model.PaymentStatus;
import com.fitflow.settings.SecretsService;
import com.fitflow.settings.entity.AppSetting;
import com.fitflow.settings.repository.AppSettingRepository;
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

import java.util.List;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class RazorpayPaymentGatewayIT {

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

    @Autowired
    private AppSettingRepository appSettingRepository;

    @Autowired
    private SecretsService secretsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_KEY_ID = "rzp_test_ScYoa7HPECrM5A";
    private static final String TEST_KEY_SECRET = "FAKEsecret1234567890abcd";

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        razorpayClient.setCredentials(TEST_KEY_ID, TEST_KEY_SECRET, "test_webhook_sec", null);
    }

    @AfterEach
    void tearDown() {
        RazorpayPaymentGateway.sanityBreakSkipAmountCheck = false;
        razorpayClient.deleteCredentials();
    }

    @Test
    @DisplayName("name() returns 'RAZORPAY'")
    void testName() {
        assertEquals("RAZORPAY", razorpayPaymentGateway.name());
    }

    @Test
    @DisplayName("createIntent: successfully posts to /v1/orders and returns PaymentIntent with clientPayload")
    void testCreateIntent_success() {
        wireMockServer.stubFor(post(urlEqualTo("/v1/orders"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "order_test_999",
                                  "entity": "order",
                                  "amount": 49900,
                                  "amount_paid": 0,
                                  "amount_due": 49900,
                                  "currency": "INR",
                                  "receipt": "pay_101",
                                  "status": "created",
                                  "attempts": 0,
                                  "created_at": 1600000000
                                }
                                """)));

        CreateIntentRequest request = new CreateIntentRequest(
                49900L,
                "INR",
                "pay_101",
                Map.of("paymentId", 101L, "memberId", 99001L)
        );

        PaymentIntent intent = razorpayPaymentGateway.createIntent(request);

        assertNotNull(intent);
        assertEquals("RAZORPAY", intent.gateway());
        assertEquals("order_test_999", intent.gatewayOrderId());
        assertEquals(49900L, intent.amountPaise());
        assertEquals("INR", intent.currency());

        Map<String, Object> payload = intent.clientPayload();
        assertNotNull(payload);
        assertEquals(TEST_KEY_ID, payload.get("keyId"));
        assertEquals("order_test_999", payload.get("orderId"));
        assertEquals(49900L, ((Number) payload.get("amount")).longValue());
        assertEquals("FitFlow", payload.get("name"));
    }

    @Test
    @DisplayName("confirm: successfully confirms payment when Razorpay status is 'captured'")
    void testConfirm_successCaptured() {
        String orderId = "order_TEST123";
        String paymentId = "pay_TEST456";
        String validSignature = RazorpaySignatureValidator.calculateHmacSha256Hex(
                TEST_KEY_SECRET.getBytes(),
                (orderId + "|" + paymentId).getBytes()
        );

        wireMockServer.stubFor(get(urlEqualTo("/v1/payments/" + paymentId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "%s",
                                  "entity": "payment",
                                  "amount": 49900,
                                  "currency": "INR",
                                  "status": "captured",
                                  "order_id": "%s",
                                  "method": "upi",
                                  "created_at": 1600000000
                                }
                                """.formatted(paymentId, orderId))));

        ConfirmRequest request = new ConfirmRequest(
                orderId,
                Map.of(
                        "razorpay_order_id", orderId,
                        "razorpay_payment_id", paymentId,
                        "razorpay_signature", validSignature
                ),
                49900L
        );

        ConfirmResult result = razorpayPaymentGateway.confirm(request);

        assertNotNull(result);
        assertEquals(PaymentStatus.SUCCEEDED, result.status());
        assertEquals(paymentId, result.gatewayPaymentId());
        assertEquals(49900L, result.amountPaise());
    }

    @Test
    @DisplayName("confirm: auto-captures payment when status is 'authorized'")
    void testConfirm_autoCapture() {
        String orderId = "order_TEST123";
        String paymentId = "pay_AUTH456";
        String validSignature = RazorpaySignatureValidator.calculateHmacSha256Hex(
                TEST_KEY_SECRET.getBytes(),
                (orderId + "|" + paymentId).getBytes()
        );

        // Fetch returns authorized
        wireMockServer.stubFor(get(urlEqualTo("/v1/payments/" + paymentId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "%s",
                                  "entity": "payment",
                                  "amount": 89900,
                                  "currency": "INR",
                                  "status": "authorized",
                                  "order_id": "%s",
                                  "created_at": 1600000000
                                }
                                """.formatted(paymentId, orderId))));

        // Capture endpoint returns captured
        wireMockServer.stubFor(post(urlEqualTo("/v1/payments/" + paymentId + "/capture"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "%s",
                                  "entity": "payment",
                                  "amount": 89900,
                                  "currency": "INR",
                                  "status": "captured",
                                  "order_id": "%s",
                                  "created_at": 1600000001
                                }
                                """.formatted(paymentId, orderId))));

        ConfirmRequest request = new ConfirmRequest(
                orderId,
                Map.of(
                        "razorpay_order_id", orderId,
                        "razorpay_payment_id", paymentId,
                        "razorpay_signature", validSignature
                ),
                89900L
        );

        ConfirmResult result = razorpayPaymentGateway.confirm(request);

        assertNotNull(result);
        assertEquals(PaymentStatus.SUCCEEDED, result.status());
        assertEquals(paymentId, result.gatewayPaymentId());

        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/payments/" + paymentId + "/capture")));
    }

    @Test
    @DisplayName("confirm: throws 400 VALIDATION_FAILED when client orderId does not match expected orderId")
    void testConfirm_orderMismatch() {
        ConfirmRequest request = new ConfirmRequest(
                "order_EXPECTED",
                Map.of(
                        "razorpay_order_id", "order_TAMPERED",
                        "razorpay_payment_id", "pay_123",
                        "razorpay_signature", "sig_123"
                ),
                49900L
        );

        ApiException ex = assertThrows(ApiException.class, () -> razorpayPaymentGateway.confirm(request));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Order ID mismatch"));
    }

    @Test
    @DisplayName("confirm: throws 400 PAYMENT_SIGNATURE_INVALID when signature verification fails")
    void testConfirm_invalidSignature() {
        String orderId = "order_TEST123";
        String paymentId = "pay_TEST456";

        ConfirmRequest request = new ConfirmRequest(
                orderId,
                Map.of(
                        "razorpay_order_id", orderId,
                        "razorpay_payment_id", paymentId,
                        "razorpay_signature", "invalid_tampered_signature_hex"
                ),
                49900L
        );

        ApiException ex = assertThrows(ApiException.class, () -> razorpayPaymentGateway.confirm(request));
        assertEquals(ErrorCode.PAYMENT_SIGNATURE_INVALID, ex.getErrorCode());
    }

    @Test
    @DisplayName("confirm: throws 400 VALIDATION_FAILED when Razorpay payment amount does not match expected order amount")
    void testConfirm_amountMismatch() {
        String orderId = "order_TEST123";
        String paymentId = "pay_TEST456";
        String validSignature = RazorpaySignatureValidator.calculateHmacSha256Hex(
                TEST_KEY_SECRET.getBytes(),
                (orderId + "|" + paymentId).getBytes()
        );

        wireMockServer.stubFor(get(urlEqualTo("/v1/payments/" + paymentId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "%s",
                                  "entity": "payment",
                                  "amount": 29900,
                                  "currency": "INR",
                                  "status": "captured",
                                  "order_id": "%s"
                                }
                                """.formatted(paymentId, orderId))));

        ConfirmRequest request = new ConfirmRequest(
                orderId,
                Map.of(
                        "razorpay_order_id", orderId,
                        "razorpay_payment_id", paymentId,
                        "razorpay_signature", validSignature
                ),
                49900L // Expected 49900, but Razorpay returned 29900
        );

        ApiException ex = assertThrows(ApiException.class, () -> razorpayPaymentGateway.confirm(request));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("amount mismatch"));
    }

    @Test
    @DisplayName("confirm: throws 400 VALIDATION_FAILED when Razorpay currency does not match INR")
    void testConfirm_currencyMismatch() {
        String orderId = "order_TEST123";
        String paymentId = "pay_TEST456";
        String validSignature = RazorpaySignatureValidator.calculateHmacSha256Hex(
                TEST_KEY_SECRET.getBytes(),
                (orderId + "|" + paymentId).getBytes()
        );

        wireMockServer.stubFor(get(urlEqualTo("/v1/payments/" + paymentId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "%s",
                                  "entity": "payment",
                                  "amount": 49900,
                                  "currency": "USD",
                                  "status": "captured",
                                  "order_id": "%s"
                                }
                                """.formatted(paymentId, orderId))));

        ConfirmRequest request = new ConfirmRequest(
                orderId,
                Map.of(
                        "razorpay_order_id", orderId,
                        "razorpay_payment_id", paymentId,
                        "razorpay_signature", validSignature
                ),
                49900L
        );

        ApiException ex = assertThrows(ApiException.class, () -> razorpayPaymentGateway.confirm(request));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Currency mismatch"));
    }

    @Test
    @DisplayName("confirm: returns FAILED with error description when Razorpay status is 'failed'")
    void testConfirm_paymentFailedStatus() {
        String orderId = "order_TEST123";
        String paymentId = "pay_FAILED456";
        String validSignature = RazorpaySignatureValidator.calculateHmacSha256Hex(
                TEST_KEY_SECRET.getBytes(),
                (orderId + "|" + paymentId).getBytes()
        );

        wireMockServer.stubFor(get(urlEqualTo("/v1/payments/" + paymentId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "%s",
                                  "entity": "payment",
                                  "amount": 49900,
                                  "currency": "INR",
                                  "status": "failed",
                                  "order_id": "%s",
                                  "error_code": "BAD_REQUEST_ERROR",
                                  "error_description": "Card expired"
                                }
                                """.formatted(paymentId, orderId))));

        ConfirmRequest request = new ConfirmRequest(
                orderId,
                Map.of(
                        "razorpay_order_id", orderId,
                        "razorpay_payment_id", paymentId,
                        "razorpay_signature", validSignature
                ),
                49900L
        );

        ConfirmResult result = razorpayPaymentGateway.confirm(request);

        assertNotNull(result);
        assertEquals(PaymentStatus.FAILED, result.status());
        assertEquals("Card expired", result.failureReason());
    }

    @Test
    @DisplayName("confirm: handles 401 from Razorpay by translating to ApiException")
    void testConfirm_gateway401() {
        String orderId = "order_TEST123";
        String paymentId = "pay_TEST456";
        String validSignature = RazorpaySignatureValidator.calculateHmacSha256Hex(
                TEST_KEY_SECRET.getBytes(),
                (orderId + "|" + paymentId).getBytes()
        );

        wireMockServer.stubFor(get(urlEqualTo("/v1/payments/" + paymentId))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"code\":\"BAD_REQUEST_ERROR\",\"description\":\"Unauthorized\"}}")));

        ConfirmRequest request = new ConfirmRequest(
                orderId,
                Map.of(
                        "razorpay_order_id", orderId,
                        "razorpay_payment_id", paymentId,
                        "razorpay_signature", validSignature
                ),
                49900L
        );

        assertThrows(ApiException.class, () -> razorpayPaymentGateway.confirm(request));
    }

    @Test
    @DisplayName("fetchStatus: selects newest payment and returns SUCCEEDED for captured status")
    void testFetchStatus_captured() {
        String orderId = "order_STATUS123";

        wireMockServer.stubFor(get(urlEqualTo("/v1/orders/" + orderId + "/payments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "entity": "collection",
                                  "count": 2,
                                  "items": [
                                    {
                                      "id": "pay_OLDER",
                                      "amount": 49900,
                                      "status": "failed",
                                      "created_at": 1600000000,
                                      "error_description": "Card declined"
                                    },
                                    {
                                      "id": "pay_NEWER",
                                      "amount": 49900,
                                      "status": "captured",
                                      "created_at": 1600000050
                                    }
                                  ]
                                }
                                """)));

        GatewayPaymentStatus status = razorpayPaymentGateway.fetchStatus(orderId);

        assertNotNull(status);
        assertEquals(PaymentStatus.SUCCEEDED, status.status());
        assertEquals("pay_NEWER", status.gatewayPaymentId());
        assertEquals(49900L, status.amountPaise());
    }

    @Test
    @DisplayName("fetchStatus: selects newest payment and returns FAILED when newest is failed")
    void testFetchStatus_failed() {
        String orderId = "order_FAIL123";

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
                                      "id": "pay_FAIL",
                                      "amount": 49900,
                                      "status": "failed",
                                      "created_at": 1600000000,
                                      "error_description": "Insufficient funds"
                                    }
                                  ]
                                }
                                """)));

        GatewayPaymentStatus status = razorpayPaymentGateway.fetchStatus(orderId);

        assertNotNull(status);
        assertEquals(PaymentStatus.FAILED, status.status());
        assertEquals("pay_FAIL", status.gatewayPaymentId());
        assertEquals("Insufficient funds", status.failureReason());
    }

    @Test
    @DisplayName("fetchStatus: returns PENDING when order payments collection is empty")
    void testFetchStatus_emptyCollection() {
        String orderId = "order_EMPTY123";

        wireMockServer.stubFor(get(urlEqualTo("/v1/orders/" + orderId + "/payments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "entity": "collection",
                                  "count": 0,
                                  "items": []
                                }
                                """)));

        GatewayPaymentStatus status = razorpayPaymentGateway.fetchStatus(orderId);

        assertNotNull(status);
        assertEquals(PaymentStatus.PENDING, status.status());
    }
}
