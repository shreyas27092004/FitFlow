package com.fitflow.session.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record RecurrenceRuleDto(
        @NotEmpty(message = "Recurrence daysOfWeek must not be empty")
        List<Integer> daysOfWeek,

        @NotNull(message = "Recurrence until date is required")
        LocalDate until
) {
}
