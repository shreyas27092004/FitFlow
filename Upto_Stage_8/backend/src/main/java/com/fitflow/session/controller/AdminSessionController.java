package com.fitflow.session.controller;

import com.fitflow.session.ClassSessionService;
import com.fitflow.session.dto.CancelSessionRequest;
import com.fitflow.session.dto.CreateSessionRequest;
import com.fitflow.session.dto.RescheduleSessionRequest;
import com.fitflow.session.dto.SessionDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/sessions")
@RequiredArgsConstructor
@Tag(name = "Admin Sessions", description = "Administrative Class Session Scheduling & Management")
public class AdminSessionController {

    private final ClassSessionService sessionService;

    @PostMapping
    @PreAuthorize("hasAuthority('CLASS_EDIT') or hasAuthority('SUPER_ADMIN')")
    @Operation(summary = "Schedule one or recurring class sessions")
    public ResponseEntity<List<SessionDetailResponse>> createSessions(@Valid @RequestBody CreateSessionRequest request) {
        List<SessionDetailResponse> created = sessionService.createSessions(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}/reschedule")
    @PreAuthorize("hasAuthority('CLASS_EDIT') or hasAuthority('SUPER_ADMIN')")
    @Operation(summary = "Reschedule a class session")
    public ResponseEntity<SessionDetailResponse> rescheduleSession(
            @PathVariable Long id,
            @Valid @RequestBody RescheduleSessionRequest request) {
        return ResponseEntity.ok(sessionService.rescheduleSession(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('CLASS_EDIT') or hasAuthority('SUPER_ADMIN')")
    @Operation(summary = "Cancel a class session and restore member entitlements")
    public ResponseEntity<SessionDetailResponse> cancelSession(
            @PathVariable Long id,
            @Valid @RequestBody CancelSessionRequest request) {
        return ResponseEntity.ok(sessionService.cancelSession(id, request));
    }
}
