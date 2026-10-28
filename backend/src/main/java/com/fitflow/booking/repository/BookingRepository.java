package com.fitflow.booking.repository;

import com.fitflow.booking.entity.Booking;
import com.fitflow.booking.model.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findBySessionIdAndMemberIdAndActiveFlag(Long sessionId, Long memberId, Byte activeFlag);

    Optional<Booking> findByMemberIdAndIdempotencyKey(Long memberId, String idempotencyKey);

    @Query("SELECT b FROM Booking b JOIN com.fitflow.session.entity.ClassSession s ON b.sessionId = s.id " +
            "WHERE b.memberId = :memberId AND b.status = com.fitflow.booking.model.BookingStatus.CONFIRMED " +
            "AND s.startsAt < :endsAt AND s.endsAt > :startsAt")
    List<Booking> findOverlappingConfirmedBookings(
            @Param("memberId") Long memberId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt);

    long countByMemberIdAndStatusIn(Long memberId, Collection<BookingStatus> statuses);

    List<Booking> findBySessionIdAndStatus(Long sessionId, BookingStatus status);

    List<Booking> findByMemberIdAndStatus(Long memberId, BookingStatus status);

    List<Booking> findBySessionId(Long sessionId);

    List<Booking> findByMemberIdAndSessionIdIn(Long memberId, Collection<Long> sessionIds);

    @Query("SELECT b FROM Booking b WHERE b.memberId = :memberId " +
            "AND (:status IS NULL OR b.status = :status) " +
            "AND (:from IS NULL OR b.bookedAt >= :from) " +
            "AND (:to IS NULL OR b.bookedAt <= :to) " +
            "ORDER BY b.bookedAt DESC")
    Page<Booking> findMemberBookings(
            @Param("memberId") Long memberId,
            @Param("status") BookingStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
