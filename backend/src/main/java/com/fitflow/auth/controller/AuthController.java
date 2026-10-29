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
import com.fitflow.common.error.ApiException;
import com.fitflow.security.JwtProperties;
import com.fitflow.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
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

    public static final String REFRESH_COOKIE_NAME = "ff_refresh";
    public static final String CLIENT_HEADER = "X-Client";
    public static final String CSRF_HEADER = "X-Requested-With";
    public static final String CSRF_HEADER_VALUE = "fitflow";

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final JwtProperties jwtProperties;

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
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        String ip = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader("User-Agent");
        AuthResponse response = authService.login(request, ip, userAgent);

        setRefreshCookie(servletResponse, servletRequest, response.refreshToken());

        boolean isApiClient = "api".equalsIgnoreCase(servletRequest.getHeader(CLIENT_HEADER));
        AuthResponse clientResponse = new AuthResponse(
                response.accessToken(),
                isApiClient ? response.refreshToken() : null,
                response.expiresInSeconds(),
                response.user()
        );

        return ResponseEntity.ok(clientResponse);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and issue new access token")
    public ResponseEntity<AuthResponse> refresh(
            @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        String rawToken = (request != null && request.refreshToken() != null && !request.refreshToken().isBlank())
                ? request.refreshToken()
                : null;

        boolean usedCookie = false;
        if (rawToken == null) {
            rawToken = extractCookieToken(servletRequest);
            usedCookie = true;
        }

        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.validationFailed("Refresh token is required in request body or ff_refresh cookie");
        }

        if (usedCookie) {
            String xRequestedWith = servletRequest.getHeader(CSRF_HEADER);
            if (!CSRF_HEADER_VALUE.equalsIgnoreCase(xRequestedWith)) {
                throw ApiException.forbidden("Header X-Requested-With: fitflow is required for cookie-based authentication");
            }
        }

        String ip = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader("User-Agent");
        AuthResponse response = authService.refresh(new RefreshRequest(rawToken), ip, userAgent);

        setRefreshCookie(servletResponse, servletRequest, response.refreshToken());

        boolean isApiClient = "api".equalsIgnoreCase(servletRequest.getHeader(CLIENT_HEADER));
        AuthResponse clientResponse = new AuthResponse(
                response.accessToken(),
                isApiClient ? response.refreshToken() : null,
                response.expiresInSeconds(),
                response.user()
        );

        return ResponseEntity.ok(clientResponse);
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Revoke active token family and clear refresh cookie")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) LogoutRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        String token = (request != null && request.refreshToken() != null && !request.refreshToken().isBlank())
                ? request.refreshToken()
                : extractCookieToken(servletRequest);

        if (token != null && !token.isBlank()) {
            authService.logout(new LogoutRequest(token));
        }

        clearRefreshCookie(servletResponse, servletRequest);
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

    private void setRefreshCookie(HttpServletResponse response, HttpServletRequest request, String refreshToken) {
        boolean isSecure = isSecureCookie(request);
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(isSecure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(jwtProperties.getRefreshTokenTtl().getSeconds())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response, HttpServletRequest request) {
        boolean isSecure = isSecureCookie(request);
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(isSecure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private boolean isSecureCookie(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String serverName = request.getServerName();
        return !"localhost".equalsIgnoreCase(serverName) && !"127.0.0.1".equals(serverName);
    }

    private String extractCookieToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}

