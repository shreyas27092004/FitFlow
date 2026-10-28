package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record TrainerPrepResponse(
        @JsonProperty("attendeeCount")
        int attendeeCount,

        @JsonProperty("summaryAvailable")
        boolean summaryAvailable,

        @JsonProperty("restrictionCounts")
        Map<String, Integer> restrictionCounts,

        @JsonProperty("suggestedModifications")
        List<SuggestedModificationDto> suggestedModifications,

        @JsonProperty("note")
        String note
) {
    @Override
    public String toString() {
        return "TrainerPrepResponse[REDACTED]";
    }
}
