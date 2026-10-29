package com.fitflow.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name = "refund_allocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundAllocation {

    @EmbeddedId
    private RefundAllocationId id;

    @Column(name = "amount_paise", nullable = false)
    private Long amountPaise;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class RefundAllocationId implements Serializable {
        @Column(name = "refund_id", nullable = false)
        private Long refundId;

        @Column(name = "payment_id", nullable = false)
        private Long paymentId;
    }
}
