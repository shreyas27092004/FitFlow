package com.fitflow.fitnessclass.dto;

import com.fitflow.fitnessclass.model.ClassCategory;
import com.fitflow.fitnessclass.model.ClassImpactLevel;
import com.fitflow.fitnessclass.model.ClassIntensity;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record UpdateClassRequest(
        @NotBlank(message = "Class name is required")
        @Size(max = 120, message = "Class name must not exceed 120 characters")
        String name,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        @NotNull(message = "Category is required")
        ClassCategory category,

        @NotNull(message = "Duration in minutes is required")
        @Min(value = 15, message = "Duration must be at least 15 minutes")
        @Max(value = 180, message = "Duration must not exceed 180 minutes")
        Integer durationMinutes,

        @NotNull(message = "Intensity is required")
        ClassIntensity intensity,

        @NotNull(message = "Impact level is required")
        ClassImpactLevel impactLevel,

        @NotNull(message = "MET value is required")
        @DecimalMin(value = "1.0", message = "MET value must be between 1.0 and 20.0")
        @DecimalMax(value = "20.0", message = "MET value must be between 1.0 and 20.0")
        BigDecimal metValue,

        List<String> contraindicationTags,

        @Min(value = 14, message = "Minimum age must be between 14 and 99")
        @Max(value = 99, message = "Minimum age must be between 14 and 99")
        Integer minAge,

        @Min(value = 1, message = "Default capacity must be between 1 and 200")
        @Max(value = 200, message = "Default capacity must be between 1 and 200")
        Integer defaultCapacity,

        @NotNull(message = "Version is required for optimistic locking")
        Integer version
) {
}
