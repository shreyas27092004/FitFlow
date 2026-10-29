package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitflow.wellness.model.DietPreference;
import com.fitflow.wellness.model.ExperienceLevel;
import com.fitflow.wellness.model.Goal;
import com.fitflow.wellness.model.HealthCondition;
import com.fitflow.wellness.model.InjuryArea;
import com.fitflow.wellness.model.InjurySeverity;
import com.fitflow.wellness.model.Sex;

import java.util.List;
import java.util.Map;

public record WellnessMetadataResponse(
        @JsonProperty("experienceLevels")
        List<ExperienceLevel> experienceLevels,

        @JsonProperty("goals")
        List<Goal> goals,

        @JsonProperty("sexes")
        List<Sex> sexes,

        @JsonProperty("injuryAreas")
        List<InjuryArea> injuryAreas,

        @JsonProperty("injurySeverities")
        List<InjurySeverity> injurySeverities,

        @JsonProperty("conditions")
        List<HealthCondition> conditions,

        @JsonProperty("dietPreferences")
        List<DietPreference> dietPreferences,

        @JsonProperty("parqQuestions")
        Map<String, String> parqQuestions
) {
    @Override
    public String toString() {
        return "WellnessMetadataResponse[REDACTED]";
    }
}
