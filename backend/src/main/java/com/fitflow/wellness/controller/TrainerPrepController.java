package com.fitflow.wellness.controller;

import com.fitflow.security.UserPrincipal;
import com.fitflow.wellness.TrainerPrepService;
import com.fitflow.wellness.dto.TrainerPrepResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trainer/sessions")
@RequiredArgsConstructor
@Tag(name = "Trainer Session Prep", description = "Privacy-preserving anonymous class injury counts and suggested modifications")
public class TrainerPrepController {

    private final TrainerPrepService trainerPrepService;

    @GetMapping("/{sessionId}/prep")
    @PreAuthorize("hasRole('TRAINER')")
    @Operation(summary = "Get anonymous aggregated restriction counts and movement modifications (min 3 consenting attendees)")
    public ResponseEntity<TrainerPrepResponse> getPrepSummary(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(trainerPrepService.getTrainerPrepSummary(principal.getId(), sessionId));
    }
}
