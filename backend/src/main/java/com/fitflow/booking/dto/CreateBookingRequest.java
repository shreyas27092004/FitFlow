package com.fitflow.booking.dto;

import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(
        @NotNull(message = "sessionId is required")
        Long sessionId
) {
}
