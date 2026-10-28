package com.fitflow.payment;

import com.fitflow.common.error.ApiException;
import com.fitflow.notification.OutboxService;
import com.fitflow.payment.dto.AdminRefundResponse;
import com.fitflow.payment.dto.InvoiceDetailResponse;
import com.fitflow.payment.dto.MemberPaymentSummaryDto;
import com.fitflow.payment.dto.PaymentDetailResponse;
import com.fitflow.payment.dto.PaymentIntentResponse;
import com.fitflow.payment.entity.Invoice;
import com.fitflow.payment.entity.Payment;
import com.fitflow.payment.entity.Refund;
import com.fitflow.payment.entity.RefundAllocation;
import com.fitflow.payment.gateway.GatewayRouter;
import com.fitflow.payment.gateway.PaymentGateway;
import com.fitflow.payment.gateway.PaymentGateway.ConfirmRequest;
import com.fitflow.payment.gateway.PaymentGateway.ConfirmResult;
import com.fitflow.payment.gateway.PaymentGateway.CreateIntentRequest;
import com.fitflow.payment.gateway.PaymentGateway.GatewayPaymentStatus;
import com.fitflow.payment.gateway.PaymentGateway.PaymentIntent;
import com.fitflow.payment.gateway.PaymentGateway.RefundRequest;
import com.fitflow.payment.gateway.PaymentGateway.RefundResult;
import com.fitflow.payment.model.PaymentStatus;
import com.fitflow.payment.model.RefundStatus;
import com.fitflow.payment.repository.InvoiceRepository;
import com.fitflow.payment.repository.PaymentRepository;
import com.fitflow.payment.repository.RefundAllocationRepository;
import com.fitflow.payment.repository.RefundRepository;
import com.fitflow.settings.entity.AppSetting;
import com.fitflow.settings.repository.AppSettingRepository;
import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final RefundAllocationRepository refundAllocationRepository;
    private final InvoiceRepository invoiceRepository;
    private final AppSettingRepository appSettingRepository;
    private final GatewayRouter gatewayRouter;
    private final PaymentFinalizer paymentFinalizer;
    private final OutboxService outboxService;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public PaymentIntentResponse createPaymentIntent(
            Long memberId,
            Long amountPaise,
            String currency,
            String idempotencyKey,
            Instant expiresAt,
            Map<String, Object> notes
    ) {
        // Idempotency check: if payment already exists for this member and idempotency key
        Optional<Payment> existingOpt = paymentRepository.findByMemberIdAndIdempotencyKey(memberId, idempotencyKey);
        if (existingOpt.isPresent()) {
            Payment existing = existingOpt.get();
            log.info("Idempotent replay: returning existing payment id={} for memberId={}", existing.getId(), memberId);
            return new PaymentIntentResponse(
                    existing.getId(),
                    existing.getStatus().name(),
                    existing.getGateway(),
                    existing.getGatewayOrderId(),
                    existing.getAmountPaise(),
                    existing.getCurrency(),
                    Map.of("gatewayOrderId", existing.getGatewayOrderId() != null ? existing.getGatewayOrderId() : ""),
                    expiresAt
            );
        }

        Instant now = clock.instant();
        PaymentGateway activeGateway = gatewayRouter.getActiveGateway();

        Payment payment = Payment.builder()
                .memberId(memberId)
                .amountPaise(amountPaise)
                .status(PaymentStatus.PENDING)
                .gateway(activeGateway.name())
                .currency(currency != null ? currency : "INR")
                .idempotencyKey(idempotencyKey)
                .createdAt(now)
                .build();

        Payment savedPayment = paymentRepository.saveAndFlush(payment);

        PaymentIntent intent = activeGateway.createIntent(new CreateIntentRequest(
                amountPaise,
                currency != null ? currency : "INR",
                savedPayment.getId().toString(),
                notes
        ));

        savedPayment.setGatewayOrderId(intent.gatewayOrderId());
        paymentRepository.saveAndFlush(savedPayment);

        // Emit outbox event payment.intent_created
        outboxService.append(
                "payment",
                savedPayment.getId(),
                "payment.intent_created",
                Map.of(
                        "paymentId", savedPayment.getId(),
                        "memberId", memberId,
                        "amountPaise", amountPaise,
                        "gateway", activeGateway.name(),
                        "gatewayOrderId", intent.gatewayOrderId()
                )
        );

        return new PaymentIntentResponse(
                savedPayment.getId(),
                "REQUIRES_PAYMENT",
                savedPayment.getGateway(),
                intent.gatewayOrderId(),
                amountPaise,
                savedPayment.getCurrency(),
                intent.clientPayload(),
                expiresAt
        );
    }

    @Transactional
    public PaymentDetailResponse confirmPayment(Long paymentId, Long memberId, Map<String, Object> clientPayload) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment not found"));

        if (!payment.getMemberId().equals(memberId)) {
            // Member owner only; others get 404
            throw ApiException.notFound("Payment not found");
        }

        // Idempotency: if already SUCCEEDED or FAILED, return immediately
        if (payment.getStatus() == PaymentStatus.SUCCEEDED || payment.getStatus() == PaymentStatus.FAILED) {
            log.info("confirmPayment: Payment id={} already in final status={}", paymentId, payment.getStatus());
            return new PaymentDetailResponse(
                    payment.getId(),
                    payment.getStatus(),
                    payment.getGateway(),
                    payment.getAmountPaise(),
                    payment.getStatus() == PaymentStatus.SUCCEEDED
            );
        }

        PaymentGateway gateway = gatewayRouter.getGateway(payment.getGateway());
        ConfirmResult confirmResult = gateway.confirm(new ConfirmRequest(payment.getGatewayOrderId(), clientPayload, payment.getAmountPaise()));

        Payment finalizedPayment = paymentFinalizer.finalizePayment(paymentId, confirmResult);

        return new PaymentDetailResponse(
                finalizedPayment.getId(),
                finalizedPayment.getStatus(),
                finalizedPayment.getGateway(),
                finalizedPayment.getAmountPaise(),
                finalizedPayment.getStatus() == PaymentStatus.SUCCEEDED
        );
    }

    @Transactional(readOnly = true)
    public PaymentDetailResponse getPayment(Long paymentId, Long memberId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment not found"));

        if (!payment.getMemberId().equals(memberId)) {
            throw ApiException.notFound("Payment not found");
        }

        return new PaymentDetailResponse(
                payment.getId(),
                payment.getStatus(),
                payment.getGateway(),
                payment.getAmountPaise(),
                payment.getStatus() == PaymentStatus.SUCCEEDED
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getClientPayload(Long paymentId, Long memberId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment not found"));

        if (!payment.getMemberId().equals(memberId)) {
            throw ApiException.notFound("Payment not found");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw ApiException.conflict("Payment status is " + payment.getStatus() + ", expected PENDING");
        }

        User user = userRepository.findById(memberId).orElse(null);
        String name = user != null ? user.getFullName() : null;
        String email = user != null ? user.getEmail() : null;
        String phone = user != null ? user.getPhone() : null;

        PaymentGateway gateway = gatewayRouter.getGateway(payment.getGateway());
        return gateway.getClientPayload(
                payment.getGatewayOrderId(),
                payment.getAmountPaise(),
                payment.getCurrency(),
                payment.getId(),
                memberId,
                name,
                email,
                phone
        );
    }

    @Transactional
    public Refund createAndExecuteRefund(
            Long memberId,
            long amountPaise,
            String idempotencyKey,
            Long adminActorId
    ) {
        Instant now = clock.instant();

        // 1. Gather all SUCCEEDED payments for this member, newest first
        List<Payment> succeededPayments = paymentRepository.findByMemberIdAndStatusOrderByCreatedAtDesc(
                memberId, PaymentStatus.SUCCEEDED
        );

        if (succeededPayments.isEmpty()) {
            throw ApiException.conflict("No succeeded payments found to refund for member id " + memberId);
        }

        // Check total refundable cash
        long totalRefundable = 0L;
        for (Payment p : succeededPayments) {
            long refundedOnPayment = refundRepository.sumActiveRefundsForPayment(p.getId());
            totalRefundable += Math.max(0L, p.getAmountPaise() - refundedOnPayment);
        }

        if (totalRefundable <= 0) {
            throw ApiException.conflict("No remaining refundable cash in payment chain for member id " + memberId);
        }

        long refundAmountToAllocate = Math.min(amountPaise, totalRefundable);

        // 2. Allocate across payments, newest first
        Payment primaryPayment = succeededPayments.get(0);

        // Check if refund already exists for (paymentId, idempotencyKey)
        Optional<Refund> existingRefund = refundRepository.findByPaymentIdAndIdempotencyKey(
                primaryPayment.getId(), idempotencyKey
        );
        if (existingRefund.isPresent()) {
            log.info("Idempotent replay: returning existing refund id={}", existingRefund.get().getId());
            return existingRefund.get();
        }

        // 3. Determine if auto-approved
        long autoApproveLimit = 100_000L; // default 100000 paise (1000 INR)
        Optional<AppSetting> limitSetting = appSettingRepository.findById("refund.auto-approve-limit-paise");
        if (limitSetting.isPresent() && !limitSetting.get().getValue().isBlank()) {
            try {
                autoApproveLimit = Long.parseLong(limitSetting.get().getValue().trim());
            } catch (NumberFormatException ignored) {}
        }

        boolean autoApprove = (refundAmountToAllocate <= autoApproveLimit);
        RefundStatus initialStatus = autoApprove ? RefundStatus.APPROVED : RefundStatus.PENDING_APPROVAL;

        // 4. Compute allocations across succeeded payments (newest first)
        List<Map.Entry<Long, Long>> allocationsToSave = new ArrayList<>();
        long remainingToAllocate = refundAmountToAllocate;
        for (Payment p : succeededPayments) {
            if (remainingToAllocate <= 0) break;
            long alreadyRefunded = refundRepository.sumActiveRefundsForPayment(p.getId());
            long availableOnP = Math.max(0L, p.getAmountPaise() - alreadyRefunded);
            if (availableOnP <= 0) continue;

            long allocation = Math.min(remainingToAllocate, availableOnP);
            allocationsToSave.add(Map.entry(p.getId(), allocation));
            remainingToAllocate -= allocation;
        }

        Refund refund = Refund.builder()
                .paymentId(primaryPayment.getId())
                .amountPaise(refundAmountToAllocate)
                .status(initialStatus)
                .approvedBy(autoApprove ? adminActorId : null)
                .idempotencyKey(idempotencyKey)
                .gateway(primaryPayment.getGateway())
                .createdAt(now)
                .build();

        Refund savedRefund = refundRepository.saveAndFlush(refund);

        // Save allocations
        for (Map.Entry<Long, Long> entry : allocationsToSave) {
            RefundAllocation refundAllocation = RefundAllocation.builder()
                    .id(new RefundAllocation.RefundAllocationId(savedRefund.getId(), entry.getKey()))
                    .amountPaise(entry.getValue())
                    .build();
            refundAllocationRepository.save(refundAllocation);
        }

        // 5. If auto-approved, disburse via gateway immediately
        if (autoApprove) {
            PaymentGateway gateway = gatewayRouter.getGateway(primaryPayment.getGateway());
            RefundResult refundResult = gateway.refund(new RefundRequest(
                    primaryPayment.getGatewayPaymentId(),
                    refundAmountToAllocate,
                    savedRefund.getId().toString(),
                    Map.of("refundId", savedRefund.getId())
            ));

            if (refundResult.status() == RefundStatus.PAID) {
                savedRefund.setStatus(RefundStatus.PAID);
                savedRefund.setGatewayRefundId(refundResult.gatewayRefundId());
                refundRepository.saveAndFlush(savedRefund);

                outboxService.append(
                        "refund",
                        savedRefund.getId(),
                        "refund.issued",
                        Map.of(
                                "refundId", savedRefund.getId(),
                                "paymentId", primaryPayment.getId(),
                                "memberId", memberId,
                                "amountPaise", refundAmountToAllocate,
                                "gatewayRefundId", refundResult.gatewayRefundId() != null ? refundResult.gatewayRefundId() : ""
                        )
                );
            } else {
                savedRefund.setStatus(RefundStatus.FAILED);
                savedRefund.setFailureReason(refundResult.failureReason());
                refundRepository.saveAndFlush(savedRefund);
            }
        } else {
            // Emits refund.pending_approval
            outboxService.append(
                    "refund",
                    savedRefund.getId(),
                    "refund.pending_approval",
                    Map.of(
                            "refundId", savedRefund.getId(),
                            "paymentId", primaryPayment.getId(),
                            "memberId", memberId,
                            "amountPaise", refundAmountToAllocate
                    )
            );
            log.info("Refund id={} exceeds auto-approve limit ({}), created as PENDING_APPROVAL",
                    savedRefund.getId(), autoApproveLimit);
        }

        return savedRefund;
    }

    @Transactional
    public AdminRefundResponse approveRefund(Long refundId, Long adminId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> ApiException.notFound("Refund id " + refundId + " not found"));

        if (refund.getStatus() != RefundStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("Refund is in status " + refund.getStatus() + ", cannot approve");
        }

        // Two-person rule: Cannot be approved by the same person who triggered it where applicable
        if (refund.getApprovedBy() != null && refund.getApprovedBy().equals(adminId)) {
            throw ApiException.conflict("Two-person rule violation: Admin cannot approve their own refund request");
        }

        Payment payment = paymentRepository.findById(refund.getPaymentId())
                .orElseThrow(() -> ApiException.notFound("Associated payment not found"));

        PaymentGateway gateway = gatewayRouter.getGateway(payment.getGateway());
        RefundResult result = gateway.refund(new RefundRequest(
                payment.getGatewayPaymentId(),
                refund.getAmountPaise(),
                refund.getId().toString(),
                Map.of("refundId", refund.getId())
        ));

        if (result.status() == RefundStatus.PAID) {
            refund.setStatus(RefundStatus.PAID);
            refund.setApprovedBy(adminId);
            refund.setGatewayRefundId(result.gatewayRefundId());
            Refund saved = refundRepository.saveAndFlush(refund);

            outboxService.append(
                    "refund",
                    saved.getId(),
                    "refund.issued",
                    Map.of(
                            "refundId", saved.getId(),
                            "paymentId", payment.getId(),
                            "memberId", payment.getMemberId(),
                            "amountPaise", saved.getAmountPaise(),
                            "gatewayRefundId", result.gatewayRefundId() != null ? result.gatewayRefundId() : ""
                    )
            );
            return toAdminRefundResponse(saved);
        } else {
            refund.setStatus(RefundStatus.FAILED);
            refund.setFailureReason(result.failureReason());
            Refund saved = refundRepository.saveAndFlush(refund);
            return toAdminRefundResponse(saved);
        }
    }

    @Transactional
    public AdminRefundResponse rejectRefund(Long refundId, Long adminId, String reason) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> ApiException.notFound("Refund id " + refundId + " not found"));

        if (refund.getStatus() != RefundStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("Refund is in status " + refund.getStatus() + ", cannot reject");
        }

        refund.setStatus(RefundStatus.REJECTED);
        refund.setApprovedBy(adminId);
        refund.setFailureReason(reason);
        Refund saved = refundRepository.saveAndFlush(refund);
        return toAdminRefundResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<AdminRefundResponse> getRefunds(RefundStatus status, Pageable pageable) {
        Page<Refund> refunds = status != null ?
                refundRepository.findByStatus(status, pageable) :
                refundRepository.findAll(pageable);

        return refunds.map(r -> new AdminRefundResponse(
                r.getId(),
                r.getPaymentId(),
                r.getAmountPaise(),
                r.getStatus(),
                r.getApprovedBy(),
                r.getGatewayRefundId(),
                r.getCreatedAt()
        ));
    }

    @Transactional(readOnly = true)
    public Page<MemberPaymentSummaryDto> getMemberPayments(Long memberId, Pageable pageable) {
        Page<Payment> page = paymentRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);
        return page.map(p -> {
            List<Refund> refunds = refundRepository.findByPaymentId(p.getId());
            List<MemberPaymentSummaryDto.RefundSummaryDto> refundDtos = refunds.stream()
                    .map(r -> new MemberPaymentSummaryDto.RefundSummaryDto(
                            r.getId(),
                            r.getAmountPaise(),
                            r.getStatus(),
                            r.getCreatedAt()
                    )).toList();

            return new MemberPaymentSummaryDto(
                    p.getId(),
                    p.getAmountPaise(),
                    p.getCurrency(),
                    p.getStatus(),
                    p.getGateway(),
                    p.getCreatedAt(),
                    refundDtos
            );
        });
    }

    @Transactional(readOnly = true)
    public InvoiceDetailResponse getInvoice(Long invoiceId, Long memberId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> ApiException.notFound("Invoice not found"));

        Payment payment = paymentRepository.findById(invoice.getPaymentId())
                .orElseThrow(() -> ApiException.notFound("Payment not found"));

        if (!payment.getMemberId().equals(memberId)) {
            // Member owner only
            throw ApiException.notFound("Invoice not found");
        }

        return new InvoiceDetailResponse(
                invoice.getId(),
                invoice.getPaymentId(),
                invoice.getInvoiceNumber(),
                invoice.getTotalPaise(),
                invoice.getIssuedAt()
        );
    }

    @Transactional(readOnly = true)
    public long getTotalRefundableCashInChain(Long memberId) {
        List<Payment> payments = paymentRepository.findByMemberIdAndStatusOrderByCreatedAtDesc(
                memberId, PaymentStatus.SUCCEEDED
        );
        long total = 0L;
        for (Payment p : payments) {
            long refunded = refundRepository.sumActiveRefundsForPayment(p.getId());
            total += Math.max(0L, p.getAmountPaise() - refunded);
        }
        return total;
    }

    private AdminRefundResponse toAdminRefundResponse(Refund r) {
        return new AdminRefundResponse(
                r.getId(),
                r.getPaymentId(),
                r.getAmountPaise(),
                r.getStatus(),
                r.getApprovedBy(),
                r.getGatewayRefundId(),
                r.getCreatedAt()
        );
    }
}
