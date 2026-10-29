package com.fitflow.session.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RescheduleSessionRequest(
        @NotNull(message = "startsAt is required")
        @Future(message = "Session startsAt must be in the future")
        Instant startsAt,

        @NotNull(message = "version is required for optimistic locking")
        Integer version
) {
}
