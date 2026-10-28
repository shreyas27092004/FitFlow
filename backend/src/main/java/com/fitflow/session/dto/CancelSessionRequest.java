package com.fitflow.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelSessionRequest(
        @NotBlank(message = "Cancellation reason is required")
        @Size(max = 250, message = "Reason must not exceed 250 characters")
        String reason
) {
}
