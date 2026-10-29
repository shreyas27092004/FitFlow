package com.fitflow.wellness.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
@Table(name = "member_health_profiles")
public class MemberHealthProfile {

    @Id
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "data_encrypted", nullable = false)
    private byte[] dataEncrypted;

    @Column(name = "schema_version", nullable = false)
    @Builder.Default
    private Integer schemaVersion = 1;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
