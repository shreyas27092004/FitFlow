package com.fitflow.membership.entity;

import com.fitflow.membership.model.PendingChangeKind;
import com.fitflow.membership.model.PendingChangeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "pending_membership_changes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingMembershipChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "payment_id", nullable = false, unique = true)
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, columnDefinition = "enum('SUBSCRIBE', 'CHANGE')")
    private PendingChangeKind kind;

    @Column(name = "target_plan_id", nullable = false)
    private Long targetPlanId;

    @Column(name = "credit_paise", nullable = false)
    private Long creditPaise;

    @Column(name = "amount_due_paise", nullable = false)
    private Long amountDuePaise;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "json")
    private String snapshotJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "enum('PENDING', 'APPLIED', 'EXPIRED', 'FAILED')")
    private PendingChangeStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}
