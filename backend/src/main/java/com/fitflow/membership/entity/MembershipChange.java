package com.fitflow.membership.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "membership_changes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "membership_id", nullable = false)
    private Long membershipId;

    @Column(name = "from_plan_id", nullable = false)
    private Long fromPlanId;

    @Column(name = "to_plan_id", nullable = false)
    private Long toPlanId;

    @Column(name = "credit_paise", nullable = false)
    private Long creditPaise;

    @Column(name = "amount_due_paise", nullable = false)
    private Long amountDuePaise;

    @Column(name = "refund_paise", nullable = false)
    private Long refundPaise;

    @Column(name = "policy_name", nullable = false, length = 40)
    private String policyName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
