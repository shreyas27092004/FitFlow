package com.fitflow.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.auth.dto.RegisterRequest;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class RegisterIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Test
    @DisplayName("POST /register: valid member registration returns 201 Created with MEMBER role only")
    void testRegisterSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "newmember@fitflow.local",
                "StrongPass123!",
                "John Doe",
                LocalDate.of(1995, 5, 20),
                "+919876543210"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.email").value("newmember@fitflow.local"))
                .andExpect(jsonPath("$.fullName").value("John Doe"));

        Optional<User> userOpt = userRepository.findByEmail("newmember@fitflow.local");
        assertTrue(userOpt.isPresent());
        User user = userOpt.get();
        assertEquals(User.Status.ACTIVE, user.getStatus());
        assertFalse(user.getMustChangePassword());

        List<UserRole> roles = userRoleRepository.findByIdUserId(user.getId());
        assertEquals(1, roles.size());
        assertEquals(UserRole.Role.MEMBER, roles.get(0).getId().getRole());
    }

    @Test
    @DisplayName("POST /register: duplicate email (case-insensitive) returns 409 CONFLICT")
    void testRegisterDuplicateEmail() throws Exception {
        RegisterRequest initial = new RegisterRequest(
                "duplicate@fitflow.local",
                "Pass123456!",
                "First User",
                LocalDate.of(1992, 1, 1),
                null
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initial)))
                .andExpect(status().isCreated());

        // Same email with uppercase
        RegisterRequest duplicate = new RegisterRequest(
                "DUPLICATE@fitflow.local",
                "DifferentPass123!",
                "Second User",
                LocalDate.of(1990, 6, 15),
                null
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.detail").value(containsString("already registered")));
    }

    @Test
    @DisplayName("POST /register: weak password (< 10 chars) returns 400 VALIDATION_FAILED")
    void testRegisterWeakPasswordUnder10Chars() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "weakpass@fitflow.local",
                "Short1!",
                "Short Pass",
                LocalDate.of(1995, 1, 1),
                null
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value(containsString("at least 10 characters")));
    }

    @Test
    @DisplayName("POST /register: password identical to email returns 400 VALIDATION_FAILED")
    void testRegisterPasswordIdenticalToEmail() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "identical@fitflow.local",
                "identical@fitflow.local",
                "Identical User",
                LocalDate.of(1995, 1, 1),
                null
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value(containsString("cannot be identical to email")));
    }

    @Test
    @DisplayName("POST /register: under 14 years of age returns 400 AGE_RESTRICTED")
    void testRegisterUnderage() throws Exception {
        // Assume current year >= 2026; born 2018 is < 14
        RegisterRequest request = new RegisterRequest(
                "child@fitflow.local",
                "Password123!",
                "Child Member",
                LocalDate.of(2018, 5, 1),
                null
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AGE_RESTRICTED"))
                .andExpect(jsonPath("$.detail").value(containsString("at least 14 years old")));
    }

    @Test
    @DisplayName("POST /register: invalid email format returns 400 VALIDATION_FAILED with field error")
    void testRegisterInvalidEmail() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "invalid-email-format",
                "ValidPassword123!",
                "User Name",
                LocalDate.of(1995, 1, 1),
                null
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("POST /register: rejects unknown JSON properties")
    void testRegisterRejectsUnknownJsonProperties() throws Exception {
        String payloadWithUnknownProp = """
                {
                  "email": "unknownprop@fitflow.local",
                  "password": "StrongPass123!",
                  "fullName": "Unknown Tester",
                  "dateOfBirth": "1995-01-01",
                  "extraHackerField": "maliciousValue"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadWithUnknownProp))
                .andExpect(status().isBadRequest());
    }
}
