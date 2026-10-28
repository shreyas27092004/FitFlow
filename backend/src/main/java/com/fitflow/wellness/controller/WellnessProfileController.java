package com.fitflow.wellness.controller;

import com.fitflow.security.UserPrincipal;
import com.fitflow.wellness.WellnessProfileService;
import com.fitflow.wellness.dto.BodyMetricRequest;
import com.fitflow.wellness.dto.BodyMetricResponse;
import com.fitflow.wellness.dto.DeleteHealthDataRequest;
import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.HealthExportResponse;
import com.fitflow.wellness.dto.ProfileResponse;
import com.fitflow.wellness.dto.SafeClassesResponse;
import com.fitflow.wellness.dto.SafetyVerdict;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/wellness")
@RequiredArgsConstructor
@Tag(name = "Wellness Profile", description = "Member encrypted health profile, safety screening, biometrics, and DPDP data rights")
public class WellnessProfileController {

    private final WellnessProfileService wellnessProfileService;

    @PutMapping("/profile")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Create or update encrypted fitness profile and return deterministic screening verdict")
    public ResponseEntity<ProfileResponse> saveProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody FitnessProfile request) {
        return ResponseEntity.ok(wellnessProfileService.saveProfile(principal.getId(), request));
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Get decrypted fitness profile and screening evaluation for owner")
    public ResponseEntity<ProfileResponse> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(wellnessProfileService.getProfile(principal.getId()));
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Export complete decrypted wellness records, metrics, feedback, and access log history")
    public ResponseEntity<HealthExportResponse> exportHealthData(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(wellnessProfileService.exportHealthData(principal.getId()));
    }

    @DeleteMapping("/data")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Permanently hard-delete all health records, metrics, logs, and proposals")
    public ResponseEntity<Void> deleteHealthData(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody DeleteHealthDataRequest request) {
        wellnessProfileService.deleteHealthData(principal.getId(), request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/metrics")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Record encrypted body metrics measurement (weight, height)")
    public ResponseEntity<BodyMetricResponse> recordMetric(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BodyMetricRequest request) {
        return ResponseEntity.ok(wellnessProfileService.recordMetric(principal.getId(), request));
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Get decrypted history of body metrics measurements")
    public ResponseEntity<List<BodyMetricResponse>> getMetrics(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(wellnessProfileService.getMetrics(principal.getId(), from, to));
    }

    @GetMapping("/classes/safe")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Get sessions filtered by deterministic safety rules (eligible vs avoid)")
    public ResponseEntity<SafeClassesResponse> getSafeClasses(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(wellnessProfileService.getSafeClasses(principal.getId(), from, to, page, size));
    }

    @GetMapping("/classes/{classId}/check")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Check safety verdict for a specific fitness class (advisory only)")
    public ResponseEntity<SafetyVerdict> checkClassSafety(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long classId) {
        return ResponseEntity.ok(wellnessProfileService.checkClassSafety(principal.getId(), classId));
    }
}
