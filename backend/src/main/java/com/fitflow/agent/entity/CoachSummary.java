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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "coach_summaries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoachSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", nullable = false, columnDefinition = "jsonb")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "numbers_json", columnDefinition = "jsonb")
    private String numbersJson;

    @Column(name = "summary_text")
    private String summaryText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
