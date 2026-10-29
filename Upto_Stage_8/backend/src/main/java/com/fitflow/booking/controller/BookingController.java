package com.fitflow.booking.controller;

import com.fitflow.booking.BookingService;
import com.fitflow.booking.dto.CreateBookingRequest;
import com.fitflow.booking.dto.MemberBookingResponse;
import com.fitflow.booking.model.BookingStatus;
import com.fitflow.common.pagination.PageResponse;
import com.fitflow.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/bookings")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<?> createBooking(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        BookingService.BookingResult result = bookingService.createBooking(
                request, idempotencyKey, principal.getId()
        );

        if (result.cached() && result.body() instanceof String jsonPayload) {
            return ResponseEntity.status(result.status())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonPayload);
        }

        return ResponseEntity.status(result.status()).body(result.body());
    }

    @DeleteMapping("/bookings/{id}")
    @PreAuthorize("hasRole('MEMBER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        bookingService.cancelBooking(id, principal.getId(), false, null);
    }

    @GetMapping("/me/bookings")
    @PreAuthorize("hasRole('MEMBER')")
    public PageResponse<MemberBookingResponse> getMyBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return bookingService.getMemberBookings(principal.getId(), status, from, to, page, size);
    }

    @DeleteMapping("/waitlist/{sessionId}")
    @PreAuthorize("hasRole('MEMBER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveWaitlist(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        bookingService.leaveWaitlist(sessionId, principal.getId());
    }
}
