package com.fitflow.booking;

import com.fitflow.booking.model.BookingStatus;
import com.fitflow.booking.model.ConsumedFrom;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.common.error.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stage 4 stub implementation of {@link EntitlementService}.
 * Marked in CONTRACT.md "Stubs".
 * Allows up to N lifetime bookings per member (config fitflow.booking.stub-free-sessions, default 3).
 * Stage 5 replaces this with the real membership entitlement engine.
 */
@Service
@Slf4j
public class DefaultFreeTierEntitlementService implements EntitlementService {

    private final BookingRepository bookingRepository;
    private final int maxFreeSessions;
    private final Set<Long> restoredBookingIds = ConcurrentHashMap.newKeySet();

    public DefaultFreeTierEntitlementService(
            BookingRepository bookingRepository,
            @Value("${fitflow.booking.stub-free-sessions:3}") int maxFreeSessions) {
        this.bookingRepository = bookingRepository;
        this.maxFreeSessions = maxFreeSessions;
    }

    @Override
    public Entitlement reserve(Long memberId, Long sessionId, String idempotencyKey) {
        long activeCount = bookingRepository.countByMemberIdAndStatusIn(
                memberId,
                Set.of(BookingStatus.CONFIRMED, BookingStatus.ATTENDED, BookingStatus.NO_SHOW)
        );

        List<com.fitflow.booking.entity.Booking> cancelledBookings =
                bookingRepository.findByMemberIdAndStatus(memberId, BookingStatus.CANCELLED);
        long burnedCancelledCount = cancelledBookings.stream()
                .filter(b -> !restoredBookingIds.contains(b.getId()))
                .count();

        long usedCount = activeCount + burnedCancelledCount;

        if (usedCount >= maxFreeSessions) {
            log.warn("Member id={} has exhausted free tier quota ({}/{})", memberId, usedCount, maxFreeSessions);
            throw ApiException.conflict("UPGRADE_REQUIRED", "Member has used all " + maxFreeSessions + " free sessions. Upgrade required.");
        }

        log.debug("Member id={} reserved free tier entitlement for sessionId={} ({}/{})",
                memberId, sessionId, usedCount + 1, maxFreeSessions);
        return new Entitlement(ConsumedFrom.FREE_SESSION, null);
    }

    @Override
    public void restore(Long bookingId, String reason) {
        if (bookingId != null) {
            restoredBookingIds.add(bookingId);
        }
        log.info("Entitlement restored for bookingId={}. Reason: {}", bookingId, reason);
    }
}
