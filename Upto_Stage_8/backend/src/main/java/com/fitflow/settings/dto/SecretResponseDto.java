package com.fitflow.settings.dto;

import java.time.Instant;

public record SecretResponseDto(
        String name,
        String maskedValue,
        Integer keyVersion,
        Instant updatedAt
) {
    @Override
    public String toString() {
        return "SecretResponseDto{" +
                "name='" + name + '\'' +
                ", maskedValue='" + maskedValue + '\'' +
                ", keyVersion=" + keyVersion +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
