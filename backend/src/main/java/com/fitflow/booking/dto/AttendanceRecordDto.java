package com.fitflow.booking.dto;

import com.fitflow.booking.model.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

public record AttendanceRecordDto(
        @NotNull(message = "bookingId is required")
        Long bookingId,

        @NotNull(message = "status is required")
        AttendanceStatus status
) {
}
