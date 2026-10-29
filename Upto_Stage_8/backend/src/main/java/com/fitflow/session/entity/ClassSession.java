package com.fitflow.session.entity;

import com.fitflow.common.entity.BaseVersionedAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "class_sessions")
public class ClassSession extends BaseVersionedAuditedEntity {

    public enum Status {
        SCHEDULED, CANCELLED, COMPLETED
    }

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "trainer_id", nullable = false)
    private Long trainerId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "booked_count", nullable = false)
    @Builder.Default
    private Integer bookedCount = 0;

    @Column(name = "room", nullable = false, length = 60)
    private String room;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private Status status = Status.SCHEDULED;

    @Column(name = "series_id", columnDefinition = "CHAR(36)", length = 36)
    private String seriesId;

    @Column(name = "trainer_rate_snapshot_paise", nullable = false)
    private Long trainerRateSnapshotPaise;
}
