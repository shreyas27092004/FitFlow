package com.fitflow.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.auth.dto.LoginRequest;
import com.fitflow.auth.dto.RefreshRequest;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class LogSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger fitflowLogger;

    @BeforeEach
    void setUp() {
        fitflowLogger = (Logger) LoggerFactory.getLogger("com.fitflow");
        listAppender = new ListAppender<>();
        listAppender.start();
        fitflowLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        if (fitflowLogger != null && listAppender != null) {
            fitflowLogger.detachAppender(listAppender);
        }
    }

    @Test
    @DisplayName("Security: Passwords, refresh tokens, and Authorization headers never appear in captured logs during login/refresh")
    void testNoSecretsAppearInCapturedLogs() throws Exception {
        String sensitivePassword = "SuperSensitivePassword999!";
        User user = createUser("logcheck@fitflow.local", sensitivePassword);

        // 1. Perform login with sensitive password
        LoginRequest loginReq = new LoginRequest("logcheck@fitflow.local", sensitivePassword);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk());

        // 2. Perform refresh with sensitive refresh token
        String rawRefreshToken = refreshTokenService.createRefreshToken(user.getId(), "agent", "127.0.0.1");
        RefreshRequest refreshReq = new RefreshRequest(rawRefreshToken);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk());

        // 3. Inspect every captured log message
        List<ILoggingEvent> logs = listAppender.list;
        assertFalse(logs.isEmpty(), "Logs should have been captured during web request execution");

        for (ILoggingEvent event : logs) {
            String msg = event.getFormattedMessage();
            assertFalse(
                    msg.contains(sensitivePassword),
                    "Raw password leaked in log output: " + msg
            );
            assertFalse(
                    msg.contains(rawRefreshToken),
                    "Raw refresh token leaked in log output: " + msg
            );
            assertFalse(
                    msg.toLowerCase().contains("authorization: bearer"),
                    "Authorization header leaked in log output: " + msg
            );
        }
    }

    private User createUser(String email, String rawPassword) {
        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .fullName("Log User")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
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
