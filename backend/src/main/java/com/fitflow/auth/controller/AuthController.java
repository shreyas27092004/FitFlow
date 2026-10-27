package com.fitflow.auth.controller;

import com.fitflow.auth.AuthService;
import com.fitflow.auth.PasswordResetService;
import com.fitflow.auth.dto.AuthResponse;
import com.fitflow.auth.dto.ChangePasswordRequest;
import com.fitflow.auth.dto.ForgotPasswordRequest;
import com.fitflow.auth.dto.ForgotPasswordResponse;
import com.fitflow.auth.dto.LoginRequest;
import com.fitflow.auth.dto.LogoutRequest;
import com.fitflow.auth.dto.RefreshRequest;
import com.fitflow.auth.dto.RegisterRequest;
import com.fitflow.auth.dto.RegisterResponse;
import com.fitflow.auth.dto.ResetPasswordRequest;
import com.fitflow.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication, registration, token refresh, and password management")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/register")
    @Operation(summary = "Register a new member account")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user credentials and issue JWT tokens")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {

        String ip = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader("User-Agent");
        AuthResponse response = authService.login(request, ip, userAgent);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and issue new access token")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest servletRequest) {

        String ip = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader("User-Agent");
        AuthResponse response = authService.refresh(request, ip, userAgent);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Revoke active token family")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Change password for the authenticated user and revoke active tokens")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {

        authService.changePassword(principal.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset token (always returns 202)")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        passwordResetService.requestReset(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                new ForgotPasswordResponse("If the email is registered, a password reset link has been sent.")
        );
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using valid single-use token")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
