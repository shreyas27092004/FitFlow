package com.fitflow.session.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateSessionRequest(
        @NotNull(message = "classId is required")
        Long classId,

        @NotNull(message = "trainerId is required")
        Long trainerId,

        @NotNull(message = "startsAt is required")
        @Future(message = "Session startsAt must be in the future")
        Instant startsAt,

        @Min(value = 1, message = "Capacity must be at least 1")
        @Max(value = 200, message = "Capacity must not exceed 200")
        Integer capacity,

        @NotBlank(message = "Room is required")
        @Size(max = 60, message = "Room must not exceed 60 characters")
        String room,

        @Valid
        RecurrenceRuleDto recurrence
) {
}
