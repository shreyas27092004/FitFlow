package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitflow.wellness.model.InjurySeverity;
import jakarta.validation.constraints.NotNull;

public record AcceptProposalRequest(
        @NotNull(message = "severity is required")
        @JsonProperty("severity")
        InjurySeverity severity
) {
    @Override
    public String toString() {
        return "AcceptProposalRequest[REDACTED]";
    }
}
