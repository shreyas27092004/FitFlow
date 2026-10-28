package com.fitflow.trainer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AvailabilitySlotDto(
        @Min(value = 1, message = "dayOfWeek must be between 1 (Monday) and 7 (Sunday)")
        @Max(value = 7, message = "dayOfWeek must be between 1 (Monday) and 7 (Sunday)")
        int dayOfWeek,

        @NotBlank(message = "start time is required")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Start time must be in HH:mm format")
        String start,

        @NotBlank(message = "end time is required")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "End time must be in HH:mm format")
        String end
) {}
