package com.fitflow.fitnessclass.dto;

import com.fitflow.fitnessclass.model.ClassCategory;
import com.fitflow.fitnessclass.model.ClassImpactLevel;
import com.fitflow.fitnessclass.model.ClassIntensity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ClassDetailResponse(
        Long id,
        String name,
        String description,
        ClassCategory category,
        Integer durationMinutes,
        ClassIntensity intensity,
        ClassImpactLevel impactLevel,
        BigDecimal metValue,
        List<String> contraindicationTags,
        Integer minAge,
        Integer defaultCapacity,
        Boolean active,
        Boolean metadataReviewed,
        Long createdBy,
        Integer version,
        Instant createdAt,
        Instant updatedAt
) {
}
