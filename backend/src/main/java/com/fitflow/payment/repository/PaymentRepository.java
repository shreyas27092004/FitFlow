package com.fitflow.payment.repository;

import com.fitflow.payment.entity.Payment;
import com.fitflow.payment.model.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    Optional<Payment> findByMemberIdAndIdempotencyKey(Long memberId, String idempotencyKey);

    Page<Payment> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

    List<Payment> findByMembershipIdAndStatusOrderByCreatedAtDesc(Long membershipId, PaymentStatus status);

    List<Payment> findByMemberIdAndStatusOrderByCreatedAtDesc(Long memberId, PaymentStatus status);

    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant cutoff);
}
