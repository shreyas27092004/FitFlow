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

    @Query("SELECT m FROM Membership m " +
            "JOIN com.fitflow.membership.entity.MembershipPlan p ON m.planId = p.id " +
            "WHERE m.status = com.fitflow.membership.model.MembershipStatus.ACTIVE " +
            "AND p.code != 'FREE' " +
            "AND m.endsAt >= :windowStart AND m.endsAt <= :windowEnd " +
            "AND NOT EXISTS (SELECT n FROM com.fitflow.membership.entity.MembershipNotice n " +
            "                WHERE n.id.membershipId = m.id AND n.id.kind = :kind)")
    List<Membership> findActiveExpiringMembershipsNeedingNotice(
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd,
            @Param("kind") String kind);
}
