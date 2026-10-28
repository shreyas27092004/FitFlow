package com.fitflow.auth;

import com.fitflow.auth.entity.PasswordResetToken;
import com.fitflow.auth.repository.PasswordResetTokenRepository;
import com.fitflow.common.error.ApiException;
import com.fitflow.notification.OutboxService;
import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final OutboxService outboxService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public void requestReset(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return;
        }

        String email = rawEmail.trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty() || userOpt.get().getStatus() != User.Status.ACTIVE) {
            log.debug("Password reset requested for non-existent or inactive email");
            return;
        }

        User user = userOpt.get();

        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String tokenHash = RefreshTokenService.sha256Hex(rawToken);

        Instant now = clock.instant();
        Instant expiresAt = now.plus(Duration.ofMinutes(30));

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .createdAt(now)
                .build();
        passwordResetTokenRepository.save(resetToken);

        // Store outbox event for Stage 6 email dispatch
        // Sensitive token is stored encrypted in payload_secret, never in payload or logs
        outboxService.appendWithSecret(
                "USER",
                user.getId(),
                "password.reset_requested",
                java.util.Map.of("userId", user.getId()),
                java.util.Map.of("token", rawToken)
        );

        log.info("Password reset token generated and outbox event dispatched for user id {}", user.getId());
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.validationFailed("Invalid or expired password reset token");
        }

        String tokenHash = RefreshTokenService.sha256Hex(rawToken);
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> ApiException.validationFailed("Invalid or expired password reset token"));

        Instant now = clock.instant();
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(now)) {
            throw ApiException.validationFailed("Invalid or expired password reset token");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> ApiException.validationFailed("Invalid or expired password reset token"));

        passwordPolicy.validate(newPassword, user.getEmail());

        token.setUsedAt(now);
        passwordResetTokenRepository.save(token);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);

        // Invalidate all active refresh tokens for the user
        refreshTokenService.revokeAllForUser(user.getId());

        log.info("Password reset successfully for user id {}", user.getId());
    }
}
