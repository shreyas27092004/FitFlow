package com.fitflow.fitnessclass.controller;

import com.fitflow.common.pagination.PageResponse;
import com.fitflow.fitnessclass.FitnessClassService;
import com.fitflow.fitnessclass.dto.ClassDetailResponse;
import com.fitflow.fitnessclass.dto.ClassMetadataResponse;
import com.fitflow.fitnessclass.model.ClassCategory;
import com.fitflow.fitnessclass.model.ClassImpactLevel;
import com.fitflow.fitnessclass.model.ClassIntensity;
import com.fitflow.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/classes")
@RequiredArgsConstructor
@Tag(name = "Fitness Classes", description = "Public & Member Fitness Class Catalog")
public class ClassCatalogController {

    private final FitnessClassService fitnessClassService;

    @GetMapping("/metadata")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get class vocabularies and metadata enums")
    public ResponseEntity<ClassMetadataResponse> getMetadata() {
        return ResponseEntity.ok(fitnessClassService.getMetadata());
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List fitness classes with optional filters")
    public ResponseEntity<PageResponse<ClassDetailResponse>> getClasses(
            @RequestParam(required = false) ClassCategory category,
            @RequestParam(required = false) ClassIntensity intensity,
            @RequestParam(required = false) ClassImpactLevel impactLevel,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean isAdmin = isAdmin(principal);
        return ResponseEntity.ok(fitnessClassService.getClasses(category, intensity, impactLevel, q, page, size, isAdmin));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get fitness class details by ID")
    public ResponseEntity<ClassDetailResponse> getClassById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean isAdmin = isAdmin(principal);
        return ResponseEntity.ok(fitnessClassService.getClassById(id, isAdmin));
    }

    private boolean isAdmin(UserPrincipal principal) {
        if (principal == null) {
            return false;
        }
        boolean hasAdminRole = principal.getRoles() != null && principal.getRoles().contains("ADMIN");
        boolean hasSuperAdmin = principal.getPermissions() != null && principal.getPermissions().contains("SUPER_ADMIN");
        boolean hasClassEdit = principal.getPermissions() != null && principal.getPermissions().contains("CLASS_EDIT");
        return hasAdminRole || hasSuperAdmin || hasClassEdit;
    }
}
