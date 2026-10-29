package com.fitflow.payment.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.payment.gateway.razorpay.RazorpayClient;
import com.fitflow.payment.settings.dto.SetRazorpayKeysRequest;
import com.fitflow.payment.settings.dto.SwitchGatewayRequest;
import com.fitflow.security.TokenService;
import com.fitflow.settings.entity.AppSetting;
import com.fitflow.settings.repository.AppSettingRepository;
import com.fitflow.user.entity.AdminPermission;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.AdminPermissionRepository;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.github.tomakehurst.wiremock.client.WireMock;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
public class PaymentSettingsIT {

    private static WireMockServer wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());

    @BeforeAll
    static void startWireMock() {
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("fitflow.payments.razorpay.base-url", wireMockServer::baseUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RazorpayClient razorpayClient;

    @Autowired
    private AppSettingRepository appSettingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private AdminPermissionRepository adminPermissionRepository;

    @Autowired
    private TokenService tokenService;

    private String adminToken;
    private String memberToken;

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        razorpayClient.deleteCredentials();

        // 1. Admin user with SETTINGS_EDIT
        User admin = userRepository.findByEmail("settings_admin@example.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("settings_admin@example.com")
                        .passwordHash("hash")
                        .fullName("Settings Admin")
                        .status(User.Status.ACTIVE)
                        .build()));
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(admin.getId(), UserRole.Role.ADMIN)));
        adminPermissionRepository.save(AdminPermission.builder()
                .id(new AdminPermission.AdminPermissionId(admin.getId(), "SETTINGS_EDIT"))
                .grantedBy(admin.getId())
                .grantedAt(Instant.now())
                .build());
        adminToken = tokenService.generateAccessToken(admin.getId(), "settings_admin@example.com", List.of("ADMIN"), List.of("SETTINGS_EDIT"));

        // 2. Member user without admin role
        User member = userRepository.findByEmail("normal_member@example.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("normal_member@example.com")
                        .passwordHash("hash")
                        .fullName("Normal Member")
                        .status(User.Status.ACTIVE)
                        .build()));
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(member.getId(), UserRole.Role.MEMBER)));
        memberToken = tokenService.generateAccessToken(member.getId(), "normal_member@example.com", List.of("MEMBER"), List.of());
    }

    @AfterEach
    void tearDown() {
        razorpayClient.deleteCredentials();
        AppSetting gw = appSettingRepository.findById("payments.gateway").orElseGet(() -> AppSetting.builder().name("payments.gateway").build());
        gw.setValue("MOCK");
        appSettingRepository.save(gw);
        jdbcTemplate.execute("DELETE FROM admin_action_log WHERE action IN ('CONFIGURE_RAZORPAY_KEYS', 'DELETE_RAZORPAY_KEYS', 'SWITCH_GATEWAY')");
    }

    @Test
    @DisplayName("GET /api/v1/admin/settings/payments: returns unconfigured state when keys absent")
    void testGetSettings_unconfigured() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings/payments")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gateway", is("MOCK")))
                .andExpect(jsonPath("$.razorpay.configured", is(false)))
                .andExpect(jsonPath("$.razorpay.keyId", nullValue()))
                .andExpect(jsonPath("$.razorpay.mode", nullValue()))
                .andExpect(jsonPath("$.webhookUrlHint", is("/api/v1/payments/webhooks/razorpay")));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/settings/payments/razorpay: stores test keys and returns masked response")
    void testSetRazorpayKeys_success() throws Exception {
        SetRazorpayKeysRequest req = new SetRazorpayKeysRequest(
                "rzp_test_ScYoa7HPECrM5A",
                "wqgMHBfXKilwkHhZHuutoB7v",
                "whsec_test12345"
        );

        mockMvc.perform(put("/api/v1/admin/settings/payments/razorpay")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razorpay.configured", is(true)))
                .andExpect(jsonPath("$.razorpay.keyId", is("rzp_test_ScYoa7HPECrM5A")))
                .andExpect(jsonPath("$.razorpay.keySecretMaskedLast4", is("oB7v")))
                .andExpect(jsonPath("$.razorpay.webhookSecretConfigured", is(true)))
                .andExpect(jsonPath("$.razorpay.mode", is("test")))
                .andExpect(jsonPath("$.razorpay.keySecret").doesNotExist());
    }

    @Test
    @DisplayName("PUT /api/v1/admin/settings/payments/razorpay: rejects invalid keyId format")
    void testSetRazorpayKeys_invalidKeyId() throws Exception {
        SetRazorpayKeysRequest req = new SetRazorpayKeysRequest(
                "invalid_prefix_12345",
                "wqgMHBfXKilwkHhZHuutoB7v",
                null
        );

        mockMvc.perform(put("/api/v1/admin/settings/payments/razorpay")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/v1/admin/settings/payments/razorpay: rejects short or whitespace keySecret")
    void testSetRazorpayKeys_invalidSecret() throws Exception {
        SetRazorpayKeysRequest req = new SetRazorpayKeysRequest(
                "rzp_test_ScYoa7HPECrM5A",
                "short",
                null
        );

        mockMvc.perform(put("/api/v1/admin/settings/payments/razorpay")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("LIVE KEY GUARD: rejects rzp_live_ keys in test/dev profiles with clear 400 error")
    void testSetRazorpayKeys_liveKeyGuard() throws Exception {
        SetRazorpayKeysRequest req = new SetRazorpayKeysRequest(
                "rzp_live_ABC1234567890",
                "wqgMHBfXKilwkHhZHuutoB7v",
                null
        );

        mockMvc.perform(put("/api/v1/admin/settings/payments/razorpay")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("non-prod environments")));
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/settings/payments/razorpay: removes credentials and falls back to MOCK")
    void testDeleteRazorpayKeys_fallsBackToMock() throws Exception {
        razorpayClient.setCredentials("rzp_test_ScYoa7HPECrM5A", "FAKEsecret1234567890abcd", "whsec", null);
        AppSetting gw = appSettingRepository.findById("payments.gateway").orElseGet(() -> AppSetting.builder().name("payments.gateway").build());
        gw.setValue("RAZORPAY");
        appSettingRepository.save(gw);

        mockMvc.perform(delete("/api/v1/admin/settings/payments/razorpay")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertFalse(razorpayClient.isConfigured());
        assertEquals("MOCK", appSettingRepository.findById("payments.gateway").orElseThrow().getValue());
    }

    @Test
    @DisplayName("PUT /api/v1/admin/settings/payments/gateway: rejects switch to RAZORPAY when keys unconfigured")
    void testSwitchGateway_unconfiguredKeys_throws409() throws Exception {
        SwitchGatewayRequest req = new SwitchGatewayRequest("RAZORPAY");

        mockMvc.perform(put("/api/v1/admin/settings/payments/gateway")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("keys are not configured")));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/settings/payments/gateway: rejects switch to RAZORPAY when test was not run in last 10m")
    void testSwitchGateway_staleTest_throws409() throws Exception {
        razorpayClient.setCredentials("rzp_test_ScYoa7HPECrM5A", "FAKEsecret1234567890abcd", "whsec", null);

        SwitchGatewayRequest req = new SwitchGatewayRequest("RAZORPAY");

        mockMvc.perform(put("/api/v1/admin/settings/payments/gateway")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("successful test connection within the last 10 minutes")));
    }

    @Test
    @DisplayName("POST /api/v1/admin/settings/payments/razorpay/test: performs authenticated GET against Razorpay and records success")
    void testTestRazorpay_success() throws Exception {
        razorpayClient.setCredentials("rzp_test_ScYoa7HPECrM5A", "FAKEsecret1234567890abcd", "whsec", null);

        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/payments?count=1"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"entity\":\"collection\",\"count\":0,\"items\":[]}")));

        mockMvc.perform(post("/api/v1/admin/settings/payments/razorpay/test")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok", is(true)))
                .andExpect(jsonPath("$.httpStatus", is(200)));

        assertTrue(razorpayClient.getLastTestSuccessAt().isPresent());
    }

    @Test
    @DisplayName("POST /api/v1/admin/settings/payments/razorpay/test: returns ok=false on 401 Unauthorized")
    void testTestRazorpay_invalidCredentials() throws Exception {
        razorpayClient.setCredentials("rzp_test_ScYoa7HPECrM5A", "FAKEsecret1234567890abcd", "whsec", null);

        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/payments?count=1"))
                .willReturn(WireMock.aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"code\":\"BAD_REQUEST_ERROR\",\"description\":\"Unauthorized\"}}")));

        mockMvc.perform(post("/api/v1/admin/settings/payments/razorpay/test")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok", is(false)))
                .andExpect(jsonPath("$.httpStatus", is(401)))
                .andExpect(jsonPath("$.message", containsString("Invalid key id or secret")));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/settings/payments/gateway: switches successfully to RAZORPAY after recent test")
    void testSwitchGateway_success() throws Exception {
        razorpayClient.setCredentials("rzp_test_ScYoa7HPECrM5A", "FAKEsecret1234567890abcd", "whsec", null);
        razorpayClient.recordLastTestSuccess(Instant.now());

        SwitchGatewayRequest req = new SwitchGatewayRequest("RAZORPAY");

        mockMvc.perform(put("/api/v1/admin/settings/payments/gateway")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gateway", is("RAZORPAY")));

        assertEquals("RAZORPAY", appSettingRepository.findById("payments.gateway").orElseThrow().getValue());
    }

    @Test
    @DisplayName("Security: Member without SETTINGS_EDIT receives 403 Forbidden")
    void testSecurity_memberForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings/payments")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security: Anonymous caller receives 401 Unauthorized")
    void testSecurity_anonymousUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings/payments"))
                .andExpect(status().isUnauthorized());
    }
}
