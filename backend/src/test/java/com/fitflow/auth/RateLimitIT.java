package com.fitflow.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.auth.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class RateLimitIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        loginAttemptService.clearRateLimitBuckets();
    }

    @Test
    @DisplayName("Rate Limiting: 10 login attempts allowed per minute per IP+email; 11th returns 429 RATE_LIMITED")
    void test11thLoginAttemptWithinMinuteReturns429() throws Exception {
        LoginRequest request = new LoginRequest("ratelimit.test@fitflow.local", "WrongPassword123!");

        // 10 attempts
        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", "198.51.100.42")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized()); // Failed credentials, but passed rate limit
        }

        // 11th attempt: rate limited!
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "198.51.100.42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}
