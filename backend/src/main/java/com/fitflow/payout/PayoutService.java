package com.fitflow.payout;

import com.fitflow.common.error.ApiException;
import com.fitflow.notification.OutboxService;
import com.fitflow.payout.dto.PayoutBatchResponse;
import com.fitflow.payout.dto.TrainerEarningItemDto;
import com.fitflow.payout.dto.TrainerEarningsResponse;
import com.fitflow.payout.entity.TrainerPayout;
import com.fitflow.payout.entity.TrainerSessionEarnings;
import com.fitflow.payout.model.EarningsStatus;
import com.fitflow.payout.model.PayoutStatus;
import com.fitflow.payout.repository.TrainerPayoutRepository;
import com.fitflow.payout.repository.TrainerSessionEarningsRepository;
import com.fitflow.trainer.TrainerEarningsProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class PayoutService implements TrainerEarningsProvider {

    private final TrainerSessionEarningsRepository earningsRepository;
    private final TrainerPayoutRepository payoutRepository;
    private final OutboxService outboxService;
    private final Clock clock;

    public static volatile boolean sanityBreakDisableTwoPersonRule = false;

    @Override
    @Transactional(readOnly = true)
    public long getTotalEarningsPaise(Long trainerId) {
        return earningsRepository.sumTotalEarningsForTrainer(trainerId);
    }

    @Transactional(readOnly = true)
    public TrainerEarningsResponse getTrainerEarnings(Long trainerId, Instant from, Instant to) {
        Instant effectiveFrom = from != null ? from : Instant.EPOCH;
        Instant effectiveTo = to != null ? to : clock.instant().plus(Duration.ofDays(3650));

        List<TrainerSessionEarnings> list = earningsRepository.findByTrainerIdAndCreatedAtBetween(
                trainerId, effectiveFrom, effectiveTo
        );

        long totalPaise = 0L;
        long pendingPaise = 0L;
        long paidPaise = 0L;

        for (TrainerSessionEarnings e : list) {
            totalPaise += e.getAmountPaise();
            if (e.getStatus() == EarningsStatus.PENDING || e.getStatus() == EarningsStatus.IN_BATCH) {
                pendingPaise += e.getAmountPaise();
            } else if (e.getStatus() == EarningsStatus.PAID) {
                paidPaise += e.getAmountPaise();
            }
        }

        List<TrainerEarningItemDto> items = list.stream()
                .map(e -> new TrainerEarningItemDto(
                        e.getId(),
                        e.getSessionId(),
                        e.getTrainerId(),
                        e.getAmountPaise(),
                        e.getStatus(),
                        e.getPayoutId(),
                        e.getCreatedAt()
                ))
                .toList();

        return new TrainerEarningsResponse(totalPaise, pendingPaise, paidPaise, items);
    }

    @Transactional
    public PayoutBatchResponse createDraftPayout(LocalDate periodStart, LocalDate periodEnd, Long adminId) {
        if (periodStart.isAfter(periodEnd)) {
            throw ApiException.badRequest("periodStart cannot be after periodEnd");
        }

        Instant from = periodStart.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = periodEnd.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        // Gather all PENDING earnings in the period
        List<TrainerSessionEarnings> pendingEarnings = earningsRepository.findByStatusAndCreatedAtBetween(
                EarningsStatus.PENDING, from, to
        );

        if (pendingEarnings.isEmpty()) {
            throw ApiException.conflict("No pending session earnings found in period " + periodStart + " to " + periodEnd);
        }

        long totalPaise = pendingEarnings.stream()
                .mapToLong(TrainerSessionEarnings::getAmountPaise)
                .sum();

        // In-transaction verification that sum of earnings exactly matches payout total
        long verifySum = 0L;
        for (TrainerSessionEarnings e : pendingEarnings) {
            verifySum += e.getAmountPaise();
        }
        if (verifySum != totalPaise) {
            throw ApiException.internalError("Payout total verification failed: sum=" + verifySum + " != total=" + totalPaise);
        }

        Instant now = clock.instant();
        TrainerPayout payout = TrainerPayout.builder()
                .status(PayoutStatus.DRAFT)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .totalPaise(totalPaise)
                .createdBy(adminId)
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .build();

        payout = payoutRepository.saveAndFlush(payout);

        // Transition earnings from PENDING to IN_BATCH
        for (TrainerSessionEarnings earning : pendingEarnings) {
            earning.setStatus(EarningsStatus.IN_BATCH);
            earning.setPayoutId(payout.getId());
            earningsRepository.save(earning);
        }

        log.info("Created draft payout batch id={} with {} sessions, total {} paise by admin id={}",
                payout.getId(), pendingEarnings.size(), totalPaise, adminId);

        return toPayoutBatchResponse(payout, pendingEarnings.size());
    }

    @Transactional
    public PayoutBatchResponse approvePayout(Long payoutId, Long adminId) {
        TrainerPayout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> ApiException.notFound("Payout batch id " + payoutId + " not found"));

        if (payout.getStatus() != PayoutStatus.DRAFT) {
            throw ApiException.conflict("Payout batch id " + payoutId + " is not in DRAFT status (status=" + payout.getStatus() + ")");
        }

        // Enforce two-person approval rule
        if (!sanityBreakDisableTwoPersonRule && payout.getCreatedBy().equals(adminId)) {
            log.warn("Two-person rule violation: admin id={} attempted to approve own payout batch id={}", adminId, payoutId);
            throw ApiException.conflict("Two-person rule violation: Payout cannot be approved by its creator");
        }

        Instant now = clock.instant();
        payout.setStatus(PayoutStatus.APPROVED);
        payout.setApprovedBy(adminId);
        payout.setUpdatedAt(now);
        payoutRepository.save(payout);

        outboxService.append(
                "payout",
                payout.getId(),
                "payout.approved",
                Map.of(
                        "payoutId", payout.getId(),
                        "approvedBy", adminId,
                        "totalPaise", payout.getTotalPaise(),
                        "periodStart", payout.getPeriodStart().toString(),
                        "periodEnd", payout.getPeriodEnd().toString()
                )
        );

        log.info("Payout batch id={} approved by admin id={}", payoutId, adminId);
        int sessionCount = earningsRepository.findByPayoutId(payoutId).size();
        return toPayoutBatchResponse(payout, sessionCount);
    }

    @Transactional
    public PayoutBatchResponse markPayoutPaid(Long payoutId, Long adminId) {
        TrainerPayout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> ApiException.notFound("Payout batch id " + payoutId + " not found"));

        if (payout.getStatus() != PayoutStatus.APPROVED) {
            throw ApiException.conflict("Payout batch id " + payoutId + " must be APPROVED before being marked PAID (current=" + payout.getStatus() + ")");
        }

        Instant now = clock.instant();
        payout.setStatus(PayoutStatus.PAID);
        payout.setUpdatedAt(now);
        payoutRepository.save(payout);

        List<TrainerSessionEarnings> earnings = earningsRepository.findByPayoutId(payoutId);
        for (TrainerSessionEarnings e : earnings) {
            e.setStatus(EarningsStatus.PAID);
            earningsRepository.save(e);
        }

        outboxService.append(
                "payout",
                payout.getId(),
                "payout.paid",
                Map.of(
                        "payoutId", payout.getId(),
                        "markedPaidBy", adminId,
                        "totalPaise", payout.getTotalPaise(),
                        "sessionCount", earnings.size()
                )
        );

        log.info("Payout batch id={} marked PAID with {} earnings by admin id={}", payoutId, earnings.size(), adminId);
        return toPayoutBatchResponse(payout, earnings.size());
    }

    @Transactional(readOnly = true)
    public String exportPayoutCsv(Long payoutId) {
        TrainerPayout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> ApiException.notFound("Payout batch id " + payoutId + " not found"));

        List<TrainerSessionEarnings> earnings = earningsRepository.findByPayoutId(payoutId);

        StringBuilder sb = new StringBuilder();
        sb.append("session_id,trainer_id,amount_paise,status,created_at\n");
        for (TrainerSessionEarnings e : earnings) {
            sb.append(e.getSessionId()).append(",")
                    .append(e.getTrainerId()).append(",")
                    .append(e.getAmountPaise()).append(",")
                    .append(e.getStatus().name()).append(",")
                    .append(e.getCreatedAt().toString()).append("\n");
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public PayoutBatchResponse getPayout(Long payoutId) {
        TrainerPayout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> ApiException.notFound("Payout batch id " + payoutId + " not found"));
        int sessionCount = earningsRepository.findByPayoutId(payoutId).size();
        return toPayoutBatchResponse(payout, sessionCount);
    }

    private PayoutBatchResponse toPayoutBatchResponse(TrainerPayout p, int sessionCount) {
        return new PayoutBatchResponse(
                p.getId(),
                p.getStatus(),
                p.getPeriodStart(),
                p.getPeriodEnd(),
                p.getTotalPaise(),
                p.getCreatedBy(),
                p.getApprovedBy(),
                p.getVersion(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                sessionCount
        );
    }
}
