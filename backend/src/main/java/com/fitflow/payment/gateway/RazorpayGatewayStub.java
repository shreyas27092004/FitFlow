package com.fitflow.payment.gateway;

import com.fitflow.common.error.ApiException;
import lombok.extern.slf4j.Slf4j;

/**
 * Stub class for Razorpay payment gateway integration.
 * Planned for Stage 5B. Listed in CONTRACT.md "Stubs".
 * Not wired as an active Spring bean by default.
 */
@Slf4j
public class RazorpayGatewayStub implements PaymentGateway {

    public static final String GATEWAY_NAME = "RAZORPAY";

    @Override
    public String name() {
        return GATEWAY_NAME;
    }

    @Override
    public PaymentIntent createIntent(CreateIntentRequest request) {
        log.warn("RazorpayGatewayStub called: createIntent is not implemented in Stage 5");
        throw ApiException.internalError("Razorpay gateway is not configured for Stage 5");
    }

    @Override
    public ConfirmResult confirm(ConfirmRequest request) {
        log.warn("RazorpayGatewayStub called: confirm is not implemented in Stage 5");
        throw ApiException.internalError("Razorpay gateway is not configured for Stage 5");
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        log.warn("RazorpayGatewayStub called: refund is not implemented in Stage 5");
        throw ApiException.internalError("Razorpay gateway is not configured for Stage 5");
    }

    @Override
    public GatewayPaymentStatus fetchStatus(String gatewayOrderId) {
        log.warn("RazorpayGatewayStub called: fetchStatus is not implemented in Stage 5");
        throw ApiException.internalError("Razorpay gateway is not configured for Stage 5");
    }
}
