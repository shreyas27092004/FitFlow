package com.fitflow.agent.entity;

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
import java.time.LocalDate;

@Entity
@Table(name = "at_risk_flags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AtRiskFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "flagged_on", nullable = false)
    private LocalDate flaggedOn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
