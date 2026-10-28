package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SafeClassesResponse(
        @JsonProperty("doctorCheckRecommended")
        boolean doctorCheckRecommended,

        @JsonProperty("disclaimer")
        String disclaimer,

        @JsonProperty("note")
        String note,

        @JsonProperty("eligible")
        List<SafeSessionItem> eligible,

        @JsonProperty("avoid")
        List<SafeSessionItem> avoid
) {
    @Override
    public String toString() {
        return "SafeClassesResponse[REDACTED]";
    }
}
