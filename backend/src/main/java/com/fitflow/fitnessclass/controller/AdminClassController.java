package com.fitflow.fitnessclass.controller;

import com.fitflow.fitnessclass.FitnessClassService;
import com.fitflow.fitnessclass.dto.ClassDetailResponse;
import com.fitflow.fitnessclass.dto.CreateClassRequest;
import com.fitflow.fitnessclass.dto.UpdateClassRequest;
import com.fitflow.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/classes")
@RequiredArgsConstructor
@Tag(name = "Admin Classes", description = "Administrative Fitness Class Management")
public class AdminClassController {

    private final FitnessClassService fitnessClassService;

    @PostMapping
    @PreAuthorize("hasAuthority('CLASS_EDIT') or hasAuthority('SUPER_ADMIN')")
    @Operation(summary = "Create a new fitness class")
    public ResponseEntity<ClassDetailResponse> createClass(
            @Valid @RequestBody CreateClassRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long creatorId = principal != null && principal.getId() != null ? principal.getId() : 1L;
        ClassDetailResponse created = fitnessClassService.createClass(request, creatorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CLASS_EDIT') or hasAuthority('SUPER_ADMIN')")
    @Operation(summary = "Update an existing fitness class")
    public ResponseEntity<ClassDetailResponse> updateClass(
            @PathVariable Long id,
            @Valid @RequestBody UpdateClassRequest request) {
        return ResponseEntity.ok(fitnessClassService.updateClass(id, request));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('CLASS_EDIT') or hasAuthority('SUPER_ADMIN')")
    @Operation(summary = "Deactivate a fitness class")
    public ResponseEntity<ClassDetailResponse> deactivateClass(@PathVariable Long id) {
        return ResponseEntity.ok(fitnessClassService.deactivateClass(id));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('CLASS_EDIT') or hasAuthority('SUPER_ADMIN')")
    @Operation(summary = "Activate a fitness class")
    public ResponseEntity<ClassDetailResponse> activateClass(@PathVariable Long id) {
        return ResponseEntity.ok(fitnessClassService.activateClass(id));
    }

    @PostMapping("/{id}/confirm-metadata")
    @PreAuthorize("hasAuthority('CLASS_EDIT') or hasAuthority('SUPER_ADMIN')")
    @Operation(summary = "Confirm fitness class metadata review")
    public ResponseEntity<ClassDetailResponse> confirmMetadata(@PathVariable Long id) {
        return ResponseEntity.ok(fitnessClassService.confirmMetadata(id));
    }
}
