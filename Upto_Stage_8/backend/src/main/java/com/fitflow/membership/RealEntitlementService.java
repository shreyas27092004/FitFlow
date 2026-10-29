package com.fitflow.membership;

import com.fitflow.booking.Entitlement;
import com.fitflow.booking.EntitlementService;
import com.fitflow.booking.entity.Booking;
import com.fitflow.booking.model.BookingStatus;
import com.fitflow.booking.model.ConsumedFrom;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.common.error.ApiException;
import com.fitflow.membership.dto.PlanDto;
import com.fitflow.membership.entity.Membership;
import com.fitflow.membership.entity.MembershipPlan;
import com.fitflow.membership.model.MembershipStatus;
import com.fitflow.membership.repository.MembershipPlanRepository;
import com.fitflow.membership.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class RealEntitlementService implements EntitlementService {

    private final MembershipRepository membershipRepository;
    private final MembershipPlanRepository planRepository;
    private final BookingRepository bookingRepository;
    private final Clock clock;

    @Override
    @Transactional(noRollbackFor = ApiException.class)
    public Entitlement reserve(Long memberId, Long sessionId, String idempotencyKey) {
        Instant now = clock.instant();

        // Lock active memberships to prevent race conditions during concurrent bookings
        List<Membership> activeMemberships = membershipRepository.findByMemberIdAndStatusForUpdate(
                memberId, MembershipStatus.ACTIVE
        );

        MembershipPlan freePlan = getFreePlan();

        // 1. Check active PAID membership with quota remaining and not expired
        Optional<Membership> paidMemOpt = activeMemberships.stream()
                .filter(m -> !m.getPlanId().equals(freePlan.getId()))
                .filter(m -> m.getEndsAt() == null || m.getEndsAt().isAfter(now))
                .filter(m -> m.getSessionsTotal() == null || m.getSessionsUsed() < m.getSessionsTotal())
                .findFirst();

        if (paidMemOpt.isPresent()) {
            Membership paidMem = paidMemOpt.get();
            paidMem.setSessionsUsed(paidMem.getSessionsUsed() + 1);
            paidMem.setUpdatedAt(now);
            membershipRepository.save(paidMem);

            log.info("Member id={} consumed session from PAID membership id={} ({}/{} used)",
                    memberId, paidMem.getId(), paidMem.getSessionsUsed(),
                    paidMem.getSessionsTotal() != null ? paidMem.getSessionsTotal() : "unlimited");
            return new Entitlement(ConsumedFrom.PLAN_SESSION, paidMem.getId());
        }

        // 2. Check FREE membership with free sessions remaining
        Optional<Membership> freeMemOpt = activeMemberships.stream()
                .filter(m -> m.getPlanId().equals(freePlan.getId()))
                .findFirst();

        if (freeMemOpt.isEmpty()) {
            long existingUsed = bookingRepository.countByMemberIdAndStatusIn(
                    memberId,
                    Set.of(BookingStatus.CONFIRMED, BookingStatus.ATTENDED, BookingStatus.NO_SHOW)
            );
            Membership createdFree = Membership.builder()
                    .memberId(memberId)
                    .planId(freePlan.getId())
                    .status(MembershipStatus.ACTIVE)
                    .startedAt(now)
                    .endsAt(null)
                    .sessionsTotal(null)
                    .sessionsUsed((int) existingUsed)
                    .amountPaidPaise(0L)
                    .version(0)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            freeMemOpt = Optional.of(membershipRepository.saveAndFlush(createdFree));
        }

        int freeQuota = freePlan.getFreeSessions() != null ? freePlan.getFreeSessions() : 3;

        if (freeMemOpt.isPresent()) {
            Membership freeMem = freeMemOpt.get();
            if (freeMem.getSessionsUsed() < freeQuota) {
                freeMem.setSessionsUsed(freeMem.getSessionsUsed() + 1);
                freeMem.setUpdatedAt(now);
                membershipRepository.save(freeMem);

                log.info("Member id={} consumed session from FREE membership id={} ({}/{} used)",
                        memberId, freeMem.getId(), freeMem.getSessionsUsed(), freeQuota);
                return new Entitlement(ConsumedFrom.FREE_SESSION, freeMem.getId());
            }
        }

        // 3. Otherwise: UPGRADE_REQUIRED with available plans
        log.warn("Member id={} has exhausted all session entitlements. Upgrade required.", memberId);
        List<PlanDto> plans = planRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(p -> new PlanDto(
                        p.getId(),
                        p.getCode(),
                        p.getName(),
                        p.getPricePaise(),
                        p.getDurationDays(),
                        p.getSessionQuota(),
                        Boolean.TRUE.equals(p.getIsFree()),
                        p.getFreeSessions(),
                        Boolean.TRUE.equals(p.getActive()),
                        p.getSortOrder() != null ? p.getSortOrder() : 0
                ))
                .toList();

        throw ApiException.upgradeRequired("Member has used all session entitlements. Upgrade required to continue booking.", plans);
    }

    @Override
    @Transactional
    public void restore(Long bookingId, String reason) {
        if (bookingId == null) {
            log.warn("Cannot restore entitlement: bookingId is null (reason: {})", reason);
            return;
        }

        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) {
            log.warn("Cannot restore entitlement: Booking id={} not found", bookingId);
            return;
        }

        // Must restore quota exactly once
        if (booking.getRestoredAt() != null) {
            log.info("Entitlement for booking id={} was already restored at {}. Skipping second restore.",
                    bookingId, booking.getRestoredAt());
            return;
        }

        Instant now = clock.instant();

        if (booking.getMembershipId() != null) {
            restoreEntitlement(booking.getMembershipId(), reason);
        }

        booking.setRestoredAt(now);
        bookingRepository.save(booking);
        log.info("Booking id={} marked restored at {} (reason: {})", bookingId, now, reason);
    }

    @Override
    @Transactional
    public void restoreEntitlement(Long membershipId, String reason) {
        if (membershipId != null) {
            membershipRepository.findById(membershipId).ifPresent(mem -> {
                if (mem.getSessionsUsed() > 0) {
                    mem.setSessionsUsed(mem.getSessionsUsed() - 1);
                    mem.setUpdatedAt(clock.instant());
                    membershipRepository.save(mem);
                    log.info("Restored 1 session on membership id={} (reason: {})", mem.getId(), reason);
                }
            });
        }
    }

    private MembershipPlan getFreePlan() {
        return planRepository.findByCode("FREE")
                .orElseGet(() -> planRepository.findAll().stream()
                        .filter(p -> Boolean.TRUE.equals(p.getIsFree()))
                        .findFirst()
                        .orElseThrow(() -> ApiException.internalError("Default FREE plan not found")));
    }
}
