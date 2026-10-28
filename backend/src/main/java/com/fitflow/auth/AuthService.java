package com.fitflow.auth;

import com.fitflow.auth.dto.AuthResponse;
import com.fitflow.auth.dto.ChangePasswordRequest;
import com.fitflow.auth.dto.LoginRequest;
import com.fitflow.auth.dto.LogoutRequest;
import com.fitflow.auth.dto.PermissionsResponse;
import com.fitflow.auth.dto.RefreshRequest;
import com.fitflow.auth.dto.RegisterRequest;
import com.fitflow.auth.dto.RegisterResponse;
import com.fitflow.auth.dto.UserDto;
import com.fitflow.common.error.ApiException;
import com.fitflow.security.JwtProperties;
import com.fitflow.security.TokenService;
import com.fitflow.user.AgeService;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.AdminPermissionRepository;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String GENERIC_AUTH_ERROR = "Invalid email or password";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AdminPermissionRepository adminPermissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AgeService ageService;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final JwtProperties jwtProperties;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw ApiException.conflict("Email is already registered");
        }

        ageService.validateRegistrationAge(request.dateOfBirth());
        passwordPolicy.validate(request.password(), normalizedEmail);

        String passwordHash = passwordEncoder.encode(request.password());

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordHash)
                .fullName(request.fullName().trim())
                .dateOfBirth(request.dateOfBirth())
                .phone(request.phone() != null ? request.phone().trim() : null)
                .status(User.Status.ACTIVE)
                .mustChangePassword(false)
                .failedLoginCount(0)
                .build();

        User savedUser = userRepository.save(user);

        // All registered users start with role MEMBER only
        UserRole userRole = UserRole.builder()
                .id(new UserRole.UserRoleId(savedUser.getId(), UserRole.Role.MEMBER))
                .build();
        userRoleRepository.save(userRole);

        // Publish event for downstream listeners (e.g., auto-assign free tier membership)
        eventPublisher.publishEvent(new com.fitflow.auth.event.UserRegisteredEvent(
                savedUser.getId(),
                savedUser.getEmail(),
                UserRole.Role.MEMBER.name()
        ));

        log.info("Member registered successfully with userId={}", savedUser.getId());
        return new RegisterResponse(savedUser.getId(), savedUser.getEmail(), savedUser.getFullName());
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        loginAttemptService.checkRateLimit(ipAddress, request.email());

        String normalizedEmail = request.email().trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);

        if (userOpt.isEmpty()) {
            // Prevent user enumeration timing oracle
            passwordEncoder.matches("dummyPassword", "$2a$12$e8rO2mO6V.jN3f5kL4jL1e8rO2mO6V.jN3f5kL4jL1e8rO2mO6V.");
            throw ApiException.unauthenticated(GENERIC_AUTH_ERROR);
        }

        User user = userOpt.get();

        // Enforce account lockout if previously locked
        loginAttemptService.checkLockout(user);

        // Suspended or deleted users receive identical generic 401
        if (user.getStatus() != User.Status.ACTIVE) {
            log.warn("Login attempt on non-active user status={}", user.getStatus());
            throw ApiException.unauthenticated(GENERIC_AUTH_ERROR);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.onLoginFailed(user);
            throw ApiException.unauthenticated(GENERIC_AUTH_ERROR);
        }

        loginAttemptService.onLoginSuccess(user);
        log.info("User id {} logged in successfully from ip {}", user.getId(), ipAddress);

        List<String> roles = loadRoles(user.getId());
        List<String> permissions = loadPermissions(user.getId());

        String accessToken = tokenService.generateAccessToken(user.getId(), user.getEmail(), roles, permissions);
        String refreshToken = refreshTokenService.createRefreshToken(user.getId(), userAgent, ipAddress);

        UserDto userDto = new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                roles,
                permissions,
                Boolean.TRUE.equals(user.getMustChangePassword())
        );

        return new AuthResponse(
                accessToken,
                refreshToken,
                jwtProperties.getAccessTokenTtl().getSeconds(),
                userDto
        );
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request, String ipAddress, String userAgent) {
        RefreshTokenService.RotationResult result = refreshTokenService.rotateRefreshToken(
                request.refreshToken(),
                userAgent,
                ipAddress
        );

        User user = result.user();
        List<String> roles = loadRoles(user.getId());
        List<String> permissions = loadPermissions(user.getId());

        String accessToken = tokenService.generateAccessToken(user.getId(), user.getEmail(), roles, permissions);
        log.info("Token refreshed successfully for user id {}", user.getId());

        UserDto userDto = new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                roles,
                permissions,
                Boolean.TRUE.equals(user.getMustChangePassword())
        );

        return new AuthResponse(
                accessToken,
                result.newRawToken(),
                jwtProperties.getAccessTokenTtl().getSeconds(),
                userDto
        );
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenService.revokeFamily(request.refreshToken());
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthenticated("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.unauthenticated("Current password does not match");
        }

        passwordPolicy.validate(request.newPassword(), user.getEmail());

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        refreshTokenService.revokeAllForUser(user.getId());
        log.info("Password changed and refresh tokens revoked for user id {}", user.getId());
    }

    @Transactional(readOnly = true)
    public PermissionsResponse getPermissions(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthenticated("User not found"));

        List<String> roles = loadRoles(userId);
        List<String> permissions = loadPermissions(userId);

        return new PermissionsResponse(roles, permissions);
    }

    private List<String> loadRoles(Long userId) {
        List<UserRole> roles = userRoleRepository.findByIdUserId(userId);
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        return roles.stream().map(r -> r.getId().getRole().name()).toList();
    }

    private List<String> loadPermissions(Long userId) {
        var perms = adminPermissionRepository.findByIdUserId(userId);
        if (perms == null || perms.isEmpty()) {
            return Collections.emptyList();
        }
        return perms.stream().map(p -> p.getId().getPermission()).toList();
    }
}
