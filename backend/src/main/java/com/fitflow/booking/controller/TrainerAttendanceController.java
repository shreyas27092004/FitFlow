package com.fitflow.booking.controller;

import com.fitflow.booking.TrainerAttendanceService;
import com.fitflow.booking.dto.CompleteSessionRequest;
import com.fitflow.booking.dto.CompleteSessionResponse;
import com.fitflow.booking.dto.MarkAttendanceRequest;
import com.fitflow.booking.dto.MarkAttendanceResponse;
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
@RequestMapping("/api/v1/trainer/sessions")
@RequiredArgsConstructor
public class TrainerAttendanceController {

    private final TrainerAttendanceService attendanceService;

    @PostMapping("/{id}/attendance")
    @PreAuthorize("hasRole('TRAINER')")
    public MarkAttendanceResponse markAttendance(
            @PathVariable Long id,
            @Valid @RequestBody MarkAttendanceRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return attendanceService.markAttendance(id, principal.getId(), request);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('TRAINER')")
    public CompleteSessionResponse completeSession(
            @PathVariable Long id,
            @RequestBody(required = false) CompleteSessionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return attendanceService.completeSession(id, principal.getId(), request);
    }
}
