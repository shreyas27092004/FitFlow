package com.fitflow.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record MarkAttendanceRequest(
        @NotEmpty(message = "records list cannot be empty")
        @Valid
        List<AttendanceRecordDto> records
) {
}
