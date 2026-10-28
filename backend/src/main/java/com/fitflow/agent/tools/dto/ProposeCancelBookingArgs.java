package com.fitflow.agent.tools.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record ProposeCancelBookingArgs(
        @NotNull(message = "bookingId is required")
        @JsonProperty("bookingId")
        Long bookingId
) {}
