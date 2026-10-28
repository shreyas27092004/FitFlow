package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitflow.wellness.model.InjuryArea;
import com.fitflow.wellness.model.InjurySeverity;
import jakarta.validation.constraints.NotNull;

public record InjuryDto(
        @NotNull(message = "Injury area is required")
        @JsonProperty("area")
        InjuryArea area,

        @NotNull(message = "Injury severity is required")
        @JsonProperty("severity")
        InjurySeverity severity
) {
    @Override
    public String toString() {
        return "InjuryDto[REDACTED]";
    }
}
