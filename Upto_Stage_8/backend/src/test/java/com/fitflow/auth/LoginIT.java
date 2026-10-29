package com.fitflow.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.auth.dto.LoginRequest;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class LoginIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        loginAttemptService.clearRateLimitBuckets();
    }

    @Test
    @DisplayName("POST /login: valid credentials returns 200 with tokens and user metadata")
    void testLoginSuccess() throws Exception {
        User user = createUser("validuser@fitflow.local", "Password123!", User.Status.ACTIVE, false);

        LoginRequest request = new LoginRequest("validuser@fitflow.local", "Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.expiresInSeconds").isNumber())
                .andExpect(jsonPath("$.user.id").value(user.getId()))
                .andExpect(jsonPath("$.user.email").value("validuser@fitflow.local"))
                .andExpect(jsonPath("$.user.roles", hasItem("MEMBER")))
                .andExpect(jsonPath("$.user.mustChangePassword").value(false));
    }

    @Test
    @DisplayName("POST /login: wrong password and unknown email return identical 401 UNAUTHENTICATED bodies")
    void testWrongPasswordAndUnknownEmailReturnIdentical401() throws Exception {
        createUser("existinguser@fitflow.local", "RealPassword123!", User.Status.ACTIVE, false);

        // Case 1: Existing user with wrong password
        LoginRequest wrongPassword = new LoginRequest("existinguser@fitflow.local", "WrongPassword123!");
        MvcResult res1 = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPassword)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"))
                .andReturn();

        // Case 2: Non-existent user
        LoginRequest unknownEmail = new LoginRequest("ghost@fitflow.local", "SomePassword123!");
        MvcResult res2 = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknownEmail)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"))
                .andReturn();

        assertEquals(
                objectMapper.readTree(res1.getResponse().getContentAsString()).get("detail").asText(),
                objectMapper.readTree(res2.getResponse().getContentAsString()).get("detail").asText()
        );
    }

    @Test
    @DisplayName("POST /login: 5 failed attempts locks user account, 6th returns 429 LOCKED with Retry-After")
    void testLogin5FailuresLocksAccount() throws Exception {
        User user = createUser("lockout@fitflow.local", "CorrectPass123!", User.Status.ACTIVE, false);

        LoginRequest wrongPass = new LoginRequest("lockout@fitflow.local", "BadPass123!");

        // 5 consecutive failures
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(wrongPass)))
                    .andExpect(status().isUnauthorized());
        }

        User lockedUser = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(5, lockedUser.getFailedLoginCount());
        assertNotNull(lockedUser.getLockedUntil());
        assertTrue(lockedUser.getLockedUntil().isAfter(Instant.now()));

        // 6th attempt: account is locked, must return 429 TOO_MANY_REQUESTS with code LOCKED and Retry-After
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPass)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("LOCKED"));
    }

    @Test
    @DisplayName("POST /login: suspended user returns identical generic 401 UNAUTHENTICATED")
    void testLoginSuspendedUserRejected() throws Exception {
        createUser("suspended@fitflow.local", "ValidPass123!", User.Status.SUSPENDED, false);

        LoginRequest request = new LoginRequest("suspended@fitflow.local", "ValidPass123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /login: returns mustChangePassword = true when flag is set")
    void testLoginReturnsMustChangePasswordFlag() throws Exception {
        createUser("mustchange@fitflow.local", "TempPass123!", User.Status.ACTIVE, true);

        LoginRequest request = new LoginRequest("mustchange@fitflow.local", "TempPass123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true));
    }

    private User createUser(String email, String rawPassword, User.Status status, boolean mustChangePassword) {
        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .fullName("Test User")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .status(status)
                .mustChangePassword(mustChangePassword)
                .failedLoginCount(0)
                .build());

        userRoleRepository.save(UserRole.builder()
                .id(new UserRole.UserRoleId(user.getId(), UserRole.Role.MEMBER))
                .build());

        return user;
    }
}
