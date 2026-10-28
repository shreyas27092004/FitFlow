package com.fitflow.booking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingResponse(
        Long bookingId,
        Long sessionId,
        String status,
        Integer position
) {
    public static BookingResponse confirmed(Long bookingId, Long sessionId) {
        return new BookingResponse(bookingId, sessionId, "CONFIRMED", null);
    }

    public static BookingResponse waitlisted(Long sessionId, int position) {
        return new BookingResponse(null, sessionId, "WAITLISTED", position);
    }
}
