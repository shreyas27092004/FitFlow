package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProfileResponse(
        @JsonProperty("profile")
        FitnessProfile profile,

        @JsonProperty("screening")
        ScreeningResult screening
) {
    @Override
    public String toString() {
        return "ProfileResponse[REDACTED]";
    }
}
