package com.fitflow.security;

import com.fitflow.common.error.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtProperties jwtProperties;
    private final Clock clock;

    public String generateAccessToken(Long userId, String email, List<String> roles, List<String> permissions) {
        Date now = Date.from(clock.instant());
        Date expiry = Date.from(clock.instant().plus(jwtProperties.getAccessTokenTtl()));

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("roles", roles != null ? roles : Collections.emptyList())
                .claim("permissions", permissions != null ? permissions : Collections.emptyList())
                .issuer(jwtProperties.getIssuer())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(jwtProperties.getSecretKey())
                .compact();
    }

    public Claims validateAndExtractClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(jwtProperties.getSecretKey())
                    .requireIssuer(jwtProperties.getIssuer())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("Token expired: {}", e.getMessage());
            throw ApiException.unauthenticated("Access token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid token: {}", e.getMessage());
            throw ApiException.unauthenticated("Invalid access token");
        }
    }

    public Long extractUserId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    public String extractEmail(Claims claims) {
        return claims.get("email", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Claims claims) {
        List<?> raw = claims.get("roles", List.class);
        if (raw == null) {
            return Collections.emptyList();
        }
        return raw.stream().map(Object::toString).toList();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractPermissions(Claims claims) {
        List<?> raw = claims.get("permissions", List.class);
        if (raw == null) {
            return Collections.emptyList();
        }
        return raw.stream().map(Object::toString).toList();
    }

    public UserPrincipal parseTokenToPrincipal(String token) {
        Claims claims = validateAndExtractClaims(token);
        return UserPrincipal.builder()
                .id(extractUserId(claims))
                .email(extractEmail(claims))
                .roles(extractRoles(claims))
                .permissions(extractPermissions(claims))
                .build();
    }
}
