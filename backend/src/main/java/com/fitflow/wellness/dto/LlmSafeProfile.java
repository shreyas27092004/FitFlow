package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitflow.wellness.model.ExperienceLevel;
import com.fitflow.wellness.model.Goal;
import com.fitflow.wellness.model.ScreeningStatus;
import com.fitflow.wellness.model.Sex;

import java.util.List;

public record LlmSafeProfile(
        @JsonProperty("ageBand")
        String ageBand,

        @JsonProperty("sex")
        Sex sex,

        @JsonProperty("experienceLevel")
        ExperienceLevel experienceLevel,

        @JsonProperty("goals")
        List<Goal> goals,

        @JsonProperty("injuryCodes")
        List<String> injuryCodes,

        @JsonProperty("conditionCodes")
        List<String> conditionCodes,

        @JsonProperty("screeningStatus")
        ScreeningStatus screeningStatus,

        @JsonProperty("doctorCheckRecommended")
        boolean doctorCheckRecommended
) {
    @Override
    public String toString() {
        return "LlmSafeProfile[REDACTED]";
    }
}
