package com.fitflow.payment.repository;

import com.fitflow.payment.entity.Refund;
import com.fitflow.payment.model.RefundStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByPaymentIdAndIdempotencyKey(Long paymentId, String idempotencyKey);

    List<Refund> findByStatus(RefundStatus status);

    Page<Refund> findByStatus(RefundStatus status, Pageable pageable);

    List<Refund> findByPaymentId(Long paymentId);

    List<Refund> findByPaymentIdIn(List<Long> paymentIds);

    @Query("SELECT COALESCE(SUM(r.amountPaise), 0) FROM Refund r WHERE r.paymentId = :paymentId AND r.status IN ('PENDING_APPROVAL', 'APPROVED', 'PAID')")
    long sumActiveRefundsForPayment(@Param("paymentId") Long paymentId);
}
