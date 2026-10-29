package com.fitflow.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.auth.controller.AuthController;
import com.fitflow.auth.dto.LoginRequest;
import com.fitflow.auth.repository.RefreshTokenRepository;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import jakarta.servlet.http.Cookie;
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

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class CookieAuthIT {

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
    private RefreshTokenRepository refreshTokenRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .email("cookieuser@fitflow.local")
                .passwordHash(passwordEncoder.encode("SecurePass123!"))
                .fullName("Cookie Test User")
                .dateOfBirth(java.time.LocalDate.of(1990, 1, 1))
                .status(User.Status.ACTIVE)
                .mustChangePassword(false)
                .failedLoginCount(0)
                .build());

        userRoleRepository.save(UserRole.builder()
                .id(new UserRole.UserRoleId(testUser.getId(), UserRole.Role.MEMBER))
                .build());
    }

    @Test
    @DisplayName("Stage 11: Browser login sets HttpOnly Strict cookie and omits refreshToken from JSON body")
    void testBrowserLoginSetsCookieAndOmitsTokenFromBody() throws Exception {
        LoginRequest request = new LoginRequest("cookieuser@fitflow.local", "SecurePass123!");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        List<String> setCookieHeaders = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertFalse(setCookieHeaders.isEmpty(), "Expected Set-Cookie header to be present");

        String cookieHeader = setCookieHeaders.stream()
                .filter(c -> c.startsWith("ff_refresh="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ff_refresh cookie missing in Set-Cookie header"));

        assertTrue(cookieHeader.contains("HttpOnly"), "Cookie must be HttpOnly");
        assertTrue(cookieHeader.contains("SameSite=Strict"), "Cookie must have SameSite=Strict");
        assertTrue(cookieHeader.contains("Path=/api/v1/auth"), "Cookie must have Path=/api/v1/auth");
        assertTrue(cookieHeader.contains("Max-Age=1209600"), "Cookie must have Max-Age corresponding to 14 days");
    }

    @Test
    @DisplayName("Stage 11: API client (X-Client: api) receives refreshToken in both JSON body and cookie")
    void testApiClientLoginReturnsTokenInBodyAndCookie() throws Exception {
        LoginRequest request = new LoginRequest("cookieuser@fitflow.local", "SecurePass123!");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Client", "api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNotNull(root.get("refreshToken").asText());

        List<String> setCookieHeaders = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertTrue(setCookieHeaders.stream().anyMatch(c -> c.startsWith("ff_refresh=")));
    }

    @Test
    @DisplayName("Stage 11: Refresh via ff_refresh cookie succeeds when X-Requested-With: fitflow is present")
    void testRefreshViaCookieWithCsrfHeaderSucceeds() throws Exception {
        // Step 1: Login as browser to get ff_refresh cookie
        LoginRequest loginReq = new LoginRequest("cookieuser@fitflow.local", "SecurePass123!");
        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshCookie = loginRes.getResponse().getCookie("ff_refresh");
        assertNotNull(refreshCookie, "ff_refresh cookie must be set on login");

        // Step 2: Call /refresh with cookie and X-Requested-With: fitflow (no request body)
        MvcResult refreshRes = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .header("X-Requested-With", "fitflow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        Cookie rotatedCookie = refreshRes.getResponse().getCookie("ff_refresh");
        assertNotNull(rotatedCookie, "Rotated ff_refresh cookie must be present");
        assertFalse(rotatedCookie.getValue().isBlank());
    }

    @Test
    @DisplayName("Stage 11: Refresh via cookie fails with 403 when X-Requested-With header is missing")
    void testRefreshViaCookieFailsWithoutCsrfHeader() throws Exception {
        LoginRequest loginReq = new LoginRequest("cookieuser@fitflow.local", "SecurePass123!");
        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andReturn();

        Cookie refreshCookie = loginRes.getResponse().getCookie("ff_refresh");
        assertNotNull(refreshCookie);

        // Missing X-Requested-With header
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Stage 11: Logout clears ff_refresh cookie with Max-Age=0 and revokes token family")
    void testLogoutClearsCookieAndRevokesFamily() throws Exception {
        LoginRequest loginReq = new LoginRequest("cookieuser@fitflow.local", "SecurePass123!");
        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Client", "api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andReturn();

        Cookie refreshCookie = loginRes.getResponse().getCookie("ff_refresh");
        String accessToken = objectMapper.readTree(loginRes.getResponse().getContentAsString()).get("accessToken").asText();

        // Logout with cookie and accessToken
        MvcResult logoutRes = mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .cookie(refreshCookie))
                .andExpect(status().isNoContent())
                .andReturn();

        // Verify cookie is cleared (Max-Age=0)
        String logoutSetCookie = logoutRes.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(logoutSetCookie);
        assertTrue(logoutSetCookie.contains("ff_refresh="));
        assertTrue(logoutSetCookie.contains("Max-Age=0"));

        // Subsequent refresh with old cookie must fail (family was revoked)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .header("X-Requested-With", "fitflow"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Stage 11: Token reuse detection works seamlessly via cookie authentication")
    void testCookieReuseDetectionWorks() throws Exception {
        // Login
        LoginRequest loginReq = new LoginRequest("cookieuser@fitflow.local", "SecurePass123!");
        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andReturn();
        Cookie tokenV1 = loginRes.getResponse().getCookie("ff_refresh");

        // Legitimate rotation: V1 -> V2
        MvcResult rot1 = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(tokenV1)
                        .header("X-Requested-With", "fitflow"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie tokenV2 = rot1.getResponse().getCookie("ff_refresh");

        // Attacker replays old token V1 via cookie
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(tokenV1)
                        .header("X-Requested-With", "fitflow"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        // Family was wiped: legitimate token V2 is now also rejected
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(tokenV2)
                        .header("X-Requested-With", "fitflow"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
