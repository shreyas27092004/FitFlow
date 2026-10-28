package com.fitflow.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = false)
public record RefreshRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {
    @Override
    public String toString() {
        return "RefreshRequest[refreshToken=[REDACTED]]";
    }
}
