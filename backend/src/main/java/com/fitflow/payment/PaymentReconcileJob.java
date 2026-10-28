package com.fitflow.payment;

import com.fitflow.common.job.JobLockRepository;
import com.fitflow.notification.OutboxService;
import com.fitflow.payment.entity.Payment;
import com.fitflow.payment.entity.Refund;
import com.fitflow.payment.gateway.GatewayRouter;
import com.fitflow.payment.gateway.PaymentGateway;
import com.fitflow.payment.gateway.PaymentGateway.ConfirmResult;
import com.fitflow.payment.gateway.PaymentGateway.GatewayPaymentStatus;
import com.fitflow.payment.gateway.PaymentGateway.RefundResult;
import com.fitflow.payment.model.PaymentStatus;
import com.fitflow.payment.model.RefundStatus;
import com.fitflow.payment.repository.PaymentRepository;
import com.fitflow.payment.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentReconcileJob {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final GatewayRouter gatewayRouter;
    private final PaymentFinalizer paymentFinalizer;
    private final JobLockRepository jobLockRepository;
    private final OutboxService outboxService;
    private final Clock clock;

    private final String instanceId = UUID.randomUUID().toString();

    @Scheduled(fixedDelay = 60000)
    public void runScheduled() {
        runReconcile();
    }

    @Transactional
    public void runReconcile() {
        Instant now = clock.instant();
        Instant expiredBefore = now.minusSeconds(300); // 5 minutes stale lock expiry

        int acquired = 0;
        try {
            acquired = jobLockRepository.acquireLock("payment_reconcile", now, instanceId, expiredBefore);
        } catch (Exception e) {
            log.warn("JobLock acquire failed for payment_reconcile: {}", e.getMessage());
        }

        if (acquired == 0) {
            log.debug("Payment reconcile lock held by another instance. Skipping.");
            return;
        }

        try {
            log.debug("Executing payment reconcile job at {}", now);

            // 1. Reconcile PENDING payments older than 15 minutes (or 2 minutes)
            Instant cutoff = now.minusSeconds(900); // 15 minutes
            Instant sixtyMinutesAgo = now.minusSeconds(3600); // 60 minutes
            List<Payment> pendingPayments = paymentRepository.findByStatusAndCreatedAtBefore(
                    PaymentStatus.PENDING, cutoff
            );

            for (Payment payment : pendingPayments) {
                if (payment.getGatewayOrderId() == null) continue;
                try {
                    PaymentGateway gateway = gatewayRouter.getGateway(payment.getGateway());
                    GatewayPaymentStatus status = gateway.fetchStatus(payment.getGatewayOrderId());

                    if (status.status() == PaymentStatus.SUCCEEDED) {
                        log.info("Reconcile job: Payment id={} resolved to SUCCEEDED", payment.getId());
                        paymentFinalizer.finalizePayment(
                                payment.getId(),
                                new ConfirmResult(PaymentStatus.SUCCEEDED, status.gatewayPaymentId(), null, status.amountPaise())
                        );
                    } else if (status.status() == PaymentStatus.FAILED) {
                        log.info("Reconcile job: Payment id={} resolved to FAILED", payment.getId());
                        paymentFinalizer.finalizePayment(
                                payment.getId(),
                                new ConfirmResult(PaymentStatus.FAILED, status.gatewayPaymentId(), status.failureReason(), status.amountPaise())
                        );
                    } else if (status.status() == PaymentStatus.PENDING && payment.getCreatedAt().isBefore(sixtyMinutesAgo)) {
                        log.info("Reconcile job: Payment id={} expired after >60 minutes in PENDING", payment.getId());
                        paymentFinalizer.finalizePayment(
                                payment.getId(),
                                new ConfirmResult(PaymentStatus.FAILED, status.gatewayPaymentId(), "Payment expired (>60m)", status.amountPaise())
                        );
                    }
                } catch (Exception e) {
                    log.error("Reconcile failed for payment id={}: {}", payment.getId(), e.getMessage());
                }
            }

            // 2. Reconcile APPROVED refunds older than 10 minutes
            Instant tenMinutesAgo = now.minusSeconds(600);
            List<Refund> pendingRefunds = refundRepository.findByStatusAndCreatedAtBefore(
                    RefundStatus.APPROVED, tenMinutesAgo
            );

            for (Refund refund : pendingRefunds) {
                if (refund.getGatewayRefundId() == null || refund.getGatewayRefundId().isBlank()) {
                    continue;
                }
                try {
                    PaymentGateway gateway = gatewayRouter.getGateway(refund.getGateway());
                    RefundResult result = gateway.fetchRefundStatus(refund.getGatewayRefundId());

                    if (result.status() == RefundStatus.PAID) {
                        log.info("Reconcile job: Refund id={} resolved to PAID", refund.getId());
                        refund.setStatus(RefundStatus.PAID);
                        refundRepository.save(refund);

                        paymentRepository.findById(refund.getPaymentId()).ifPresent(payment -> {
                            outboxService.append(
                                    "refund",
                                    refund.getId(),
                                    "refund.issued",
                                    Map.of(
                                            "refundId", refund.getId(),
                                            "paymentId", refund.getPaymentId(),
                                            "memberId", payment.getMemberId(),
                                            "amountPaise", refund.getAmountPaise(),
                                            "gatewayRefundId", refund.getGatewayRefundId()
                                    )
                            );
                        });
                    } else if (result.status() == RefundStatus.FAILED) {
                        log.info("Reconcile job: Refund id={} resolved to FAILED", refund.getId());
                        refund.setStatus(RefundStatus.FAILED);
                        refund.setFailureReason(result.failureReason());
                        refundRepository.save(refund);
                    }
                } catch (Exception e) {
                    log.error("Reconcile failed for refund id={}: {}", refund.getId(), e.getMessage());
                }
            }
        } finally {
            try {
                jobLockRepository.releaseLock("payment_reconcile", instanceId);
            } catch (Exception ignored) {}
        }
    }
}
