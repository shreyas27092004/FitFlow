package com.fitflow.auth;

import com.fitflow.auth.entity.RefreshToken;
import com.fitflow.auth.repository.RefreshTokenRepository;
import com.fitflow.common.error.ApiException;
import com.fitflow.security.JwtProperties;
import com.fitflow.user.RefreshTokenRevoker;
import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService implements RefreshTokenRevoker {

    public record RotationResult(User user, String newRawToken) {}

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String createRefreshToken(Long userId, String userAgent, String ipAddress) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = sha256Hex(rawToken);
        String familyId = UUID.randomUUID().toString();
        Instant now = clock.instant();

        RefreshToken token = RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash)
                .familyId(familyId)
                .expiresAt(now.plus(jwtProperties.getRefreshTokenTtl()))
                .userAgent(userAgent)
                .ipHash(ipAddress != null ? sha256Hex(ipAddress) : null)
                .createdAt(now)
                .build();

        refreshTokenRepository.save(token);
        return rawToken;
    }

    @Transactional
    public RotationResult rotateRefreshToken(String rawToken, String userAgent, String ipAddress) {
        String hash = sha256Hex(rawToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.unauthenticated("Invalid refresh token"));

        Instant now = clock.instant();

        // Reuse detection: if a revoked token is presented again, revoke entire token family
        if (token.getRevokedAt() != null) {
            log.warn("Revoked refresh token reuse detected for family {}", token.getFamilyId());
            List<RefreshToken> family = refreshTokenRepository.findByFamilyId(token.getFamilyId());
            for (RefreshToken member : family) {
                if (member.getRevokedAt() == null) {
                    member.setRevokedAt(now);
                    refreshTokenRepository.save(member);
                }
            }
            throw ApiException.unauthenticated("Revoked refresh token reuse detected");
        }

        if (token.getExpiresAt().isBefore(now)) {
            throw ApiException.unauthenticated("Refresh token has expired");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> ApiException.unauthenticated("User not found"));

        if (user.getStatus() != User.Status.ACTIVE) {
            throw ApiException.unauthenticated("User account is not active");
        }

        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String newRawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String newHash = sha256Hex(newRawToken);

        RefreshToken newToken = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(newHash)
                .familyId(token.getFamilyId())
                .expiresAt(now.plus(jwtProperties.getRefreshTokenTtl()))
                .userAgent(userAgent)
                .ipHash(ipAddress != null ? sha256Hex(ipAddress) : null)
                .createdAt(now)
                .build();
        RefreshToken savedNew = refreshTokenRepository.save(newToken);

        token.setRevokedAt(now);
        token.setReplacedById(savedNew.getId());
        refreshTokenRepository.save(token);

        return new RotationResult(user, newRawToken);
    }

    @Transactional
    public void revokeFamily(String rawToken) {
        String hash = sha256Hex(rawToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            List<RefreshToken> family = refreshTokenRepository.findByFamilyId(token.getFamilyId());
            Instant now = clock.instant();
            for (RefreshToken member : family) {
                if (member.getRevokedAt() == null) {
                    member.setRevokedAt(now);
                    refreshTokenRepository.save(member);
                }
            }
        });
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId);
        Instant now = clock.instant();
        for (RefreshToken token : activeTokens) {
            token.setRevokedAt(now);
            refreshTokenRepository.save(token);
        }
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
