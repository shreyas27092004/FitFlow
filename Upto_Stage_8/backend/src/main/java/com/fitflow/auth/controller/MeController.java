package com.fitflow.auth.controller;

import com.fitflow.auth.AuthService;
import com.fitflow.auth.dto.PermissionsResponse;
import com.fitflow.security.UserPrincipal;
import com.fitflow.user.UserService;
import com.fitflow.user.dto.DeleteAccountRequest;
import com.fitflow.user.dto.UpdateProfileRequest;
import com.fitflow.user.dto.UserDataExportResponse;
import com.fitflow.user.dto.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Current User", description = "Current authenticated user profile, permissions, export, and self-service")
public class MeController {

    private final AuthService authService;
    private final UserService userService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current authenticated user profile")
    public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        UserProfileResponse profile = userService.getProfile(principal.getId());
        return ResponseEntity.ok(profile);
    }

    @PatchMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update current authenticated user profile")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {

        UserProfileResponse updated = userService.updateProfile(principal.getId(), request);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/export")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Export current user account data (profile, bookings, memberships, payments)")
    public ResponseEntity<UserDataExportResponse> exportData(@AuthenticationPrincipal UserPrincipal principal) {
        UserDataExportResponse export = userService.exportUserData(principal.getId());
        return ResponseEntity.ok(export);
    }

    @PostMapping("/delete-request")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Request soft deletion of member account")
    public ResponseEntity<Void> requestDelete(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody DeleteAccountRequest request) {

        userService.requestDelete(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping("/permissions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get roles and permissions of the currently authenticated user")
    public ResponseEntity<PermissionsResponse> getMyPermissions(
            @AuthenticationPrincipal UserPrincipal principal) {

        PermissionsResponse permissions = authService.getPermissions(principal.getId());
        return ResponseEntity.ok(permissions);
    }
}
