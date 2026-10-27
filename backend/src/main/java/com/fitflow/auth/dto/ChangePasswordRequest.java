package com.fitflow.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = false)
public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        String newPassword
) {
    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=[REDACTED], newPassword=[REDACTED]]";
    }
}
