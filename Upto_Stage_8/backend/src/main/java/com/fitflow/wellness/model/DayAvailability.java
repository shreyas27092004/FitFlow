package com.fitflow.wellness.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DayAvailability(
        @Min(1) @Max(7)
        @JsonProperty("dayOfWeek")
        int dayOfWeek,

        @NotBlank
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "from must be in HH:mm format")
        @JsonProperty("from")
        String from,

        @NotBlank
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "to must be in HH:mm format")
        @JsonProperty("to")
        String to
) {
    @Override
    public String toString() {
        return "DayAvailability[REDACTED]";
    }
}
