package com.fitflow.security;

import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

@Slf4j
@Getter
@Setter
@Component
public class JwtProperties {

    @Value("${fitflow.security.jwt.secret:}")
    private String secret;

    @Value("${fitflow.security.jwt.access-token-ttl:PT15M}")
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    @Value("${fitflow.security.jwt.refresh-token-ttl:P14D}")
    private Duration refreshTokenTtl = Duration.ofDays(14);

    @Value("${fitflow.security.jwt.issuer:fitflow}")
    private String issuer = "fitflow";

    private final Environment environment;
    private byte[] secretBytes;

    public JwtProperties(Environment environment) {
        this.environment = environment;
    }

    public static JwtProperties of(String secret, Duration accessTokenTtl, Duration refreshTokenTtl, String issuer) {
        JwtProperties properties = new JwtProperties(null);
        properties.secret = secret;
        properties.accessTokenTtl = accessTokenTtl;
        properties.refreshTokenTtl = refreshTokenTtl;
        properties.issuer = issuer;
        properties.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        return properties;
    }

    @PostConstruct
    public void init() {
        if (this.secretBytes != null) {
            return;
        }

        boolean isProd = environment != null && Arrays.asList(environment.getActiveProfiles()).contains("prod");

        if (secret != null && !secret.isBlank()) {
            byte[] bytes = secret.trim().getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                if (isProd) {
                    throw new IllegalStateException("Production startup failed: JWT_SECRET must be at least 32 bytes (256 bits).");
                }
                log.warn("Configured JWT_SECRET is shorter than 32 bytes; auto-generating development secret in .local/jwt.secret");
            } else {
                this.secretBytes = bytes;
                return;
            }
        }

        if (isProd) {
            throw new IllegalStateException("Production startup failed: JWT_SECRET environment variable is required and must not be blank.");
        }

        this.secretBytes = loadOrGenerateLocalDevJwtSecret();
    }

    public SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secretBytes);
    }

    private byte[] loadOrGenerateLocalDevJwtSecret() {
        File localDir = new File(".local");
        if (!localDir.exists()) {
            localDir.mkdirs();
        }

        File secretFile = new File(localDir, "jwt.secret");
        if (secretFile.exists()) {
            try {
                String content = Files.readString(secretFile.toPath()).trim();
                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                if (bytes.length >= 32) {
                    return bytes;
                }
            } catch (IOException e) {
                log.warn("Could not read .local/jwt.secret; regenerating development secret", e);
            }
        }

        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        String base64Secret = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        try {
            Files.writeString(secretFile.toPath(), base64Secret);
            log.warn("JWT secret not configured in dev profile. Auto-generating development secret in .local/jwt.secret");
        } catch (IOException e) {
            log.warn("Could not write .local/jwt.secret to disk; using in-memory random secret", e);
        }
        return base64Secret.getBytes(StandardCharsets.UTF_8);
    }
}
