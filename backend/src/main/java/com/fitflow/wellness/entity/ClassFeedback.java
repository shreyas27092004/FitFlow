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

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "class_feedback")
public class ClassFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "rating", nullable = false)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.TINYINT)
    private Integer rating;

    @Column(name = "felt_pain", nullable = false)
    @Builder.Default
    private Boolean feltPain = false;

    @Column(name = "pain_area_code", length = 30)
    private String painAreaCode;

    @Column(name = "comment_enc")
    private byte[] commentEnc;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
