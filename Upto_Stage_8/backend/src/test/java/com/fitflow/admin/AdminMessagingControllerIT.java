package com.fitflow.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.admin.entity.AdminActionLog;
import com.fitflow.admin.repository.AdminActionLogRepository;
import com.fitflow.notification.entity.OutboxEvent;
import com.fitflow.notification.repository.OutboxEventRepository;
import com.fitflow.security.TokenService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminMessagingControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private AdminPermissionRepository adminPermissionRepository;

    @Autowired
    private AdminActionLogRepository adminActionLogRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private Clock clock;

    private String settingsAdminToken;
    private String plainAdminToken;
    private String superAdminToken;
    private String memberToken;

    private User settingsAdmin;
    private User superAdmin;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();

        // 1. Settings Admin
        settingsAdmin = userRepository.findByEmail("settings_msg_admin@example.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("settings_msg_admin@example.com")
                        .passwordHash("hash")
                        .fullName("Settings Msg Admin")
                        .status(User.Status.ACTIVE)
                        .build()));
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(settingsAdmin.getId(), UserRole.Role.ADMIN)));
        adminPermissionRepository.save(AdminPermission.builder()
                .id(new AdminPermission.AdminPermissionId(settingsAdmin.getId(), "SETTINGS_EDIT"))
                .grantedBy(settingsAdmin.getId())
                .grantedAt(clock.instant())
                .build());
        settingsAdminToken = tokenService.generateAccessToken(
                settingsAdmin.getId(), settingsAdmin.getEmail(), List.of("ADMIN"), List.of("SETTINGS_EDIT")
        );

        // 2. Plain Admin (no SETTINGS_EDIT)
        User plainAdmin = userRepository.findByEmail("plain_msg_admin@example.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("plain_msg_admin@example.com")
                        .passwordHash("hash")
                        .fullName("Plain Msg Admin")
                        .status(User.Status.ACTIVE)
                        .build()));
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(plainAdmin.getId(), UserRole.Role.ADMIN)));
        plainAdminToken = tokenService.generateAccessToken(
                plainAdmin.getId(), plainAdmin.getEmail(), List.of("ADMIN"), List.of()
        );

        // 3. Super Admin
        superAdmin = userRepository.findByEmail("super_msg_admin@example.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("super_msg_admin@example.com")
                        .passwordHash("hash")
                        .fullName("Super Msg Admin")
                        .status(User.Status.ACTIVE)
                        .build()));
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(superAdmin.getId(), UserRole.Role.ADMIN)));
        adminPermissionRepository.save(AdminPermission.builder()
                .id(new AdminPermission.AdminPermissionId(superAdmin.getId(), "SUPER_ADMIN"))
                .grantedBy(superAdmin.getId())
                .grantedAt(clock.instant())
                .build());
        superAdminToken = tokenService.generateAccessToken(
                superAdmin.getId(), superAdmin.getEmail(), List.of("ADMIN"), List.of("SUPER_ADMIN")
        );

        // 4. Member
        User member = userRepository.findByEmail("member_msg_user@example.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("member_msg_user@example.com")
                        .passwordHash("hash")
                        .fullName("Member Msg User")
                        .status(User.Status.ACTIVE)
                        .build()));
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(member.getId(), UserRole.Role.MEMBER)));
        memberToken = tokenService.generateAccessToken(
                member.getId(), member.getEmail(), List.of("MEMBER"), List.of()
        );
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (superAdmin != null) {
            adminPermissionRepository.deleteById(new AdminPermission.AdminPermissionId(superAdmin.getId(), "SUPER_ADMIN"));
        }
        if (settingsAdmin != null) {
            adminPermissionRepository.deleteById(new AdminPermission.AdminPermissionId(settingsAdmin.getId(), "SETTINGS_EDIT"));
        }
    }

    @Test
    @DisplayName("GET /api/v1/admin/messaging/outbox returns 401 for unauthenticated and 403 for member")
    void testGetOutboxSecurityChecks() throws Exception {
        mockMvc.perform(get("/api/v1/admin/messaging/outbox"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/messaging/outbox")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/messaging/outbox")
                        .header("Authorization", "Bearer " + plainAdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/admin/messaging/outbox returns 200 for SETTINGS_EDIT admin and omits payload")
    void testGetOutboxSuccessAndOmitsPayload() throws Exception {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("BOOKING")
                .aggregateId(55L)
                .eventType("booking.created")
                .payload("{\"secretHealthData\":\"HIGH_CONFIDENTIAL\",\"bookingId\":55}")
                .payloadSecret("secret-ciphertext")
                .status(OutboxEvent.Status.PENDING)
                .attempts(1)
                .createdAt(clock.instant())
                .nextAttemptAt(clock.instant())
                .build();
        outboxEventRepository.save(event);

        mockMvc.perform(get("/api/v1/admin/messaging/outbox")
                        .header("Authorization", "Bearer " + settingsAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].eventType", is("booking.created")))
                .andExpect(jsonPath("$.items[0].status", is("PENDING")))
                .andExpect(jsonPath("$.items[0].payload").doesNotExist())
                .andExpect(jsonPath("$.items[0].payloadSecret").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/v1/admin/messaging/outbox/{id}/retry resets FAILED event to PENDING")
    void testRetryOutboxEvent() throws Exception {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("PAYMENT")
                .aggregateId(77L)
                .eventType("payment.failed")
                .payload("{\"paymentId\":77}")
                .status(OutboxEvent.Status.FAILED)
                .attempts(10)
                .lastError("Timeout communicating with broker")
                .createdAt(clock.instant())
                .nextAttemptAt(clock.instant())
                .build();
        OutboxEvent saved = outboxEventRepository.save(event);

        mockMvc.perform(post("/api/v1/admin/messaging/outbox/" + saved.getId() + "/retry")
                        .header("Authorization", "Bearer " + settingsAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.attempts", is(0)));

        OutboxEvent reloaded = outboxEventRepository.findById(saved.getId()).orElseThrow();
        assertEquals(OutboxEvent.Status.PENDING, reloaded.getStatus());
        assertEquals(0, reloaded.getAttempts());
    }

    @Test
    @DisplayName("POST /api/v1/admin/messaging/outbox/{id}/retry on non-failed event returns 409 Conflict")
    void testRetryNonFailedEventReturns409() throws Exception {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("PAYMENT")
                .aggregateId(78L)
                .eventType("payment.succeeded")
                .payload("{\"paymentId\":78}")
                .status(OutboxEvent.Status.PENDING)
                .attempts(0)
                .createdAt(clock.instant())
                .nextAttemptAt(clock.instant())
                .build();
        OutboxEvent saved = outboxEventRepository.save(event);

        mockMvc.perform(post("/api/v1/admin/messaging/outbox/" + saved.getId() + "/retry")
                        .header("Authorization", "Bearer " + settingsAdminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/v1/admin/messaging/queues returns queue depths")
    void testGetQueues() throws Exception {
        mockMvc.perform(get("/api/v1/admin/messaging/queues")
                        .header("Authorization", "Bearer " + settingsAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").exists())
                .andExpect(jsonPath("$.queues").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/admin/messaging/dlq/{queue}/replay requires SUPER_ADMIN and clamps limit to 100")
    void testReplayDlqSuperAdminOnlyAndClampsLimit() throws Exception {
        // Settings admin (without SUPER_ADMIN) returns 403
        mockMvc.perform(post("/api/v1/admin/messaging/dlq/q.notifications.email.dlq/replay?limit=250")
                        .header("Authorization", "Bearer " + settingsAdminToken))
                .andExpect(status().isForbidden());

        // Super Admin succeeds
        mockMvc.perform(post("/api/v1/admin/messaging/dlq/q.notifications.email.dlq/replay?limit=250")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queue", is("q.notifications.email.dlq")));

        // Verify action was audited
        List<AdminActionLog> logs = adminActionLogRepository.findByAdminId(superAdmin.getId());
        assertFalse(logs.isEmpty());
        AdminActionLog replayLog = logs.get(logs.size() - 1);
        assertEquals("DLQ_REPLAY", replayLog.getAction());
        assertTrue(replayLog.getAfterJson().contains("100"), "Limit must be clamped to 100");
    }
}
