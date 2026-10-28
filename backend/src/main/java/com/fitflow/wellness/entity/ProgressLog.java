package com.fitflow.wellness.entity;

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

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "progress_logs")
public class ProgressLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "kind", nullable = false, length = 30)
    private String kind;

    @Column(name = "value_enc")
    private byte[] valueEnc;

    @Column(name = "note_enc")
    private byte[] noteEnc;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
