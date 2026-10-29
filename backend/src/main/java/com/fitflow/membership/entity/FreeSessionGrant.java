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
@Table(name = "free_session_grants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FreeSessionGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "sessions", nullable = false)
    private Integer sessions;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "granted_by", nullable = false)
    private Long grantedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
