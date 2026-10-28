package com.fitflow.trainer.controller;

import com.fitflow.security.UserPrincipal;
import com.fitflow.trainer.TrainerService;
import com.fitflow.trainer.dto.TrainerProfileResponse;
import com.fitflow.trainer.dto.TrainerPublicResponse;
import com.fitflow.trainer.dto.TrainerSummaryResponse;
import com.fitflow.trainer.dto.UpdateTrainerProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Trainers", description = "Public trainer catalog and trainer profile self-service")
public class TrainerController {

    private final TrainerService trainerService;

    @GetMapping("/api/v1/trainers")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List active trainers with public details (name, bio, specialties)")
    public ResponseEntity<List<TrainerPublicResponse>> listTrainers() {
        List<TrainerPublicResponse> trainers = trainerService.listPublicTrainers();
        return ResponseEntity.ok(trainers);
    }

    @GetMapping("/api/v1/trainers/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get single active trainer public details")
    public ResponseEntity<TrainerPublicResponse> getTrainer(@PathVariable Long id) {
        TrainerPublicResponse trainer = trainerService.getPublicTrainer(id);
        return ResponseEntity.ok(trainer);
    }

    @GetMapping("/api/v1/trainer/me")
    @PreAuthorize("hasRole('TRAINER')")
    @Operation(summary = "Get current trainer's own full profile including pay rate")
    public ResponseEntity<TrainerProfileResponse> getMyTrainerProfile(
            @AuthenticationPrincipal UserPrincipal principal) {

        TrainerProfileResponse profile = trainerService.getMyTrainerProfile(principal.getId());
        return ResponseEntity.ok(profile);
    }

    @PatchMapping("/api/v1/trainer/me")
    @PreAuthorize("hasRole('TRAINER')")
    @Operation(summary = "Update trainer bio, specialties, certifications, and availability")
    public ResponseEntity<TrainerProfileResponse> updateMyTrainerProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateTrainerProfileRequest request) {

        TrainerProfileResponse updated = trainerService.updateMyTrainerProfile(principal.getId(), request);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/api/v1/trainer/me/summary")
    @PreAuthorize("hasRole('TRAINER')")
    @Operation(summary = "Get summary metrics for current trainer (upcoming sessions and earnings)")
    public ResponseEntity<TrainerSummaryResponse> getTrainerSummary(
            @AuthenticationPrincipal UserPrincipal principal) {

        TrainerSummaryResponse summary = trainerService.getTrainerSummary(principal.getId());
        return ResponseEntity.ok(summary);
    }
}
