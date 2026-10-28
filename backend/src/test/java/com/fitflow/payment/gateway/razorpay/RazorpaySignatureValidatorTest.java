package com.fitflow.payment.gateway.razorpay;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RazorpaySignatureValidatorTest {

    private static final String CHECKOUT_SECRET = "FAKEsecret1234567890abcd";
    private static final String ORDER_ID = "order_TEST123";
    private static final String PAYMENT_ID = "pay_TEST456";
    // Pre-computed vector 1: HMAC-SHA256 of "order_TEST123|pay_TEST456" with secret "FAKEsecret1234567890abcd"
    private static final String VALID_CHECKOUT_SIG = "74f3718b9f7ff5a2419ad793b6ee393286abfa67093cb160d962d05e4ce4a98f";

    private static final String WEBHOOK_SECRET = "whsec_FAKE";
    private static final byte[] WEBHOOK_BODY = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
    // Pre-computed vector 2: HMAC-SHA256 of '{"event":"payment.captured"}' with secret "whsec_FAKE"
    private static final String VALID_WEBHOOK_SIG = "b711014997d2144a8aa33c5414b5d5cb20c4a31aa053bf1eec1df0814c2ed863";

    @AfterEach
    void resetSanityBreak() {
        RazorpaySignatureValidator.sanityBreakAlwaysTrue = false;
    }

    @Test
    @DisplayName("Vector 1: Checkout signature matches independently computed HMAC-SHA256")
    void testVector1_checkoutSignatureValid() {
        assertTrue(RazorpaySignatureValidator.verifyCheckoutSignature(
                ORDER_ID, PAYMENT_ID, VALID_CHECKOUT_SIG, CHECKOUT_SECRET
        ));
    }

    @Test
    @DisplayName("Vector 2: Webhook signature matches independently computed HMAC-SHA256")
    void testVector2_webhookSignatureValid() {
        assertTrue(RazorpaySignatureValidator.verifyWebhookSignature(
                WEBHOOK_BODY, VALID_WEBHOOK_SIG, WEBHOOK_SECRET
        ));
    }

    @Test
    @DisplayName("Vector 3: Checkout signature fails with wrong secret")
    void testVector3_wrongSecretFails() {
        assertFalse(RazorpaySignatureValidator.verifyCheckoutSignature(
                ORDER_ID, PAYMENT_ID, VALID_CHECKOUT_SIG, "whsec_FAKE"
        ));
    }

    @Test
    @DisplayName("Vector 4: Checkout signature fails with wrong order ID")
    void testVector4_wrongOrderIdFails() {
        assertFalse(RazorpaySignatureValidator.verifyCheckoutSignature(
                "order_TEST999", PAYMENT_ID, VALID_CHECKOUT_SIG, CHECKOUT_SECRET
        ));
    }

    @Test
    @DisplayName("Vector 5: Checkout signature fails with flipped character (tampered signature)")
    void testVector5_flippedBitFails() {
        // Flipped last character 'f' -> 'e'
        String tampered = "74f3718b9f7ff5a2419ad793b6ee393286abfa67093cb160d962d05e4ce4a98e";
        assertFalse(RazorpaySignatureValidator.verifyCheckoutSignature(
                ORDER_ID, PAYMENT_ID, tampered, CHECKOUT_SECRET
        ));
    }

    @Test
    @DisplayName("Checkout verification rejects null or blank parameters")
    void testCheckoutNullAndBlankHandling() {
        assertFalse(RazorpaySignatureValidator.verifyCheckoutSignature(null, PAYMENT_ID, VALID_CHECKOUT_SIG, CHECKOUT_SECRET));
        assertFalse(RazorpaySignatureValidator.verifyCheckoutSignature(ORDER_ID, null, VALID_CHECKOUT_SIG, CHECKOUT_SECRET));
        assertFalse(RazorpaySignatureValidator.verifyCheckoutSignature(ORDER_ID, PAYMENT_ID, null, CHECKOUT_SECRET));
        assertFalse(RazorpaySignatureValidator.verifyCheckoutSignature(ORDER_ID, PAYMENT_ID, VALID_CHECKOUT_SIG, null));
        assertFalse(RazorpaySignatureValidator.verifyCheckoutSignature(ORDER_ID, PAYMENT_ID, "   ", CHECKOUT_SECRET));
        assertFalse(RazorpaySignatureValidator.verifyCheckoutSignature(ORDER_ID, PAYMENT_ID, VALID_CHECKOUT_SIG, "   "));
    }

    @Test
    @DisplayName("Webhook verification rejects null or blank parameters")
    void testWebhookNullAndBlankHandling() {
        assertFalse(RazorpaySignatureValidator.verifyWebhookSignature(null, VALID_WEBHOOK_SIG, WEBHOOK_SECRET));
        assertFalse(RazorpaySignatureValidator.verifyWebhookSignature(WEBHOOK_BODY, null, WEBHOOK_SECRET));
        assertFalse(RazorpaySignatureValidator.verifyWebhookSignature(WEBHOOK_BODY, VALID_WEBHOOK_SIG, null));
        assertFalse(RazorpaySignatureValidator.verifyWebhookSignature(WEBHOOK_BODY, "  ", WEBHOOK_SECRET));
        assertFalse(RazorpaySignatureValidator.verifyWebhookSignature(WEBHOOK_BODY, VALID_WEBHOOK_SIG, "  "));
    }

    @Test
    @DisplayName("Checkout verification is case-insensitive with respect to hex string")
    void testCheckoutCaseInsensitiveHex() {
        String uppercaseSig = VALID_CHECKOUT_SIG.toUpperCase();
        assertTrue(RazorpaySignatureValidator.verifyCheckoutSignature(
                ORDER_ID, PAYMENT_ID, uppercaseSig, CHECKOUT_SECRET
        ));
    }

    @Test
    @DisplayName("Webhook verification is case-insensitive with respect to hex string")
    void testWebhookCaseInsensitiveHex() {
        String uppercaseSig = VALID_WEBHOOK_SIG.toUpperCase();
        assertTrue(RazorpaySignatureValidator.verifyWebhookSignature(
                WEBHOOK_BODY, uppercaseSig, WEBHOOK_SECRET
        ));
    }

    @Test
    @DisplayName("Sanity break flag forces verifyCheckoutSignature and verifyWebhookSignature to return true")
    void testSanityBreakFlag() {
        RazorpaySignatureValidator.sanityBreakAlwaysTrue = true;

        assertTrue(RazorpaySignatureValidator.verifyCheckoutSignature("wrong", "wrong", "invalid", "invalid"));
        assertTrue(RazorpaySignatureValidator.verifyWebhookSignature("wrong".getBytes(), "invalid", "invalid"));
    }
}
