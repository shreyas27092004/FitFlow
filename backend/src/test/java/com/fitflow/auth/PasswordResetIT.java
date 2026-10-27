package com.fitflow.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.auth.dto.ForgotPasswordRequest;
import com.fitflow.auth.dto.ResetPasswordRequest;
import com.fitflow.auth.entity.PasswordResetToken;
import com.fitflow.auth.repository.PasswordResetTokenRepository;
import com.fitflow.auth.repository.RefreshTokenRepository;
import com.fitflow.notification.entity.OutboxEvent;
import com.fitflow.notification.repository.OutboxEventRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
public class PasswordResetIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("POST /forgot-password: always returns 202 with identical body for existing and non-existing email")
    void testForgotPasswordIdenticalBodies() throws Exception {
        createUser("registered@fitflow.local", "Password123!");

        ForgotPasswordRequest existingReq = new ForgotPasswordRequest("registered@fitflow.local");
        MvcResult res1 = mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(existingReq)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").isString())
                .andReturn();

        ForgotPasswordRequest ghostReq = new ForgotPasswordRequest("nonexistent@fitflow.local");
        MvcResult res2 = mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ghostReq)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").isString())
                .andReturn();

        assertEquals(
                res1.getResponse().getContentAsString(),
                res2.getResponse().getContentAsString(),
                "Responses must be strictly identical to prevent user enumeration attacks"
        );
    }

    @Test
    @DisplayName("POST /forgot-password writes outbox event password.reset_requested with token in payload")
    void testOutboxEventWrittenForExistingUser() throws Exception {
        User user = createUser("outboxcheck@fitflow.local", "Password123!");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("outboxcheck@fitflow.local"))))
                .andExpect(status().isAccepted());

        List<OutboxEvent> events = outboxEventRepository.findByAggregateIdAndEventType(user.getId(), "password.reset_requested");
        assertFalse(events.isEmpty(), "Outbox event must be created for Stage 6 email dispatch");

        OutboxEvent event = events.get(0);
        assertEquals("USER", event.getAggregateType());
        assertEquals(user.getId(), event.getAggregateId());
        assertEquals("password.reset_requested", event.getEventType());

        JsonNode payload = objectMapper.readTree(event.getPayload());
        assertEquals(user.getId(), payload.get("userId").asLong());
        String token = payload.get("token").asText();
        assertNotNull(token);

        // Verify token in DB is stored only as SHA-256 hash
        String tokenHash = RefreshTokenService.sha256Hex(token);
        PasswordResetToken dbToken = passwordResetTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertEquals(tokenHash, dbToken.getTokenHash());
        assertNotEquals(token, dbToken.getTokenHash());
        assertEquals(64, dbToken.getTokenHash().length());
    }

    @Test
    @DisplayName("POST /reset-password: valid token successfully updates password and marks token used")
    void testPasswordResetSuccess() throws Exception {
        User user = createUser("resetpw@fitflow.local", "OldPassword123!");
        String activeRefresh = refreshTokenService.createRefreshToken(user.getId(), "device", "127.0.0.1");

        // Trigger forgot-password
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("resetpw@fitflow.local"))))
                .andExpect(status().isAccepted());

        OutboxEvent event = outboxEventRepository.findByAggregateIdAndEventType(user.getId(), "password.reset_requested").get(0);
        String rawToken = objectMapper.readTree(event.getPayload()).get("token").asText();

        // Perform reset password
        ResetPasswordRequest resetReq = new ResetPasswordRequest(rawToken, "BrandNewPassword789!");
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().isNoContent());

        // Verify password changed
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("BrandNewPassword789!", updatedUser.getPasswordHash()));

        // Verify token marked used
        String tokenHash = RefreshTokenService.sha256Hex(rawToken);
        PasswordResetToken dbToken = passwordResetTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertNotNull(dbToken.getUsedAt());

        // Verify all refresh tokens revoked
        String refreshHash = RefreshTokenService.sha256Hex(activeRefresh);
        assertNotNull(refreshTokenRepository.findByTokenHash(refreshHash).orElseThrow().getRevokedAt());
    }

    @Test
    @DisplayName("POST /reset-password: token is single-use, second use fails with 400 VALIDATION_FAILED")
    void testPasswordResetTokenSingleUse() throws Exception {
        User user = createUser("singleuse@fitflow.local", "InitialPass123!");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("singleuse@fitflow.local"))))
                .andExpect(status().isAccepted());

        OutboxEvent event = outboxEventRepository.findByAggregateIdAndEventType(user.getId(), "password.reset_requested").get(0);
        String rawToken = objectMapper.readTree(event.getPayload()).get("token").asText();

        // 1st reset: success
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest(rawToken, "FirstNewPass123!"))))
                .andExpect(status().isNoContent());

        // 2nd reset: fails
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest(rawToken, "SecondNewPass123!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /reset-password: expired token is rejected with 400 VALIDATION_FAILED")
    void testExpiredPasswordResetTokenRejected() throws Exception {
        User user = createUser("expiredtoken@fitflow.local", "InitialPass123!");

        String rawToken = "sample-expired-token-32-bytes-long-string-123456";
        String tokenHash = RefreshTokenService.sha256Hex(rawToken);

        // Token expired 5 minutes ago
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .createdAt(Instant.now().minus(35, ChronoUnit.MINUTES))
                .build();
        passwordResetTokenRepository.save(expiredToken);

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewValidPass123!");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private User createUser(String email, String password) {
        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .fullName("Reset User")
                .dateOfBirth(LocalDate.of(1993, 3, 3))
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
