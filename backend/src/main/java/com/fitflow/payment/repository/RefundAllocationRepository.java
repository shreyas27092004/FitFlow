package com.fitflow.payment.repository;

import com.fitflow.payment.entity.RefundAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefundAllocationRepository extends JpaRepository<RefundAllocation, RefundAllocation.RefundAllocationId> {

    List<RefundAllocation> findByIdRefundId(Long refundId);

    List<RefundAllocation> findByIdPaymentId(Long paymentId);
}
