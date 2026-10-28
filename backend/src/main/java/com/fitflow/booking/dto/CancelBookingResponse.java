package com.fitflow.booking.dto;

public record CancelBookingResponse(
        Long bookingId,
        String status
) {
}
