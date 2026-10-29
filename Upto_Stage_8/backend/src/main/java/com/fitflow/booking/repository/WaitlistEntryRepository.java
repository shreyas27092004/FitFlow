package com.fitflow.booking.repository;

import com.fitflow.booking.entity.WaitlistEntry;
import com.fitflow.booking.model.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    Optional<WaitlistEntry> findBySessionIdAndMemberId(Long sessionId, Long memberId);

    Optional<WaitlistEntry> findBySessionIdAndMemberIdAndStatus(Long sessionId, Long memberId, WaitlistStatus status);

    List<WaitlistEntry> findBySessionIdAndStatusOrderByCreatedAtAscIdAsc(Long sessionId, WaitlistStatus status);

    long countBySessionIdAndStatusAndCreatedAtLessThanEqual(Long sessionId, WaitlistStatus status, Instant createdAt);
}
