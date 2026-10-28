package com.fitflow.membership.repository;

import com.fitflow.membership.entity.Membership;
import com.fitflow.membership.model.MembershipStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, Long> {

    List<Membership> findByMemberId(Long memberId);

    List<Membership> findByMemberIdAndStatus(Long memberId, MembershipStatus status);

    Optional<Membership> findFirstByMemberIdAndPlanIdAndStatus(Long memberId, Long planId, MembershipStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Membership m WHERE m.memberId = :memberId AND m.status = :status")
    List<Membership> findByMemberIdAndStatusForUpdate(
            @Param("memberId") Long memberId,
            @Param("status") MembershipStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Membership m WHERE m.id = :id")
    Optional<Membership> findByIdForUpdate(@Param("id") Long id);

    List<Membership> findByStatusAndEndsAtBefore(MembershipStatus status, Instant cutoff);

    boolean existsByMemberIdAndStatus(Long memberId, MembershipStatus status);

    long countByPlanIdAndStatus(Long planId, MembershipStatus status);
}
