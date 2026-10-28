package com.fitflow.trainer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "trainer_profiles")
@EntityListeners(AuditingEntityListener.class)
public class TrainerProfile {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "specialties", columnDefinition = "JSON")
    private String specialties;

    @Column(name = "certifications", columnDefinition = "JSON")
    private String certifications;

    @Column(name = "rate_per_session_paise", nullable = false)
    @Builder.Default
    private Long ratePerSessionPaise = 0L;

    @Column(name = "availability", columnDefinition = "JSON")
    private String availability;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
