package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitflow.wellness.model.InjuryArea;
import com.fitflow.wellness.model.InjurySeverity;

import java.time.Instant;

public record RestrictionProposalDto(
        @JsonProperty("id")
        Long id,

        @JsonProperty("area")
        InjuryArea area,

        @JsonProperty("suggestedSeverity")
        InjurySeverity suggestedSeverity,

        @JsonProperty("status")
        String status,

        @JsonProperty("createdAt")
        Instant createdAt
) {
    @Override
    public String toString() {
        return "RestrictionProposalDto[REDACTED]";
    }
}
