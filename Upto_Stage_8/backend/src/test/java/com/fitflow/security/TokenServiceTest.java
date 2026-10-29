package com.fitflow.security;

import com.fitflow.common.error.ApiException;
import com.fitflow.testutil.MutableClock;
import com.fitflow.user.UserStatusService;
import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenServiceTest {

    private static final String TEST_SECRET = "very-secure-jwt-secret-key-that-has-at-least-32-bytes!!";
    private final MutableClock clock = MutableClock.ofUtc(Instant.parse("2026-10-28T00:00:00Z"));

    private JwtProperties jwtProperties;
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        jwtProperties = JwtProperties.of(TEST_SECRET, Duration.ofMinutes(15), Duration.ofDays(14), "fitflow");
        tokenService = new TokenService(jwtProperties, clock);
    }

    @Test
    @DisplayName("Create and parse access token succeeds with all claims preserved")
    void testCreateAndParseAccessToken() {
        String token = tokenService.generateAccessToken(
                100L,
                "user@fitflow.local",
                List.of("MEMBER"),
                List.of()
        );

        assertNotNull(token);

        Claims claims = tokenService.validateAndExtractClaims(token);
        assertEquals(100L, tokenService.extractUserId(claims));
        assertEquals("user@fitflow.local", tokenService.extractEmail(claims));
        assertEquals(List.of("MEMBER"), tokenService.extractRoles(claims));
        assertTrue(tokenService.extractPermissions(claims).isEmpty());

        UserPrincipal principal = tokenService.parseTokenToPrincipal(token);
        assertEquals(100L, principal.getId());
        assertEquals("user@fitflow.local", principal.getEmail());
    }

    @Test
    @DisplayName("Expired access token is rejected with 401 UNAUTHENTICATED")
    void testExpiredAccessTokenRejected() {
        String token = tokenService.generateAccessToken(
                101L,
                "expired@fitflow.local",
                List.of("MEMBER"),
                List.of()
        );

        // Advance clock by 16 minutes (TTL is 15 minutes)
        clock.advance(Duration.ofMinutes(16));

        ApiException ex = assertThrows(ApiException.class, () -> tokenService.validateAndExtractClaims(token));
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    @DisplayName("Tampered token signature is rejected with 401 UNAUTHENTICATED")
    void testTamperedSignatureRejected() {
        String token = tokenService.generateAccessToken(
                102L,
                "tampered@fitflow.local",
                List.of("MEMBER"),
                List.of()
        );

        // Modify last character of token
        char lastChar = token.charAt(token.length() - 1);
        char replaced = (lastChar == 'a') ? 'b' : 'a';
        String tampered = token.substring(0, token.length() - 1) + replaced;

        ApiException ex = assertThrows(ApiException.class, () -> tokenService.validateAndExtractClaims(tampered));
        assertTrue(ex.getMessage().contains("Invalid access token"));
    }

    @Test
    @DisplayName("Token with wrong issuer is rejected with 401 UNAUTHENTICATED")
    void testWrongIssuerRejected() {
        Date now = Date.from(clock.instant());
        Date expiry = Date.from(clock.instant().plus(Duration.ofMinutes(15)));

        String rogueToken = Jwts.builder()
                .subject("103")
                .claim("email", "rogue@fitflow.local")
                .issuer("rogue-auth-server") // Wrong issuer
                .issuedAt(now)
                .expiration(expiry)
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        ApiException ex = assertThrows(ApiException.class, () -> tokenService.validateAndExtractClaims(rogueToken));
        assertTrue(ex.getMessage().contains("Invalid access token"));
    }

    @Test
    @DisplayName("Malformed token string is rejected")
    void testMalformedTokenRejected() {
        assertThrows(ApiException.class, () -> tokenService.validateAndExtractClaims("not.a.valid.jwt.payload"));
    }

    @Test
    @DisplayName("UserStatusService caches user status for 30s and invalidates suspended users after window")
    void testUserStatusServiceCaching() {
        UserRepository userRepository = mock(UserRepository.class);
        UserStatusService statusService = new UserStatusService(userRepository, clock);

        User activeUser = User.builder()
                .email("active@fitflow.local")
                .status(User.Status.ACTIVE)
                .build();
        activeUser.setId(200L);

        when(userRepository.findById(200L)).thenReturn(Optional.of(activeUser));

        // 1st call: DB queried
        assertTrue(statusService.isActive(200L));
        verify(userRepository, times(1)).findById(200L);

        // 2nd call at +10s: cached result used without DB hit
        clock.advance(Duration.ofSeconds(10));
        assertTrue(statusService.isActive(200L));
        verify(userRepository, times(1)).findById(200L); // Still 1 DB hit

        // User is suspended in DB
        activeUser.setStatus(User.Status.SUSPENDED);

        // Still active within the 30-second window
        clock.advance(Duration.ofSeconds(10)); // Total 20s elapsed
        assertTrue(statusService.isActive(200L));

        // Advance beyond 30 seconds (total 35s elapsed)
        clock.advance(Duration.ofSeconds(15));
        assertFalse(statusService.isActive(200L));
        verify(userRepository, times(2)).findById(200L); // 2nd DB hit detects SUSPENDED
    }
}
