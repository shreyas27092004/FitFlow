package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitflow.wellness.model.InjuryArea;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClassFeedbackRequest(
        @NotNull(message = "rating is required")
        @Min(value = 1, message = "rating must be between 1 and 5")
        @Max(value = 5, message = "rating must be between 1 and 5")
        @JsonProperty("rating")
        Integer rating,

        @JsonProperty("feltPain")
        boolean feltPain,

        @JsonProperty("painArea")
        InjuryArea painArea,

        @Size(max = 500, message = "comment must not exceed 500 characters")
        @JsonProperty("comment")
        String comment
) {
    @Override
    public String toString() {
        return "ClassFeedbackRequest[REDACTED]";
    }
}
