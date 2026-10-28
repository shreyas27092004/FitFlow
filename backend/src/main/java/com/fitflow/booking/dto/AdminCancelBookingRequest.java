package com.fitflow.booking.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminCancelBookingRequest(
        @NotBlank(message = "reason is required")
        String reason
) {
}
