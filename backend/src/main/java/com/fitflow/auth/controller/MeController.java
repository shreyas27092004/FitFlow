package com.fitflow.auth.controller;

import com.fitflow.auth.AuthService;
import com.fitflow.auth.dto.PermissionsResponse;
import com.fitflow.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Current User", description = "Current authenticated user profile and permissions")
public class MeController {

    private final AuthService authService;

    @GetMapping("/permissions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get roles and permissions of the currently authenticated user")
    public ResponseEntity<PermissionsResponse> getMyPermissions(
            @AuthenticationPrincipal UserPrincipal principal) {

        PermissionsResponse permissions = authService.getPermissions(principal.getId());
        return ResponseEntity.ok(permissions);
    }
}
