package com.fitflow.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record LogoutRequest(
        String refreshToken
) {
    public LogoutRequest() {
        this(null);
    }

    @Override
    public String toString() {
        return "LogoutRequest[refreshToken=[REDACTED]]";
    }
}

