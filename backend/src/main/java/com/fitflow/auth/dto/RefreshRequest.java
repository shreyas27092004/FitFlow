package com.fitflow.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record RefreshRequest(
        String refreshToken
) {
    public RefreshRequest() {
        this(null);
    }

    @Override
    public String toString() {
        return "RefreshRequest[refreshToken=[REDACTED]]";
    }
}

