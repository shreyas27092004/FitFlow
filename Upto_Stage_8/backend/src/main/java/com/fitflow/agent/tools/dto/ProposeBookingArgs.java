package com.fitflow.agent.tools.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record ProposeBookingArgs(
        @NotNull(message = "sessionId is required")
        @JsonProperty("sessionId")
        Long sessionId
) {}
