package com.fitflow.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.auth.dto.LoginRequest;
import com.fitflow.notification.repository.OutboxEventRepository;
import com.fitflow.security.TokenService;
import com.fitflow.user.dto.DeleteAccountRequest;
import com.fitflow.user.dto.NotificationPrefsDto;
import com.fitflow.user.dto.UpdateProfileRequest;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class SelfServiceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private String token;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .email("selfservice.member@fitflow.local")
                .passwordHash(passwordEncoder.encode("SecurePass123!"))
                .fullName("Self Service Member")
                .phone("+919988776655")
                .dateOfBirth(LocalDate.of(1996, 7, 15))
                .status(User.Status.ACTIVE)
                .mustChangePassword(false)
                .failedLoginCount(0)
                .build());

        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(testUser.getId(), UserRole.Role.MEMBER)));
        token = tokenService.generateAccessToken(testUser.getId(), testUser.getEmail(), List.of("MEMBER"), List.of());
    }

    @Test
    @DisplayName("GET /api/v1/me: unauthenticated returns 401")
    void testGetProfileUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")));
    }

    @Test
    @DisplayName("GET /api/v1/me: authenticated returns 200 with full profile and default notification prefs")
    void testGetProfileSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(testUser.getId().intValue())))
                .andExpect(jsonPath("$.email", is(testUser.getEmail())))
                .andExpect(jsonPath("$.fullName", is("Self Service Member")))
                .andExpect(jsonPath("$.notificationPrefs.emailBookings", is(true)))
                .andExpect(jsonPath("$.notificationPrefs.emailReminders", is(true)))
                .andExpect(jsonPath("$.notificationPrefs.emailMarketing", is(false)))
                .andExpect(jsonPath("$.version", notNullValue()));
    }

    @Test
    @DisplayName("PATCH /api/v1/me: updates fullName, phone, and notification prefs")
    void testPatchProfileSuccess() throws Exception {
        NotificationPrefsDto prefs = new NotificationPrefsDto(true, false, true);
        UpdateProfileRequest request = new UpdateProfileRequest(
                "Updated Name",
                "+918888888888",
                null,
                prefs,
                testUser.getVersion()
        );

        mockMvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName", is("Updated Name")))
                .andExpect(jsonPath("$.phone", is("+918888888888")))
                .andExpect(jsonPath("$.notificationPrefs.emailReminders", is(false)))
                .andExpect(jsonPath("$.notificationPrefs.emailMarketing", is(true)));
    }

    @Test
    @DisplayName("PATCH /api/v1/me: version mismatch returns 409 OPTIMISTIC_LOCK")
    void testPatchProfileOptimisticLockMismatch() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest(
                "Concurrent Edit",
                null,
                null,
                null,
                testUser.getVersion() + 99
        );

        mockMvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("OPTIMISTIC_LOCK")));
    }

    @Test
    @DisplayName("PATCH /api/v1/me: changing existing dateOfBirth returns 400 VALIDATION_FAILED")
    void testPatchProfileChangeDobFails() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest(
                null,
                null,
                LocalDate.of(1990, 1, 1),
                null,
                testUser.getVersion()
        );

        mockMvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.detail", containsString("Date of birth cannot be updated once set")));
    }

    @Test
    @DisplayName("PATCH /api/v1/me: setting dateOfBirth when initially null succeeds")
    void testPatchProfileSetDobOnceSuccess() throws Exception {
        User userWithoutDob = userRepository.save(User.builder()
                .email("nodob@fitflow.local")
                .passwordHash(passwordEncoder.encode("Pass123456!"))
                .fullName("No DOB User")
                .dateOfBirth(null)
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(userWithoutDob.getId(), UserRole.Role.MEMBER)));
        String nodobToken = tokenService.generateAccessToken(userWithoutDob.getId(), userWithoutDob.getEmail(), List.of("MEMBER"), List.of());

        UpdateProfileRequest request = new UpdateProfileRequest(
                null,
                null,
                LocalDate.of(1995, 3, 20),
                null,
                userWithoutDob.getVersion()
        );

        mockMvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + nodobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateOfBirth", is("1995-03-20")));
    }

    @Test
    @DisplayName("PATCH /api/v1/me: setting underage dateOfBirth (< 14) returns 400 AGE_RESTRICTED")
    void testPatchProfileUnderageFails() throws Exception {
        User userWithoutDob = userRepository.save(User.builder()
                .email("underage@fitflow.local")
                .passwordHash(passwordEncoder.encode("Pass123456!"))
                .fullName("Underage User")
                .dateOfBirth(null)
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(userWithoutDob.getId(), UserRole.Role.MEMBER)));
        String nodobToken = tokenService.generateAccessToken(userWithoutDob.getId(), userWithoutDob.getEmail(), List.of("MEMBER"), List.of());

        UpdateProfileRequest request = new UpdateProfileRequest(
                null,
                null,
                LocalDate.now().minusYears(10),
                null,
                userWithoutDob.getVersion()
        );

        mockMvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + nodobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("AGE_RESTRICTED")));
    }

    @Test
    @DisplayName("GET /api/v1/me/export: exports account data and strictly excludes health data")
    void testExportUserDataExcludesHealth() throws Exception {
        mockMvc.perform(get("/api/v1/me/export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.email", is(testUser.getEmail())))
                .andExpect(jsonPath("$.bookings", notNullValue()))
                .andExpect(jsonPath("$.memberships", notNullValue()))
                .andExpect(jsonPath("$.payments", notNullValue()))
                .andExpect(jsonPath("$.health").doesNotExist())
                .andExpect(jsonPath("$.biometrics").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/v1/me/delete-request: wrong password returns 401 UNAUTHENTICATED")
    void testDeleteAccountWrongPasswordFails() throws Exception {
        DeleteAccountRequest request = new DeleteAccountRequest("WrongPassword!");

        mockMvc.perform(post("/api/v1/me/delete-request")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")));
    }

    @Test
    @DisplayName("POST /api/v1/me/delete-request: correct password soft-deletes, replaces email, and prevents login")
    void testDeleteAccountSuccess() throws Exception {
        DeleteAccountRequest request = new DeleteAccountRequest("SecurePass123!");

        mockMvc.perform(post("/api/v1/me/delete-request")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        User deleted = userRepository.findById(testUser.getId()).orElseThrow();
        assertEquals(User.Status.DELETED, deleted.getStatus());
        assertEquals("deleted-" + testUser.getId() + "@deleted.invalid", deleted.getEmail());
        assertNull(deleted.getFullName());
        assertNull(deleted.getPhone());

        // Subsequent login attempt fails with 401 UNAUTHENTICATED
        LoginRequest loginRequest = new LoginRequest(testUser.getEmail(), "SecurePass123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());

        // Verify outbox event user.deletion_requested
        boolean hasOutbox = outboxEventRepository.findAll().stream()
                .anyMatch(e -> "user.deletion_requested".equals(e.getEventType()) && e.getAggregateId().equals(testUser.getId()));
        assertTrue(hasOutbox);
    }
}
