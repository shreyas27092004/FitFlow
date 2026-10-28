package com.fitflow.fitnessclass.entity;

import com.fitflow.common.entity.BaseVersionedAuditedEntity;
import com.fitflow.fitnessclass.model.ClassImpactLevel;
import com.fitflow.fitnessclass.model.ClassIntensity;
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

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "fitness_classes")
public class FitnessClass extends BaseVersionedAuditedEntity {

    @Column(name = "name", nullable = false, unique = true, length = 120)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", nullable = false, length = 40)
    private String category;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "intensity", nullable = false)
    private ClassIntensity intensity;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_level", nullable = false)
    private ClassImpactLevel impactLevel;

    @Column(name = "met_value", nullable = false, precision = 4, scale = 1)
    private BigDecimal metValue;

    @Column(name = "contraindication_tags", columnDefinition = "JSON")
    private String contraindicationTags;

    @Column(name = "min_age", nullable = false)
    @Builder.Default
    private Integer minAge = 14;

    @Column(name = "default_capacity", nullable = false)
    @Builder.Default
    private Integer defaultCapacity = 20;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "metadata_reviewed", nullable = false)
    @Builder.Default
    private Boolean metadataReviewed = false;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;
}
