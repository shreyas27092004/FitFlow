package com.fitflow.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.admin.entity.AdminActionLog;
import com.fitflow.admin.repository.AdminActionLogRepository;
import com.fitflow.security.TokenService;
import com.fitflow.settings.dto.SetAiKeyRequest;
import com.fitflow.settings.dto.SetAiModelRequest;
import com.fitflow.settings.entity.AppSecret;
import com.fitflow.settings.entity.AppSetting;
import com.fitflow.settings.repository.AppSecretRepository;
import com.fitflow.settings.repository.AppSettingRepository;
import com.fitflow.user.entity.AdminPermission;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.AdminPermissionRepository;
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

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AiSettingsIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private AdminPermissionRepository adminPermissionRepository;

    @Autowired
    private AppSecretRepository appSecretRepository;

    @Autowired
    private AppSettingRepository appSettingRepository;

    @Autowired
    private AdminActionLogRepository adminActionLogRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User settingsAdminUser;
    private String settingsAdminToken;

    private User memberUser;
    private String memberToken;

    @BeforeEach
    void setUp() {
        appSecretRepository.deleteById(AiSettingsService.GEMINI_SECRET_NAME);
        appSettingRepository.deleteById(AiSettingsService.AI_MODEL_SETTING_NAME);

        // 1. Admin with SETTINGS_EDIT
        settingsAdminUser = userRepository.save(User.builder()
                .email("ai.settings.admin@fitflow.local")
                .passwordHash(passwordEncoder.encode("SettingsPass123!"))
                .fullName("AI Settings Admin")
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(settingsAdminUser.getId(), UserRole.Role.ADMIN)));
        adminPermissionRepository.save(AdminPermission.builder()
                .id(new AdminPermission.AdminPermissionId(settingsAdminUser.getId(), "SETTINGS_EDIT"))
                .grantedBy(settingsAdminUser.getId())
                .grantedAt(Instant.now())
                .build());
        settingsAdminToken = tokenService.generateAccessToken(
                settingsAdminUser.getId(), settingsAdminUser.getEmail(), List.of("ADMIN"), List.of("SETTINGS_EDIT")
        );

        // 2. Member
        memberUser = userRepository.save(User.builder()
                .email("ai.member@fitflow.local")
                .passwordHash(passwordEncoder.encode("MemberPass123!"))
                .fullName("AI Regular Member")
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(memberUser.getId(), UserRole.Role.MEMBER)));
        memberToken = tokenService.generateAccessToken(
                memberUser.getId(), memberUser.getEmail(), List.of("MEMBER"), List.of()
        );
    }

    @Test
    @DisplayName("GET /api/v1/admin/settings/ai: unauthenticated returns 401")
    void testGetSettingsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings/ai"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")));
    }

    @Test
    @DisplayName("GET /api/v1/admin/settings/ai: member without SETTINGS_EDIT returns 403")
    void testGetSettingsForbiddenForMember() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings/ai")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("GET /api/v1/admin/settings/ai: admin with SETTINGS_EDIT returns 200 with default mock mode")
    void testGetSettingsDefaultMockMode() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings/ai")
                        .header("Authorization", "Bearer " + settingsAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured", is(false)))
                .andExpect(jsonPath("$.maskedLast4", nullValue()))
                .andExpect(jsonPath("$.source", is("none")))
                .andExpect(jsonPath("$.mode", is("mock")))
                .andExpect(jsonPath("$.model", is("gemini-1.5-flash")));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/settings/ai/key: key not starting with AIza returns 400 VALIDATION_FAILED")
    void testSetKeyInvalidPrefixFails() throws Exception {
        SetAiKeyRequest request = new SetAiKeyRequest("InvalidKeyWithoutPrefix1234567890");

        mockMvc.perform(put("/api/v1/admin/settings/ai/key")
                        .header("Authorization", "Bearer " + settingsAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/settings/ai/key: key with whitespace returns 400 VALIDATION_FAILED")
    void testSetKeyWithWhitespaceFails() throws Exception {
        SetAiKeyRequest request = new SetAiKeyRequest("AIzaSy DummyKey With Spaces 12345");

        mockMvc.perform(put("/api/v1/admin/settings/ai/key")
                        .header("Authorization", "Bearer " + settingsAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/settings/ai/key: key shorter than 20 chars returns 400 VALIDATION_FAILED")
    void testSetKeyTooShortFails() throws Exception {
        SetAiKeyRequest request = new SetAiKeyRequest("AIzaSyShort");

        mockMvc.perform(put("/api/v1/admin/settings/ai/key")
                        .header("Authorization", "Bearer " + settingsAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/settings/ai/key: valid key is encrypted at rest, audits without key, and switches mode to gemini")
    void testSetKeySuccess() throws Exception {
        String testKey = "AIzaSyB391kLMopQrst998877xYz9876";
        SetAiKeyRequest request = new SetAiKeyRequest(testKey);

        mockMvc.perform(put("/api/v1/admin/settings/ai/key")
                        .header("Authorization", "Bearer " + settingsAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured", is(true)))
                .andExpect(jsonPath("$.maskedLast4", is("9876")))
                .andExpect(jsonPath("$.mode", is("gemini")))
                .andExpect(jsonPath("$.source", is("database")))
                .andExpect(jsonPath("$.apiKey").doesNotExist());

        // Verify stored in DB encrypted (never plaintext)
        AppSecret secret = appSecretRepository.findById(AiSettingsService.GEMINI_SECRET_NAME).orElseThrow();
        assertNotEquals(testKey, secret.getEncryptedValue());
        assertFalse(secret.getEncryptedValue().contains("AIzaSy"));

        // Verify audit log has no key
        List<AdminActionLog> logs = adminActionLogRepository.findAll().stream()
                .filter(l -> "AI_KEY_UPDATE".equals(l.getAction()))
                .toList();
        assertFalse(logs.isEmpty());
        assertFalse(logs.get(0).getAfterJson().contains("AIzaSy"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/settings/ai/test: mock mode returns ok=true")
    void testAiTestMockMode() throws Exception {
        mockMvc.perform(post("/api/v1/admin/settings/ai/test")
                        .header("Authorization", "Bearer " + settingsAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok", is(true)))
                .andExpect(jsonPath("$.message", is("Mock mode: no key configured")));
    }

    @Test
    @DisplayName("POST /api/v1/admin/settings/ai/test: with key returns ok=false with Stage 8 message")
    void testAiTestWithKeyCallsStub() throws Exception {
        SetAiKeyRequest setKeyReq = new SetAiKeyRequest("AIzaSyValidKeyForTestingMockMode1234");
        mockMvc.perform(put("/api/v1/admin/settings/ai/key")
                        .header("Authorization", "Bearer " + settingsAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(setKeyReq)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/settings/ai/test")
                        .header("Authorization", "Bearer " + settingsAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok", is(false)))
                .andExpect(jsonPath("$.message", is("Real Gemini client is added in Stage 8")));
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/settings/ai/key: deletes key and returns mode to mock")
    void testDeleteKeySuccess() throws Exception {
        SetAiKeyRequest setKeyReq = new SetAiKeyRequest("AIzaSyValidKeyForTestingMockMode1234");
        mockMvc.perform(put("/api/v1/admin/settings/ai/key")
                        .header("Authorization", "Bearer " + settingsAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(setKeyReq)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/admin/settings/ai/key")
                        .header("Authorization", "Bearer " + settingsAdminToken))
                .andExpect(status().isNoContent());

        assertFalse(appSecretRepository.existsById(AiSettingsService.GEMINI_SECRET_NAME));

        mockMvc.perform(get("/api/v1/admin/settings/ai")
                        .header("Authorization", "Bearer " + settingsAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured", is(false)))
                .andExpect(jsonPath("$.mode", is("mock")));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/settings/ai/model: invalid regex returns 400 VALIDATION_FAILED")
    void testSetModelInvalidRegexFails() throws Exception {
        SetAiModelRequest request = new SetAiModelRequest("bad model with spaces!");

        mockMvc.perform(put("/api/v1/admin/settings/ai/model")
                        .header("Authorization", "Bearer " + settingsAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/settings/ai/model: valid model updates setting and writes audit log")
    void testSetModelSuccess() throws Exception {
        SetAiModelRequest request = new SetAiModelRequest("gemini-1.5-pro-exp");

        mockMvc.perform(put("/api/v1/admin/settings/ai/model")
                        .header("Authorization", "Bearer " + settingsAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model", is("gemini-1.5-pro-exp")));

        AppSetting setting = appSettingRepository.findById(AiSettingsService.AI_MODEL_SETTING_NAME).orElseThrow();
        assertEquals("gemini-1.5-pro-exp", setting.getValue());

        List<AdminActionLog> logs = adminActionLogRepository.findAll().stream()
                .filter(l -> "AI_MODEL_UPDATE".equals(l.getAction()))
                .toList();
        assertFalse(logs.isEmpty());
    }
}
