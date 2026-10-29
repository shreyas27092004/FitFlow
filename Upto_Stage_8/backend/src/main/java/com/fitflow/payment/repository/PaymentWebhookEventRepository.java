package com.fitflow.payment.repository;

import com.fitflow.payment.entity.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, String> {
}
