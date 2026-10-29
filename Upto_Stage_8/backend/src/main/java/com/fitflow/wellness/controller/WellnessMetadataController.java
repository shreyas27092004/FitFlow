package com.fitflow.wellness.controller;

import com.fitflow.wellness.WellnessProfileService;
import com.fitflow.wellness.dto.WellnessMetadataResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wellness/metadata")
@RequiredArgsConstructor
@Tag(name = "Wellness Metadata", description = "Coded health vocabularies and PAR-Q screening questionnaire")
public class WellnessMetadataController {

    private final WellnessProfileService wellnessProfileService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get coded health vocabularies, enum values, and PAR-Q questions")
    public ResponseEntity<WellnessMetadataResponse> getMetadata() {
        return ResponseEntity.ok(wellnessProfileService.getMetadata());
    }
}
