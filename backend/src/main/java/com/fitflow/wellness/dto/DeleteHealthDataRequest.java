package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record DeleteHealthDataRequest(
        @NotBlank(message = "confirm is required")
        @JsonProperty("confirm")
        String confirm
) {
    @Override
    public String toString() {
        return "DeleteHealthDataRequest[REDACTED]";
    }
}
