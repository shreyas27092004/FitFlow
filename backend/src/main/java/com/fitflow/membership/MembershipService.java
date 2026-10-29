package com.fitflow.membership;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.common.error.ApiException;
import com.fitflow.common.job.JobLockRepository;
import com.fitflow.notification.OutboxService;
import com.fitflow.payment.PaymentCompletionHandler;
import com.fitflow.payment.PaymentService;
import com.fitflow.payment.dto.PaymentIntentResponse;
import com.fitflow.payment.entity.Payment;
import com.fitflow.payment.repository.PaymentRepository;
import com.fitflow.membership.dto.ChangeMembershipRequest;
import com.fitflow.membership.dto.ChangePreviewRequest;
import com.fitflow.membership.dto.ChangePreviewResponse;
import com.fitflow.membership.dto.CreatePlanRequest;
import com.fitflow.membership.dto.MemberMembershipSummaryResponse;
import com.fitflow.membership.dto.PlanDto;
import com.fitflow.membership.dto.SubscribeRequest;
import com.fitflow.membership.dto.UpdatePlanRequest;
import com.fitflow.membership.entity.FreeSessionGrant;
import com.fitflow.membership.entity.Membership;
import com.fitflow.membership.entity.MembershipChange;
import com.fitflow.membership.entity.MembershipPlan;
import com.fitflow.membership.entity.PendingMembershipChange;
import com.fitflow.membership.model.MembershipStatus;
import com.fitflow.membership.model.PendingChangeKind;
import com.fitflow.membership.model.PendingChangeStatus;
import com.fitflow.membership.proration.ProrationInput;
import com.fitflow.membership.proration.ProrationResult;
import com.fitflow.membership.proration.ProrationService;
import com.fitflow.membership.repository.FreeSessionGrantRepository;
import com.fitflow.membership.repository.MembershipChangeRepository;
import com.fitflow.membership.repository.MembershipPlanRepository;
import com.fitflow.membership.repository.MembershipRepository;
import com.fitflow.membership.repository.PendingMembershipChangeRepository;
import com.fitflow.settings.entity.AppSetting;
import com.fitflow.settings.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class MembershipService implements PaymentCompletionHandler {

    private final MembershipPlanRepository planRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipChangeRepository membershipChangeRepository;
    private final PendingMembershipChangeRepository pendingChangeRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final ProrationService prorationService;
    private final AppSettingRepository appSettingRepository;
    private final FreeSessionGrantRepository freeSessionGrantRepository;
    private final JobLockRepository jobLockRepository;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public static volatile boolean sanityBreakApplyMembershipChangeEarly = false;

    private final String instanceId = UUID.randomUUID().toString();

    // -------------------------------------------------------------------------
    // Plan Catalog
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PlanDto> getActivePlans() {
        return planRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(this::toPlanDto)
                .toList();
    }

    @Transactional
    public PlanDto createPlan(CreatePlanRequest request) {
        if (planRepository.existsByCode(request.code())) {
            throw ApiException.conflict("Plan with code '" + request.code() + "' already exists");
        }

        Instant now = clock.instant();
        MembershipPlan plan = MembershipPlan.builder()
                .code(request.code().trim().toUpperCase())
                .name(request.name().trim())
                .pricePaise(request.pricePaise())
                .durationDays(request.durationDays())
                .sessionQuota(request.sessionQuota())
                .isFree(Boolean.TRUE.equals(request.isFree()))
                .freeSessions(request.freeSessions())
                .active(true)
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toPlanDto(planRepository.save(plan));
    }

    @Transactional
    public PlanDto updatePlan(Long id, UpdatePlanRequest request) {
        MembershipPlan plan = planRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Plan id " + id + " not found"));

        if (!plan.getVersion().equals(request.version())) {
            throw ApiException.optimisticLock("Plan was modified concurrently");
        }

        if (Boolean.FALSE.equals(request.active())) {
            // Cannot deactivate a plan if it currently has active memberships
            long activeCount = membershipRepository.countByPlanIdAndStatus(plan.getId(), MembershipStatus.ACTIVE);
            if (activeCount > 0) {
                throw ApiException.conflict("Cannot deactivate plan '" + plan.getCode() + "' with " + activeCount + " active memberships");
            }
            plan.setActive(false);
        } else if (Boolean.TRUE.equals(request.active())) {
            plan.setActive(true);
        }

        if (request.name() != null && !request.name().isBlank()) {
            plan.setName(request.name().trim());
        }
        if (request.pricePaise() != null) {
            plan.setPricePaise(request.pricePaise());
        }
        if (request.durationDays() != null) {
            plan.setDurationDays(request.durationDays());
        }
        if (request.sessionQuota() != null) {
            plan.setSessionQuota(request.sessionQuota());
        }
        if (request.sortOrder() != null) {
            plan.setSortOrder(request.sortOrder());
        }

        plan.setUpdatedAt(clock.instant());
        return toPlanDto(planRepository.save(plan));
    }

    // -------------------------------------------------------------------------
    // Member Self-Service
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public MemberMembershipSummaryResponse getMemberMembership(Long memberId) {
        Instant now = clock.instant();

        // 1. FREE membership
        MembershipPlan freePlan = getFreePlan();
        Optional<Membership> freeMemOpt = membershipRepository.findFirstByMemberIdAndPlanIdAndStatus(
                memberId, freePlan.getId(), MembershipStatus.ACTIVE
        );
        int freeRemaining = freeMemOpt.map(m -> Math.max(0, (freePlan.getFreeSessions() != null ? freePlan.getFreeSessions() : 3) - m.getSessionsUsed()))
                .orElse(0);

        // 2. Paid membership
        List<Membership> activeMemberships = membershipRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE);
        Optional<Membership> paidMemOpt = activeMemberships.stream()
                .filter(m -> !m.getPlanId().equals(freePlan.getId()))
                .findFirst();

        MemberMembershipSummaryResponse.PaidMembershipDto paidDto = null;
        if (paidMemOpt.isPresent()) {
            Membership paidMem = paidMemOpt.get();
            MembershipPlan plan = planRepository.findById(paidMem.getPlanId()).orElse(null);
            if (plan != null) {
                Integer sessionsRemaining = plan.getSessionQuota() != null ?
                        Math.max(0, paidMem.getSessionsTotal() - paidMem.getSessionsUsed()) : null;

                int daysRemaining = 0;
                if (paidMem.getEndsAt() != null) {
                    Duration duration = Duration.between(now, paidMem.getEndsAt());
                    long seconds = duration.getSeconds();
                    daysRemaining = seconds > 0 ? (int) ((seconds + 86400L - 1L) / 86400L) : 0;
                }

                paidDto = new MemberMembershipSummaryResponse.PaidMembershipDto(
                        plan.getCode(),
                        plan.getName(),
                        paidMem.getStartedAt(),
                        paidMem.getEndsAt(),
                        sessionsRemaining,
                        daysRemaining
                );
            }
        }

        return new MemberMembershipSummaryResponse(freeRemaining, paidDto);
    }

    @Transactional(readOnly = true)
    public ChangePreviewResponse changePreview(Long memberId, ChangePreviewRequest request) {
        Instant now = clock.instant();
        MembershipPlan freePlan = getFreePlan();

        List<Membership> activeMemberships = membershipRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE);
        Optional<Membership> currentPaidOpt = activeMemberships.stream()
                .filter(m -> !m.getPlanId().equals(freePlan.getId()))
                .findFirst();

        long cashInChain = paymentService.getTotalRefundableCashInChain(memberId);
        BigDecimal feePercent = getCancelFeePercent();

        boolean isCancel = "CANCEL".equalsIgnoreCase(request.action());

        if (isCancel) {
            if (currentPaidOpt.isEmpty()) {
                throw ApiException.conflict("No active paid membership to cancel");
            }
            Membership current = currentPaidOpt.get();
            MembershipPlan currentPlan = planRepository.findById(current.getPlanId())
                    .orElseThrow(() -> ApiException.notFound("Current plan not found"));

            ProrationInput input = ProrationInput.builder()
                    .now(now)
                    .endsAt(current.getEndsAt())
                    .totalDays(currentPlan.getDurationDays() != null ? currentPlan.getDurationDays() : 30)
                    .sessionsTotal(current.getSessionsTotal())
                    .sessionsUsed(current.getSessionsUsed())
                    .amountPaidPaise(current.getAmountPaidPaise())
                    .newPlanPricePaise(0L)
                    .totalCashRemainingInChain(cashInChain)
                    .feePercent(feePercent)
                    .isCancel(true)
                    .build();

            ProrationResult pr = prorationService.calculate(input);
            return toChangePreviewResponse(currentPlan.getCode(), null, current.getAmountPaidPaise(), pr);
        }

        // Plan change preview
        if (request.targetPlanCode() == null || request.targetPlanCode().isBlank()) {
            throw ApiException.badRequest("targetPlanCode must not be blank for plan change preview");
        }
        MembershipPlan targetPlan = planRepository.findByCode(request.targetPlanCode().trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Target plan '" + request.targetPlanCode() + "' not found"));

        if (!Boolean.TRUE.equals(targetPlan.getActive()) || Boolean.TRUE.equals(targetPlan.getIsFree())) {
            throw ApiException.badRequest("Target plan must be an active paid plan");
        }

        if (currentPaidOpt.isEmpty()) {
            // New subscription preview (no credit)
            return new ChangePreviewResponse(
                    null,
                    targetPlan.getCode(),
                    0L,
                    targetPlan.getDurationDays() != null ? targetPlan.getDurationDays() : 30,
                    targetPlan.getDurationDays() != null ? targetPlan.getDurationDays() : 30,
                    targetPlan.getSessionQuota(),
                    targetPlan.getSessionQuota(),
                    BigDecimal.ONE.setScale(10),
                    targetPlan.getSessionQuota() != null ? BigDecimal.ONE.setScale(10) : null,
                    BigDecimal.ONE.setScale(10),
                    0L,
                    targetPlan.getPricePaise(),
                    0L,
                    "NONE",
                    List.of("New subscription to plan " + targetPlan.getCode() + " for " + targetPlan.getPricePaise() + " paise"),
                    null
            );
        }

        Membership current = currentPaidOpt.get();
        MembershipPlan currentPlan = planRepository.findById(current.getPlanId())
                .orElseThrow(() -> ApiException.notFound("Current plan not found"));

        ProrationInput input = ProrationInput.builder()
                .now(now)
                .endsAt(current.getEndsAt())
                .totalDays(currentPlan.getDurationDays() != null ? currentPlan.getDurationDays() : 30)
                .sessionsTotal(current.getSessionsTotal())
                .sessionsUsed(current.getSessionsUsed())
                .amountPaidPaise(current.getAmountPaidPaise())
                .newPlanPricePaise(targetPlan.getPricePaise())
                .totalCashRemainingInChain(cashInChain)
                .feePercent(feePercent)
                .isCancel(false)
                .build();

        ProrationResult pr = prorationService.calculate(input);
        return toChangePreviewResponse(currentPlan.getCode(), targetPlan.getCode(), current.getAmountPaidPaise(), pr);
    }

    // -------------------------------------------------------------------------
    // Two-Phase Subscribe, Change & Cancel
    // -------------------------------------------------------------------------

    @Transactional
    public PaymentIntentResponse subscribe(Long memberId, String idempotencyKey, SubscribeRequest request) {
        MembershipPlan targetPlan = planRepository.findByCode(request.planCode().trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Plan '" + request.planCode() + "' not found"));

        if (!Boolean.TRUE.equals(targetPlan.getActive()) || Boolean.TRUE.equals(targetPlan.getIsFree())) {
            throw ApiException.badRequest("Cannot subscribe to an inactive or free plan");
        }

        MembershipPlan freePlan = getFreePlan();
        List<Membership> activeMemberships = membershipRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE);
        boolean hasPaidMembership = activeMemberships.stream()
                .anyMatch(m -> !m.getPlanId().equals(freePlan.getId()));

        if (hasPaidMembership) {
            throw ApiException.conflict("Member already has an active paid membership. Use change plan instead.");
        }

        Instant now = clock.instant();
        Instant expiresAt = now.plus(Duration.ofMinutes(30));

        PaymentIntentResponse intent = paymentService.createPaymentIntent(
                memberId,
                targetPlan.getPricePaise(),
                "INR",
                idempotencyKey,
                expiresAt,
                Map.of("planCode", targetPlan.getCode(), "action", "SUBSCRIBE")
        );

        // Record pending membership change
        PendingMembershipChange pendingChange = PendingMembershipChange.builder()
                .memberId(memberId)
                .paymentId(intent.paymentId())
                .kind(PendingChangeKind.SUBSCRIBE)
                .targetPlanId(targetPlan.getId())
                .creditPaise(0L)
                .amountDuePaise(targetPlan.getPricePaise())
                .snapshotJson("{\"targetPlanCode\":\"" + targetPlan.getCode() + "\"}")
                .status(PendingChangeStatus.PENDING)
                .expiresAt(expiresAt)
                .createdAt(now)
                .version(0)
                .build();

        pendingChangeRepository.save(pendingChange);

        // Sanity break hook
        if (sanityBreakApplyMembershipChangeEarly) {
            applyNewMembership(memberId, targetPlan, targetPlan.getPricePaise());
        }

        return intent;
    }

    @Transactional
    public Object changeMembership(Long memberId, String idempotencyKey, ChangeMembershipRequest request) {
        MembershipPlan targetPlan = planRepository.findByCode(request.targetPlanCode().trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Target plan '" + request.targetPlanCode() + "' not found"));

        if (!Boolean.TRUE.equals(targetPlan.getActive()) || Boolean.TRUE.equals(targetPlan.getIsFree())) {
            throw ApiException.badRequest("Target plan must be an active paid plan");
        }

        // Lock member's current active memberships
        List<Membership> activeMemberships = membershipRepository.findByMemberIdAndStatusForUpdate(memberId, MembershipStatus.ACTIVE);
        MembershipPlan freePlan = getFreePlan();
        Membership current = activeMemberships.stream()
                .filter(m -> !m.getPlanId().equals(freePlan.getId()))
                .findFirst()
                .orElseThrow(() -> ApiException.conflict("No active paid membership to change. Use subscribe instead."));

        MembershipPlan currentPlan = planRepository.findById(current.getPlanId())
                .orElseThrow(() -> ApiException.notFound("Current plan not found"));

        Instant now = clock.instant();
        long cashInChain = paymentService.getTotalRefundableCashInChain(memberId);

        // Recompute preview server-side
        ProrationInput input = ProrationInput.builder()
                .now(now)
                .endsAt(current.getEndsAt())
                .totalDays(currentPlan.getDurationDays() != null ? currentPlan.getDurationDays() : 30)
                .sessionsTotal(current.getSessionsTotal())
                .sessionsUsed(current.getSessionsUsed())
                .amountPaidPaise(current.getAmountPaidPaise())
                .newPlanPricePaise(targetPlan.getPricePaise())
                .totalCashRemainingInChain(cashInChain)
                .feePercent(getCancelFeePercent())
                .isCancel(false)
                .build();

        ProrationResult pr = prorationService.calculate(input);

        // Stale preview check
        if (pr.amountDuePaise() != request.expectedAmountDuePaise()) {
            log.warn("Stale preview detected for member id={}: expected={} vs recomputed={}",
                    memberId, request.expectedAmountDuePaise(), pr.amountDuePaise());
            ChangePreviewResponse newPreview = toChangePreviewResponse(
                    currentPlan.getCode(), targetPlan.getCode(), current.getAmountPaidPaise(), pr
            );
            throw ApiException.conflict("Preview numbers have changed", Map.of("newPreview", newPreview));
        }

        if (pr.amountDuePaise() > 0) {
            // Two-phase payment required
            Instant expiresAt = now.plus(Duration.ofMinutes(30));
            PaymentIntentResponse intent = paymentService.createPaymentIntent(
                    memberId,
                    pr.amountDuePaise(),
                    "INR",
                    idempotencyKey,
                    expiresAt,
                    Map.of("targetPlanCode", targetPlan.getCode(), "action", "CHANGE")
            );

            PendingMembershipChange pendingChange = PendingMembershipChange.builder()
                    .memberId(memberId)
                    .paymentId(intent.paymentId())
                    .kind(PendingChangeKind.CHANGE)
                    .targetPlanId(targetPlan.getId())
                    .creditPaise(pr.creditPaise())
                    .amountDuePaise(pr.amountDuePaise())
                    .snapshotJson("{\"fromPlanCode\":\"" + currentPlan.getCode() + "\",\"targetPlanCode\":\"" + targetPlan.getCode() + "\"}")
                    .status(PendingChangeStatus.PENDING)
                    .expiresAt(expiresAt)
                    .createdAt(now)
                    .version(0)
                    .build();

            pendingChangeRepository.save(pendingChange);
            return intent;

        } else {
            // Apply change immediately (amountDue == 0 or downgrade with refund)
            current.setStatus(MembershipStatus.CANCELLED);
            current.setUpdatedAt(now);
            membershipRepository.save(current);

            Membership newMembership = applyNewMembership(memberId, targetPlan, pr.creditPaise() + pr.amountDuePaise());

            // Record change log
            MembershipChange changeLog = MembershipChange.builder()
                    .membershipId(newMembership.getId())
                    .fromPlanId(currentPlan.getId())
                    .toPlanId(targetPlan.getId())
                    .creditPaise(pr.creditPaise())
                    .amountDuePaise(0L)
                    .refundPaise(pr.refundPaise())
                    .policyName(pr.policy())
                    .createdAt(now)
                    .build();
            membershipChangeRepository.save(changeLog);

            // Emit membership.changed
            outboxService.append(
                    "membership",
                    newMembership.getId(),
                    "membership.changed",
                    Map.of(
                            "membershipId", newMembership.getId(),
                            "memberId", memberId,
                            "fromPlanCode", currentPlan.getCode(),
                            "toPlanCode", targetPlan.getCode(),
                            "creditPaise", pr.creditPaise(),
                            "amountDuePaise", 0L,
                            "refundPaise", pr.refundPaise()
                    )
            );

            if (pr.refundPaise() > 0) {
                paymentService.createAndExecuteRefund(memberId, pr.refundPaise(), idempotencyKey, null);
            }

            return getMemberMembership(memberId);
        }
    }

    @Transactional
    public Map<String, Object> cancelMembership(Long memberId, String idempotencyKey) {
        Instant now = clock.instant();
        MembershipPlan freePlan = getFreePlan();

        List<Membership> activeMemberships = membershipRepository.findByMemberIdAndStatusForUpdate(memberId, MembershipStatus.ACTIVE);
        Membership current = activeMemberships.stream()
                .filter(m -> !m.getPlanId().equals(freePlan.getId()))
                .findFirst()
                .orElseThrow(() -> ApiException.conflict("No active paid membership to cancel"));

        MembershipPlan currentPlan = planRepository.findById(current.getPlanId())
                .orElseThrow(() -> ApiException.notFound("Current plan not found"));

        long cashInChain = paymentService.getTotalRefundableCashInChain(memberId);

        ProrationInput input = ProrationInput.builder()
                .now(now)
                .endsAt(current.getEndsAt())
                .totalDays(currentPlan.getDurationDays() != null ? currentPlan.getDurationDays() : 30)
                .sessionsTotal(current.getSessionsTotal())
                .sessionsUsed(current.getSessionsUsed())
                .amountPaidPaise(current.getAmountPaidPaise())
                .newPlanPricePaise(0L)
                .totalCashRemainingInChain(cashInChain)
                .feePercent(getCancelFeePercent())
                .isCancel(true)
                .build();

        ProrationResult pr = prorationService.calculate(input);

        // Mark cancelled
        current.setStatus(MembershipStatus.CANCELLED);
        current.setUpdatedAt(now);
        membershipRepository.save(current);

        // Execute refund if > 0
        if (pr.refundPaise() > 0) {
            paymentService.createAndExecuteRefund(memberId, pr.refundPaise(), idempotencyKey, null);
        }

        // Emit membership.cancelled
        outboxService.append(
                "membership",
                current.getId(),
                "membership.cancelled",
                Map.of(
                        "membershipId", current.getId(),
                        "memberId", memberId,
                        "planCode", currentPlan.getCode(),
                        "refundPaise", pr.refundPaise()
                )
        );

        return Map.of(
                "status", "CANCELLED",
                "refundPaise", pr.refundPaise(),
                "creditPaise", pr.creditPaise(),
                "explanation", pr.explanation()
        );
    }

    // -------------------------------------------------------------------------
    // Payment Completion Listener Implementation (DIP)
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void onPaymentSucceeded(Long paymentId) {
        log.info("MembershipService.onPaymentSucceeded: Processing payment id={}", paymentId);

        Optional<PendingMembershipChange> pendingOpt = pendingChangeRepository.findByPaymentId(paymentId);
        if (pendingOpt.isEmpty()) {
            log.info("No pending membership change found for payment id={}", paymentId);
            return;
        }

        PendingMembershipChange pending = pendingOpt.get();
        if (pending.getStatus() != PendingChangeStatus.PENDING) {
            log.info("Pending membership change id={} already in status={}", pending.getId(), pending.getStatus());
            return;
        }

        MembershipPlan targetPlan = planRepository.findById(pending.getTargetPlanId())
                .orElseThrow(() -> ApiException.notFound("Target plan id " + pending.getTargetPlanId() + " not found"));

        Instant now = clock.instant();

        if (pending.getKind() == PendingChangeKind.SUBSCRIBE) {
            Membership membership = applyNewMembership(pending.getMemberId(), targetPlan, pending.getAmountDuePaise());
            pending.setStatus(PendingChangeStatus.APPLIED);
            pending.setUpdatedAt(now);
            pendingChangeRepository.save(pending);

            outboxService.append(
                    "membership",
                    membership.getId(),
                    "membership.started",
                    Map.of(
                            "membershipId", membership.getId(),
                            "memberId", pending.getMemberId(),
                            "planCode", targetPlan.getCode(),
                            "amountPaidPaise", membership.getAmountPaidPaise(),
                            "startedAt", membership.getStartedAt().toString(),
                            "endsAt", membership.getEndsAt() != null ? membership.getEndsAt().toString() : ""
                    )
            );
            log.info("Membership started for memberId={}, plan={}", pending.getMemberId(), targetPlan.getCode());

        } else if (pending.getKind() == PendingChangeKind.CHANGE) {
            MembershipPlan freePlan = getFreePlan();
            List<Membership> activeMemberships = membershipRepository.findByMemberIdAndStatusForUpdate(
                    pending.getMemberId(), MembershipStatus.ACTIVE
            );

            Optional<Membership> currentPaidOpt = activeMemberships.stream()
                    .filter(m -> !m.getPlanId().equals(freePlan.getId()))
                    .findFirst();

            Long fromPlanId = targetPlan.getId();
            String fromPlanCode = "UNKNOWN";
            if (currentPaidOpt.isPresent()) {
                Membership current = currentPaidOpt.get();
                current.setStatus(MembershipStatus.CANCELLED);
                current.setUpdatedAt(now);
                membershipRepository.save(current);
                fromPlanId = current.getPlanId();
                fromPlanCode = planRepository.findById(fromPlanId).map(MembershipPlan::getCode).orElse("UNKNOWN");
            }

            Membership newMembership = applyNewMembership(
                    pending.getMemberId(), targetPlan, pending.getAmountDuePaise() + pending.getCreditPaise()
            );

            MembershipChange change = MembershipChange.builder()
                    .membershipId(newMembership.getId())
                    .fromPlanId(fromPlanId)
                    .toPlanId(targetPlan.getId())
                    .creditPaise(pending.getCreditPaise())
                    .amountDuePaise(pending.getAmountDuePaise())
                    .refundPaise(0L)
                    .policyName("MIN_RATIO_V1")
                    .createdAt(now)
                    .build();
            membershipChangeRepository.save(change);

            pending.setStatus(PendingChangeStatus.APPLIED);
            pending.setUpdatedAt(now);
            pendingChangeRepository.save(pending);

            outboxService.append(
                    "membership",
                    newMembership.getId(),
                    "membership.changed",
                    Map.of(
                            "membershipId", newMembership.getId(),
                            "memberId", pending.getMemberId(),
                            "fromPlanCode", fromPlanCode,
                            "toPlanCode", targetPlan.getCode(),
                            "creditPaise", pending.getCreditPaise(),
                            "amountDuePaise", pending.getAmountDuePaise(),
                            "refundPaise", 0L
                    )
            );
            log.info("Membership changed for memberId={}, to plan={}", pending.getMemberId(), targetPlan.getCode());
        }
    }

    @Override
    @Transactional
    public void onPaymentFailed(Long paymentId, String reason) {
        log.info("MembershipService.onPaymentFailed: Payment id={} failed with reason: {}", paymentId, reason);
        pendingChangeRepository.findByPaymentId(paymentId).ifPresent(pending -> {
            if (pending.getStatus() == PendingChangeStatus.PENDING) {
                pending.setStatus(PendingChangeStatus.FAILED);
                pending.setUpdatedAt(clock.instant());
                pendingChangeRepository.save(pending);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Scheduled Job: Expire Memberships & Pending Changes
    // -------------------------------------------------------------------------

    @Scheduled(fixedDelay = 60000)
    public void runPendingChangesReconcile() {
        Instant now = clock.instant();
        List<PendingMembershipChange> expiredPending = pendingChangeRepository.findByStatusAndExpiresAtBefore(
                PendingChangeStatus.PENDING, now
        );

        for (PendingMembershipChange change : expiredPending) {
            Payment payment = paymentRepository.findById(change.getPaymentId()).orElse(null);
            if (payment != null && payment.getStatus() == com.fitflow.payment.model.PaymentStatus.PENDING) {
                change.setStatus(PendingChangeStatus.EXPIRED);
                change.setUpdatedAt(now);
                pendingChangeRepository.save(change);

                payment.setStatus(com.fitflow.payment.model.PaymentStatus.FAILED);
                payment.setFailureReason("expired");
                paymentRepository.save(payment);

                outboxService.append(
                        "payment",
                        payment.getId(),
                        "payment.expired",
                        Map.of(
                                "paymentId", payment.getId(),
                                "memberId", payment.getMemberId(),
                                "amountPaise", payment.getAmountPaise()
                        )
                );
                log.info("Pending membership change id={} and payment id={} expired", change.getId(), payment.getId());
            }
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    public void runHourlyExpireMemberships() {
        expireMemberships();
    }

    @Transactional
    public void expireMemberships() {
        Instant now = clock.instant();
        Instant expiredBefore = now.minusSeconds(300);

        int acquired = 0;
        try {
            acquired = jobLockRepository.acquireLock("expire_memberships", now, instanceId, expiredBefore);
        } catch (Exception e) {
            log.warn("JobLock acquire failed for expire_memberships: {}", e.getMessage());
        }

        if (acquired == 0) {
            log.debug("expire_memberships job lock held by another instance. Skipping.");
            return;
        }

        try {
            List<Membership> expiredMemberships = membershipRepository.findByStatusAndEndsAtBefore(
                    MembershipStatus.ACTIVE, now
            );

            for (Membership m : expiredMemberships) {
                m.setStatus(MembershipStatus.EXPIRED);
                m.setUpdatedAt(now);
                membershipRepository.save(m);

                String planCode = planRepository.findById(m.getPlanId()).map(MembershipPlan::getCode).orElse("UNKNOWN");
                outboxService.append(
                        "membership",
                        m.getId(),
                        "membership.expired",
                        Map.of(
                                "membershipId", m.getId(),
                                "memberId", m.getMemberId(),
                                "planCode", planCode
                        )
                );
                log.info("Membership id={} for memberId={} expired at {}", m.getId(), m.getMemberId(), now);
            }
        } finally {
            try {
                jobLockRepository.releaseLock("expire_memberships", instanceId);
            } catch (Exception ignored) {}
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @Transactional
    public void assignFreeMembershipIfMissing(Long memberId) {
        MembershipPlan freePlan = getFreePlan();
        boolean hasFree = membershipRepository.findFirstByMemberIdAndPlanIdAndStatus(
                memberId, freePlan.getId(), MembershipStatus.ACTIVE
        ).isPresent();

        if (!hasFree) {
            Instant now = clock.instant();
            Membership freeMem = Membership.builder()
                    .memberId(memberId)
                    .planId(freePlan.getId())
                    .status(MembershipStatus.ACTIVE)
                    .startedAt(now)
                    .endsAt(null)
                    .sessionsTotal(null)
                    .sessionsUsed(0)
                    .amountPaidPaise(0L)
                    .version(0)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            membershipRepository.save(freeMem);
            log.info("Assigned default FREE membership to memberId={}", memberId);
        }
    }

    private Membership applyNewMembership(Long memberId, MembershipPlan plan, long amountPaidPaise) {
        Instant now = clock.instant();
        Instant endsAt = plan.getDurationDays() != null ? now.plus(Duration.ofDays(plan.getDurationDays())) : null;

        Membership membership = Membership.builder()
                .memberId(memberId)
                .planId(plan.getId())
                .status(MembershipStatus.ACTIVE)
                .startedAt(now)
                .endsAt(endsAt)
                .sessionsTotal(plan.getSessionQuota())
                .sessionsUsed(0)
                .amountPaidPaise(amountPaidPaise)
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return membershipRepository.saveAndFlush(membership);
    }

    private MembershipPlan getFreePlan() {
        return planRepository.findByCode("FREE")
                .orElseGet(() -> planRepository.findAll().stream()
                        .filter(p -> Boolean.TRUE.equals(p.getIsFree()))
                        .findFirst()
                        .orElseThrow(() -> ApiException.internalError("Default FREE plan not found")));
    }

    @Transactional
    public com.fitflow.membership.entity.FreeSessionGrant grantFreeSession(Long memberId, int sessions, String reason, Long adminId) {
        Instant now = clock.instant();
        Instant ninetyDaysAgo = now.minus(Duration.ofDays(90));
        int alreadyGranted = freeSessionGrantRepository.countSessionsGrantedSince(memberId, ninetyDaysAgo);
        if (alreadyGranted + sessions > 2) {
            throw ApiException.conflict("Member has reached maximum limit of 2 free sessions per 90 days (already granted " + alreadyGranted + ")");
        }

        com.fitflow.membership.entity.FreeSessionGrant grant = com.fitflow.membership.entity.FreeSessionGrant.builder()
                .memberId(memberId)
                .sessions(sessions)
                .reason(reason)
                .grantedBy(adminId)
                .createdAt(now)
                .build();
        grant = freeSessionGrantRepository.save(grant);

        List<Membership> activeList = membershipRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE);
        if (!activeList.isEmpty()) {
            Membership membership = activeList.get(0);
            if (membership.getSessionsTotal() != null) {
                membership.setSessionsTotal(membership.getSessionsTotal() + sessions);
                membership.setUpdatedAt(now);
                membershipRepository.save(membership);
            }
        }
        return grant;
    }

    private BigDecimal getCancelFeePercent() {
        return appSettingRepository.findById("refund.cancel-fee-percent")
                .map(AppSetting::getValue)
                .filter(v -> !v.isBlank())
                .map(v -> {
                    try { return new BigDecimal(v.trim()); } catch (Exception e) { return BigDecimal.ZERO; }
                })
                .orElse(BigDecimal.ZERO);
    }

    private PlanDto toPlanDto(MembershipPlan plan) {
        return new PlanDto(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getPricePaise(),
                plan.getDurationDays(),
                plan.getSessionQuota(),
                Boolean.TRUE.equals(plan.getIsFree()),
                plan.getFreeSessions(),
                Boolean.TRUE.equals(plan.getActive()),
                plan.getSortOrder() != null ? plan.getSortOrder() : 0
        );
    }

    private ChangePreviewResponse toChangePreviewResponse(
            String currentCode, String targetCode, long amountPaid, ProrationResult pr
    ) {
        return new ChangePreviewResponse(
                currentCode,
                targetCode,
                amountPaid,
                pr.daysRemaining(),
                pr.totalDays(),
                pr.sessionsRemaining(),
                pr.sessionsTotal(),
                pr.daysRatio(),
                pr.sessionsRatio(),
                pr.refundableFraction(),
                pr.creditPaise(),
                pr.amountDuePaise(),
                pr.refundPaise(),
                pr.policy(),
                pr.explanation(),
                pr.cappedByCashPaise()
        );
    }
}
