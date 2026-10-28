package com.fitflow.payment.repository;

import com.fitflow.payment.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByPaymentId(Long paymentId);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
}
