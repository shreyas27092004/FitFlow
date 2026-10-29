package com.fitflow.payment;

import com.fitflow.common.error.ApiException;
import com.fitflow.notification.OutboxService;
import com.fitflow.payment.entity.Invoice;
import com.fitflow.payment.entity.Payment;
import com.fitflow.payment.gateway.PaymentGateway.ConfirmResult;
import com.fitflow.payment.model.PaymentStatus;
import com.fitflow.payment.repository.InvoiceRepository;
import com.fitflow.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentFinalizer {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final ObjectProvider<PaymentCompletionHandler> completionHandlerProvider;
    private final OutboxService outboxService;
    private final Clock clock;

    public static volatile boolean sanityBreakDisableLockAndStatusGuard = false;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Payment finalizePayment(Long paymentId, ConfirmResult confirmResult) {
        log.info("PaymentFinalizer: Finalizing payment id={} with result status={}", paymentId, confirmResult.status());

        // 1. Guard with SELECT ... FOR UPDATE on payment row
        Payment payment;
        if (sanityBreakDisableLockAndStatusGuard) {
            payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> ApiException.notFound("Payment id " + paymentId + " not found"));
        } else {
            payment = paymentRepository.findByIdForUpdate(paymentId)
                    .orElseThrow(() -> ApiException.notFound("Payment id " + paymentId + " not found"));

            // 2. Status guard: idempotent no-op if already resolved
            if (payment.getStatus() == PaymentStatus.SUCCEEDED || payment.getStatus() == PaymentStatus.FAILED) {
                log.info("Payment id={} is already finalized with status={}. Returning without changes.",
                        paymentId, payment.getStatus());
                return payment;
            }
        }

        Instant now = clock.instant();

        if (confirmResult.status() == PaymentStatus.SUCCEEDED) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setGatewayPaymentId(confirmResult.gatewayPaymentId());
            Payment savedPayment = paymentRepository.saveAndFlush(payment);

            // Generate Invoice if not already present
            if (invoiceRepository.findByPaymentId(paymentId).isEmpty()) {
                String dateStr = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(now);
                String invoiceNumber = "INV-" + dateStr + "-" + paymentId;
                Invoice invoice = Invoice.builder()
                        .paymentId(paymentId)
                        .invoiceNumber(invoiceNumber)
                        .totalPaise(payment.getAmountPaise())
                        .issuedAt(now)
                        .createdAt(now)
                        .build();
                invoiceRepository.save(invoice);
            }

            // Apply pending membership change via completion handler
            completionHandlerProvider.ifAvailable(handler -> handler.onPaymentSucceeded(paymentId));

            // Emit payment.succeeded outbox event
            outboxService.append(
                    "payment",
                    paymentId,
                    "payment.succeeded",
                    Map.of(
                            "paymentId", paymentId,
                            "memberId", payment.getMemberId(),
                            "amountPaise", payment.getAmountPaise(),
                            "gateway", payment.getGateway(),
                            "gatewayPaymentId", payment.getGatewayPaymentId() != null ? payment.getGatewayPaymentId() : ""
                    )
            );

            log.info("Payment id={} finalized as SUCCEEDED with gatewayPaymentId={}", paymentId, payment.getGatewayPaymentId());
            return savedPayment;

        } else if (confirmResult.status() == PaymentStatus.FAILED) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(confirmResult.failureReason());
            Payment savedPayment = paymentRepository.saveAndFlush(payment);

            completionHandlerProvider.ifAvailable(handler -> handler.onPaymentFailed(paymentId, confirmResult.failureReason()));

            // Emit payment.failed outbox event
            outboxService.append(
                    "payment",
                    paymentId,
                    "payment.failed",
                    Map.of(
                            "paymentId", paymentId,
                            "memberId", payment.getMemberId(),
                            "amountPaise", payment.getAmountPaise(),
                            "gateway", payment.getGateway(),
                            "failureReason", payment.getFailureReason() != null ? payment.getFailureReason() : ""
                    )
            );

            log.info("Payment id={} finalized as FAILED with reason={}", paymentId, payment.getFailureReason());
            return savedPayment;

        } else {
            // PENDING status: payment row remains PENDING
            log.info("Payment id={} remains in PENDING status", paymentId);
            return payment;
        }
    }
}
