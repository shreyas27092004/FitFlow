package com.fitflow.membership.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "membership_notices")
public class MembershipNotice {

    @EmbeddedId
    private MembershipNoticeId id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
