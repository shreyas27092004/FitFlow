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
@Table(name = "weekly_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeeklyPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "plan", nullable = false, columnDefinition = "jsonb")
    private String plan;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "results_json", columnDefinition = "jsonb")
    private String resultsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
