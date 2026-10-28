package com.fitflow.payment;

import com.fitflow.common.job.JobLockRepository;
import com.fitflow.notification.OutboxService;
import com.fitflow.payment.entity.Payment;
import com.fitflow.payment.gateway.GatewayRouter;
import com.fitflow.payment.gateway.PaymentGateway;
import com.fitflow.payment.gateway.PaymentGateway.ConfirmResult;
import com.fitflow.payment.gateway.PaymentGateway.GatewayPaymentStatus;
import com.fitflow.payment.model.PaymentStatus;
import com.fitflow.payment.repository.PaymentRepository;
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

            // Reconcile PENDING payments older than 2 minutes
            Instant twoMinutesAgo = now.minusSeconds(120);
            List<Payment> pendingPayments = paymentRepository.findByStatusAndCreatedAtBefore(
                    PaymentStatus.PENDING, twoMinutesAgo
            );

            for (Payment payment : pendingPayments) {
                if (payment.getGatewayOrderId() == null) continue;
                try {
                    PaymentGateway gateway = gatewayRouter.getGateway(payment.getGateway());
                    GatewayPaymentStatus status = gateway.fetchStatus(payment.getGatewayOrderId());

                    if (status.status() != PaymentStatus.PENDING) {
                        log.info("Reconcile job: Payment id={} resolved to {}", payment.getId(), status.status());
                        paymentFinalizer.finalizePayment(
                                payment.getId(),
                                new ConfirmResult(status.status(), status.gatewayPaymentId(), status.failureReason(), status.amountPaise())
                        );
                    }
                } catch (Exception e) {
                    log.error("Reconcile failed for payment id={}: {}", payment.getId(), e.getMessage());
                }
            }
        } finally {
            try {
                jobLockRepository.releaseLock("payment_reconcile", instanceId);
            } catch (Exception ignored) {}
        }
    }
}
