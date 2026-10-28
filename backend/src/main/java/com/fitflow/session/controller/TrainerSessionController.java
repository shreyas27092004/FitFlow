package com.fitflow.session.controller;

import com.fitflow.security.UserPrincipal;
import com.fitflow.session.ClassSessionService;
import com.fitflow.session.dto.TrainerSessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/trainer/sessions")
@RequiredArgsConstructor
@Tag(name = "Trainer Sessions", description = "Trainer Assigned Class Sessions & Attendee Roster")
public class TrainerSessionController {

    private final ClassSessionService sessionService;

    @GetMapping
    @PreAuthorize("hasRole('TRAINER')")
    @Operation(summary = "Get trainer's own sessions with attendee summaries")
    public ResponseEntity<List<TrainerSessionResponse>> getTrainerSessions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(sessionService.getTrainerSessions(principal.getId(), from, to));
    }
}
