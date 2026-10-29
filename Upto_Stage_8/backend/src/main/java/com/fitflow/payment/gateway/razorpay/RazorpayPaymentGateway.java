package com.fitflow.payment.gateway.razorpay;

import com.fitflow.common.error.ApiException;
import com.fitflow.payment.gateway.PaymentGateway;
import com.fitflow.payment.gateway.razorpay.dto.RazorpayOrderPaymentsResponse;
import com.fitflow.payment.gateway.razorpay.dto.RazorpayOrderRequest;
import com.fitflow.payment.gateway.razorpay.dto.RazorpayOrderResponse;
import com.fitflow.payment.gateway.razorpay.dto.RazorpayPaymentResponse;
import com.fitflow.payment.gateway.razorpay.dto.RazorpayRefundRequest;
import com.fitflow.payment.gateway.razorpay.dto.RazorpayRefundResponse;
import com.fitflow.payment.model.PaymentStatus;
import com.fitflow.payment.model.RefundStatus;
import com.fitflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

@Component("razorpayPaymentGateway")
@Slf4j
@RequiredArgsConstructor
public class RazorpayPaymentGateway implements PaymentGateway {

    public static final String GATEWAY_NAME = "RAZORPAY";

    public static volatile boolean sanityBreakSkipAmountCheck = false;

    private final RazorpayClient razorpayClient;
    private final UserRepository userRepository;

    @Override
    public String name() {
        return GATEWAY_NAME;
    }

    @Override
    public PaymentIntent createIntent(CreateIntentRequest request) {
        String currency = request.currency() != null ? request.currency() : "INR";
        RazorpayOrderRequest orderReq = new RazorpayOrderRequest(
                request.amountPaise(),
                currency,
                request.receipt(),
                request.notes()
        );

        RazorpayOrderResponse orderResponse = razorpayClient.createOrder(orderReq);

        Map<String, Object> clientPayload = new HashMap<>();
        clientPayload.put("keyId", razorpayClient.getKeyId().orElse(""));
        clientPayload.put("orderId", orderResponse.id());
        clientPayload.put("amount", orderResponse.amount());
        clientPayload.put("currency", orderResponse.currency() != null ? orderResponse.currency() : "INR");
        clientPayload.put("name", "FitFlow");
        clientPayload.put("description", "FitFlow Membership");

        Map<String, String> prefill = new HashMap<>();
        if (request.notes() != null && request.notes().get("memberId") != null) {
            try {
                Long memberId = Long.valueOf(request.notes().get("memberId").toString());
                userRepository.findById(memberId).ifPresent(user -> {
                    if (user.getFullName() != null) prefill.put("name", user.getFullName());
                    if (user.getEmail() != null) prefill.put("email", user.getEmail());
                    if (user.getPhone() != null) prefill.put("contact", user.getPhone());
                });
            } catch (Exception ignored) {}
        }
        clientPayload.put("prefill", prefill);
        clientPayload.put("theme", Map.of("color", "#1976d2"));

        return new PaymentIntent(
                GATEWAY_NAME,
                orderResponse.id(),
                orderResponse.amount(),
                orderResponse.currency(),
                clientPayload
        );
    }

    @Override
    public ConfirmResult confirm(ConfirmRequest request) {
        Map<String, Object> payload = request.clientPayload();
        String clientOrderId = payload != null ? (String) payload.get("razorpay_order_id") : null;
        String clientPaymentId = payload != null ? (String) payload.get("razorpay_payment_id") : null;
        String clientSignature = payload != null ? (String) payload.get("razorpay_signature") : null;

        // 1. Order mismatch check
        if (clientOrderId == null || !clientOrderId.equals(request.gatewayOrderId())) {
            throw ApiException.badRequest("Order ID mismatch: expected " + request.gatewayOrderId() + " but got " + clientOrderId);
        }

        // 2. Verify signature
        String keySecret = razorpayClient.getKeySecret()
                .orElseThrow(() -> ApiException.badRequest("Razorpay key secret is not configured"));

        boolean validSignature = RazorpaySignatureValidator.verifyCheckoutSignature(
                clientOrderId, clientPaymentId, clientSignature, keySecret
        );
        if (!validSignature) {
            throw ApiException.paymentSignatureInvalid("Invalid payment signature");
        }

        // 3. Fetch payment from Razorpay
        RazorpayPaymentResponse payment = razorpayClient.fetchPayment(clientPaymentId);

        // Confirm order ID matches
        if (payment.orderId() == null || !payment.orderId().equals(request.gatewayOrderId())) {
            throw ApiException.badRequest("Payment order ID does not match expected order");
        }

        // Confirm currency matches
        if (!"INR".equalsIgnoreCase(payment.currency())) {
            throw ApiException.badRequest("Currency mismatch: expected INR but got " + payment.currency());
        }

        // Confirm amount matches
        long expectedAmount = request.amountPaise();
        if (!sanityBreakSkipAmountCheck && expectedAmount > 0) {
            if (payment.amount() != expectedAmount) {
                throw ApiException.badRequest("Payment amount mismatch: expected " + expectedAmount + " but got " + payment.amount());
            }
        }

        // 4. Status checks
        if ("failed".equalsIgnoreCase(payment.status())) {
            String failureReason = payment.errorDescription() != null
                    ? payment.errorDescription()
                    : (payment.errorCode() != null ? payment.errorCode() : "Payment failed");
            return new ConfirmResult(PaymentStatus.FAILED, clientPaymentId, failureReason, payment.amount());
        }

        if ("authorized".equalsIgnoreCase(payment.status())) {
            // Auto-capture
            payment = razorpayClient.capturePayment(clientPaymentId, expectedAmount > 0 ? expectedAmount : payment.amount(), "INR");
        }

        if ("captured".equalsIgnoreCase(payment.status())) {
            return new ConfirmResult(PaymentStatus.SUCCEEDED, clientPaymentId, null, payment.amount());
        }

        return new ConfirmResult(PaymentStatus.FAILED, clientPaymentId, "Unexpected payment status: " + payment.status(), payment.amount());
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        try {
            RazorpayRefundRequest refundReq = new RazorpayRefundRequest(
                    request.amountPaise(),
                    request.receipt(),
                    request.notes()
            );
            RazorpayRefundResponse response = razorpayClient.createRefund(request.gatewayPaymentId(), refundReq);
            return new RefundResult(RefundStatus.PAID, response.id(), null);
        } catch (Exception e) {
            log.error("Razorpay refund failed: {}", e.getMessage());
            return new RefundResult(RefundStatus.FAILED, null, e.getMessage());
        }
    }

    @Override
    public RefundResult fetchRefundStatus(String gatewayRefundId) {
        if (gatewayRefundId == null || gatewayRefundId.isBlank()) {
            return new RefundResult(RefundStatus.FAILED, null, "Missing gatewayRefundId");
        }
        try {
            RazorpayRefundResponse response = razorpayClient.fetchRefund(gatewayRefundId);
            if ("processed".equalsIgnoreCase(response.status())) {
                return new RefundResult(RefundStatus.PAID, response.id(), null);
            } else if ("failed".equalsIgnoreCase(response.status())) {
                return new RefundResult(RefundStatus.FAILED, response.id(), "Refund failed in Razorpay");
            } else {
                return new RefundResult(RefundStatus.APPROVED, response.id(), null);
            }
        } catch (Exception e) {
            log.error("Failed to fetch refund status for {}: {}", gatewayRefundId, e.getMessage());
            return new RefundResult(RefundStatus.APPROVED, gatewayRefundId, e.getMessage());
        }
    }

    @Override
    public GatewayPaymentStatus fetchStatus(String gatewayOrderId) {
        RazorpayOrderPaymentsResponse orderPayments = razorpayClient.fetchOrderPayments(gatewayOrderId);
        if (orderPayments == null || orderPayments.items() == null || orderPayments.items().isEmpty()) {
            return new GatewayPaymentStatus(PaymentStatus.PENDING, null, null, 0L);
        }

        var newest = orderPayments.items().stream()
                .max(Comparator.comparing(RazorpayPaymentResponse::createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(orderPayments.items().get(0));

        if ("captured".equalsIgnoreCase(newest.status())) {
            return new GatewayPaymentStatus(PaymentStatus.SUCCEEDED, newest.id(), null, newest.amount());
        } else if ("failed".equalsIgnoreCase(newest.status())) {
            String reason = newest.errorDescription() != null ? newest.errorDescription() : "Payment failed";
            return new GatewayPaymentStatus(PaymentStatus.FAILED, newest.id(), reason, newest.amount());
        } else {
            return new GatewayPaymentStatus(PaymentStatus.PENDING, newest.id(), null, newest.amount());
        }
    }

    @Override
    public Map<String, Object> getClientPayload(
            String gatewayOrderId,
            long amountPaise,
            String currency,
            Long paymentId,
            Long memberId,
            String memberName,
            String memberEmail,
            String memberPhone
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("keyId", razorpayClient.getKeyId().orElse(""));
        payload.put("orderId", gatewayOrderId);
        payload.put("amount", amountPaise);
        payload.put("currency", currency != null ? currency : "INR");
        payload.put("name", "FitFlow");
        payload.put("description", "FitFlow Membership");

        Map<String, String> prefill = new HashMap<>();
        if (memberName != null) prefill.put("name", memberName);
        if (memberEmail != null) prefill.put("email", memberEmail);
        if (memberPhone != null) prefill.put("contact", memberPhone);
        payload.put("prefill", prefill);

        Map<String, Object> notes = new HashMap<>();
        if (paymentId != null) notes.put("paymentId", paymentId);
        if (memberId != null) notes.put("memberId", memberId);
        payload.put("notes", notes);
        payload.put("theme", Map.of("color", "#1976d2"));

        return payload;
    }
}
