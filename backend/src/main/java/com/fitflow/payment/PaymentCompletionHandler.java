package com.fitflow.payment;

public interface PaymentCompletionHandler {

    void onPaymentSucceeded(Long paymentId);

    void onPaymentFailed(Long paymentId, String reason);
}
