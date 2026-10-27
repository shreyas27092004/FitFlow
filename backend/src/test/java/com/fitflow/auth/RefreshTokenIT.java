package com.fitflow.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.auth.dto.ChangePasswordRequest;
import com.fitflow.auth.dto.LogoutRequest;
import com.fitflow.auth.dto.RefreshRequest;
import com.fitflow.auth.entity.RefreshToken;
import com.fitflow.auth.repository.RefreshTokenRepository;
import com.fitflow.security.TokenService;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class RefreshTokenIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("POST /refresh: rotation issues new token with same family_id and revokes presented token")
    void testRefreshTokenRotationSuccess() throws Exception {
        User user = createTestUser("rotation@fitflow.local", "Password123!");
        String initialRefreshToken = refreshTokenService.createRefreshToken(user.getId(), "agent", "127.0.0.1");

        RefreshRequest request = new RefreshRequest(initialRefreshToken);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        String newRefreshToken = responseJson.get("refreshToken").asText();
        assertNotEquals(initialRefreshToken, newRefreshToken);

        // Verify in DB that old token is revoked and linked to new token
        String oldHash = RefreshTokenService.sha256Hex(initialRefreshToken);
        RefreshToken oldToken = refreshTokenRepository.findByTokenHash(oldHash).orElseThrow();
        assertNotNull(oldToken.getRevokedAt());
        assertNotNull(oldToken.getReplacedById());

        String newHash = RefreshTokenService.sha256Hex(newRefreshToken);
        RefreshToken newToken = refreshTokenRepository.findByTokenHash(newHash).orElseThrow();
        assertEquals(oldToken.getFamilyId(), newToken.getFamilyId());
        assertEquals(oldToken.getReplacedById(), newToken.getId());
    }

    @Test
    @DisplayName("POST /refresh: reuse of a revoked token revokes entire family, and newest token then fails")
    void testReuseOfRevokedRefreshTokenRevokesEntireFamily() throws Exception {
        User user = createTestUser("reuse@fitflow.local", "Password123!");
        String tokenV1 = refreshTokenService.createRefreshToken(user.getId(), "agent", "127.0.0.1");

        // Legitimate rotation 1: V1 -> V2
        RefreshRequest req1 = new RefreshRequest(tokenV1);
        MvcResult res1 = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andReturn();
        String tokenV2 = objectMapper.readTree(res1.getResponse().getContentAsString()).get("refreshToken").asText();

        // Legitimate rotation 2: V2 -> V3
        RefreshRequest req2 = new RefreshRequest(tokenV2);
        MvcResult res2 = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andReturn();
        String tokenV3 = objectMapper.readTree(res2.getResponse().getContentAsString()).get("refreshToken").asText();

        // Attacker / replay: re-submits old token V1 (which was revoked)
        RefreshRequest attackerReq = new RefreshRequest(tokenV1);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attackerReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        // Entire family should now be revoked! Even latest token V3 must fail:
        RefreshRequest req3 = new RefreshRequest(tokenV3);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req3)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Refresh tokens are stored ONLY as SHA-256 hashes in database; raw token never appears in DB")
    void testRawRefreshTokenNeverStoredInDatabase() {
        User user = createTestUser("hashcheck@fitflow.local", "Password123!");
        String rawToken = refreshTokenService.createRefreshToken(user.getId(), "test-agent", "192.168.1.1");

        String expectedHash = RefreshTokenService.sha256Hex(rawToken);

        Optional<RefreshToken> foundByHash = refreshTokenRepository.findByTokenHash(expectedHash);
        assertTrue(foundByHash.isPresent());

        // Verify the database token_hash is strictly 64 hex characters and does NOT equal rawToken
        RefreshToken entity = foundByHash.get();
        assertEquals(expectedHash, entity.getTokenHash());
        assertNotEquals(rawToken, entity.getTokenHash());
        assertEquals(64, entity.getTokenHash().length());
    }

    @Test
    @DisplayName("POST /logout: authenticated user revokes that token family and returns 204")
    void testLogoutRevokesFamily() throws Exception {
        User user = createTestUser("logout@fitflow.local", "Password123!");
        String rawRefresh = refreshTokenService.createRefreshToken(user.getId(), "agent", "127.0.0.1");
        String accessToken = tokenService.generateAccessToken(user.getId(), user.getEmail(), List.of("MEMBER"), List.of());

        LogoutRequest request = new LogoutRequest(rawRefresh);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        // Refresh token should now be revoked
        String hash = RefreshTokenService.sha256Hex(rawRefresh);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash).orElseThrow();
        assertNotNull(token.getRevokedAt());

        // Attempting to refresh with it must fail
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(rawRefresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /change-password: updates password, clears must_change_password, and revokes all refresh tokens")
    void testChangePasswordRevokesAllUserRefreshTokens() throws Exception {
        User user = createTestUser("changepw@fitflow.local", "OldPassword123!");
        user.setMustChangePassword(true);
        userRepository.save(user);

        String refresh1 = refreshTokenService.createRefreshToken(user.getId(), "device1", "127.0.0.1");
        String refresh2 = refreshTokenService.createRefreshToken(user.getId(), "device2", "127.0.0.1");
        String accessToken = tokenService.generateAccessToken(user.getId(), user.getEmail(), List.of("MEMBER"), List.of());

        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword123!", "NewBrandPass456!");

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("NewBrandPass456!", updatedUser.getPasswordHash()));
        assertFalse(updatedUser.getMustChangePassword());

        // Verify all refresh tokens are revoked
        assertTrue(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.getId()).isEmpty());
    }

    private User createTestUser(String email, String password) {
        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .fullName("Refresh Test User")
                .dateOfBirth(LocalDate.of(1992, 1, 1))
                .status(User.Status.ACTIVE)
                .mustChangePassword(false)
                .failedLoginCount(0)
                .build());

        userRoleRepository.save(UserRole.builder()
                .id(new UserRole.UserRoleId(user.getId(), UserRole.Role.MEMBER))
                .build());

        return user;
    }
}
