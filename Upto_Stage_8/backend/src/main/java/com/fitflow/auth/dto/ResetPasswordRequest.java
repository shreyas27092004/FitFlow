package com.fitflow.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = false)
public record ResetPasswordRequest(
        @NotBlank(message = "Reset token is required")
        String token,

        @NotBlank(message = "New password is required")
        String newPassword
) {
    @Override
    public String toString() {
        return "ResetPasswordRequest[token=[REDACTED], newPassword=[REDACTED]]";
    }
}
