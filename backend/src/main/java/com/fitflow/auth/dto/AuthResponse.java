package com.fitflow.auth.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        UserDto user
) {
    @Override
    public String toString() {
        return "AuthResponse[accessToken=[REDACTED], refreshToken=[REDACTED], expiresInSeconds=" +
                expiresInSeconds + ", user=" + user + "]";
    }
}
