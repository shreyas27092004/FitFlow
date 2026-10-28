package com.fitflow.session.controller;

import com.fitflow.common.pagination.PageResponse;
import com.fitflow.fitnessclass.model.ClassCategory;
import com.fitflow.fitnessclass.model.ClassIntensity;
import com.fitflow.security.UserPrincipal;
import com.fitflow.session.ClassSessionService;
import com.fitflow.session.dto.SessionDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Tag(name = "Sessions", description = "Public & Member Class Session Schedule")
public class SessionCatalogController {

    private final ClassSessionService sessionService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List class sessions with filters")
    public ResponseEntity<PageResponse<SessionDetailResponse>> getSessions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) ClassCategory category,
            @RequestParam(required = false) ClassIntensity intensity,
            @RequestParam(required = false) Long trainerId,
            @RequestParam(required = false) Boolean onlyAvailable,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        Long memberId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(sessionService.getSessions(
                from, to, category, intensity, trainerId, onlyAvailable, page, size, memberId
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get class session by ID")
    public ResponseEntity<SessionDetailResponse> getSessionById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        Long memberId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(sessionService.getSessionById(id, memberId));
    }
}
