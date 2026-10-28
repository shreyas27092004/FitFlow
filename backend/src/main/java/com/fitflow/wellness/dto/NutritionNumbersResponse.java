package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record NutritionNumbersResponse(
        @JsonProperty("available")
        boolean available,

        @JsonProperty("reason")
        String reason,

        @JsonProperty("bmr")
        Integer bmr,

        @JsonProperty("tdee")
        Integer tdee,

        @JsonProperty("targetCalories")
        Integer targetCalories,

        @JsonProperty("floorApplied")
        boolean floorApplied,

        @JsonProperty("notes")
        List<String> notes,

        @JsonProperty("disclaimer")
        String disclaimer
) {
    @Override
    public String toString() {
        return "NutritionNumbersResponse[REDACTED]";
    }
}
