package com.fitflow.booking.controller;

import com.fitflow.booking.BookingService;
import com.fitflow.booking.dto.AdminCancelBookingRequest;
import com.fitflow.booking.dto.CancelBookingResponse;
import com.fitflow.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/bookings")
@RequiredArgsConstructor
public class AdminBookingController {

    private final BookingService bookingService;

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('USER_EDIT', 'SUPER_ADMIN')")
    public CancelBookingResponse adminCancelBooking(
            @PathVariable Long id,
            @Valid @RequestBody AdminCancelBookingRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        bookingService.cancelBooking(id, principal.getId(), true, request.reason());
        return new CancelBookingResponse(id, "CANCELLED");
    }
}
