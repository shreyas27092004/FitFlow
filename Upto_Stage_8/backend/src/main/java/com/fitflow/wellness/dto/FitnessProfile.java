package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitflow.wellness.model.DayAvailability;
import com.fitflow.wellness.model.DietPreference;
import com.fitflow.wellness.model.ExperienceLevel;
import com.fitflow.wellness.model.Goal;
import com.fitflow.wellness.model.HealthCondition;
import com.fitflow.wellness.model.Sex;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record FitnessProfile(
        @NotNull(message = "experienceLevel is required")
        @JsonProperty("experienceLevel")
        ExperienceLevel experienceLevel,

        @Size(max = 300, message = "pastExperienceNote must not exceed 300 characters")
        @JsonProperty("pastExperienceNote")
        String pastExperienceNote,

        @NotNull(message = "goals are required")
        @Size(min = 1, max = 3, message = "Must select between 1 and 3 goals")
        @JsonProperty("goals")
        List<Goal> goals,

        @NotNull(message = "sessionsPerWeekPlanned is required")
        @Min(value = 0, message = "sessionsPerWeekPlanned must be at least 0")
        @Max(value = 14, message = "sessionsPerWeekPlanned must be at most 14")
        @JsonProperty("sessionsPerWeekPlanned")
        Integer sessionsPerWeekPlanned,

        @JsonProperty("availability")
        @Valid
        List<DayAvailability> availability,

        @JsonProperty("injuries")
        @Valid
        List<InjuryDto> injuries,

        @JsonProperty("conditions")
        Set<HealthCondition> conditions,

        @JsonProperty("parq")
        Map<String, Boolean> parq,

        @JsonProperty("medicalClearanceAttested")
        Boolean medicalClearanceAttested,

        @NotNull(message = "heightCm is required")
        @DecimalMin(value = "120.0", message = "heightCm must be at least 120")
        @DecimalMax(value = "230.0", message = "heightCm must be at most 230")
        @JsonProperty("heightCm")
        BigDecimal heightCm,

        @NotNull(message = "weightKg is required")
        @DecimalMin(value = "30.0", message = "weightKg must be at least 30")
        @DecimalMax(value = "250.0", message = "weightKg must be at most 250")
        @JsonProperty("weightKg")
        BigDecimal weightKg,

        @NotNull(message = "sex is required")
        @JsonProperty("sex")
        Sex sex,

        @JsonProperty("dietPreference")
        DietPreference dietPreference,

        @JsonProperty("version")
        Integer version
) {
    public FitnessProfile {
        if (availability == null) {
            availability = Collections.emptyList();
        }
        if (injuries == null) {
            injuries = Collections.emptyList();
        }
        if (conditions == null) {
            conditions = Collections.emptySet();
        }
        if (parq == null) {
            parq = Collections.emptyMap();
        }
        if (medicalClearanceAttested == null) {
            medicalClearanceAttested = false;
        }
    }

    public boolean isMedicalClearanceAttested() {
        return Boolean.TRUE.equals(medicalClearanceAttested);
    }

    @Override
    public String toString() {
        return "FitnessProfile[REDACTED]";
    }
}
