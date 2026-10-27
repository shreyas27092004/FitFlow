package com.fitflow.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = false)
public record LogoutRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {
    @Override
    public String toString() {
        return "LogoutRequest[refreshToken=[REDACTED]]";
    }
}
