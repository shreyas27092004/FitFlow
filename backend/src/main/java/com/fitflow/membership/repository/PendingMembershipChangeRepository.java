package com.fitflow.membership.repository;

import com.fitflow.membership.entity.PendingMembershipChange;
import com.fitflow.membership.model.PendingChangeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PendingMembershipChangeRepository extends JpaRepository<PendingMembershipChange, Long> {

    Optional<PendingMembershipChange> findByPaymentId(Long paymentId);

    List<PendingMembershipChange> findByMemberIdAndStatus(Long memberId, PendingChangeStatus status);

    List<PendingMembershipChange> findByStatusAndExpiresAtBefore(PendingChangeStatus status, Instant cutoff);
}
