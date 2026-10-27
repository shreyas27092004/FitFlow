package com.fitflow.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.admin.dto.AdminCreateUserRequest;
import com.fitflow.admin.dto.AdminPermissionsRequest;
import com.fitflow.admin.dto.AdminUpdateUserRequest;
import com.fitflow.admin.entity.AdminActionLog;
import com.fitflow.admin.repository.AdminActionLogRepository;
import com.fitflow.notification.entity.OutboxEvent;
import com.fitflow.notification.repository.OutboxEventRepository;
import com.fitflow.security.TokenService;
import com.fitflow.trainer.entity.TrainerProfile;
import com.fitflow.trainer.repository.TrainerProfileRepository;
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
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AdminUserManagementIT {

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
    private TrainerProfileRepository trainerProfileRepository;

    @Autowired
    private AdminActionLogRepository adminActionLogRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User superAdminUser;
    private String superAdminToken;

    private User regularAdminUser;
    private String regularAdminToken;

    private User memberUser;
    private String memberToken;

    @BeforeEach
    void setUp() {
        // 1. Super Admin
        superAdminUser = userRepository.save(User.builder()
                .email("superadmin.it@fitflow.local")
                .passwordHash(passwordEncoder.encode("SuperSecret123!"))
                .fullName("Root Super Admin")
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(superAdminUser.getId(), UserRole.Role.ADMIN)));
        adminPermissionRepository.save(AdminPermission.builder()
                .id(new AdminPermission.AdminPermissionId(superAdminUser.getId(), "SUPER_ADMIN"))
                .grantedBy(superAdminUser.getId())
                .grantedAt(Instant.now())
                .build());
        superAdminToken = tokenService.generateAccessToken(
                superAdminUser.getId(), superAdminUser.getEmail(), List.of("ADMIN"), List.of("SUPER_ADMIN", "USER_READ", "USER_EDIT")
        );

        // 2. Regular Admin with USER_READ and USER_EDIT
        regularAdminUser = userRepository.save(User.builder()
                .email("regularadmin.it@fitflow.local")
                .passwordHash(passwordEncoder.encode("RegularPass123!"))
                .fullName("Regular Admin")
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(regularAdminUser.getId(), UserRole.Role.ADMIN)));
        adminPermissionRepository.save(AdminPermission.builder()
                .id(new AdminPermission.AdminPermissionId(regularAdminUser.getId(), "USER_READ"))
                .grantedBy(superAdminUser.getId())
                .grantedAt(Instant.now())
                .build());
        adminPermissionRepository.save(AdminPermission.builder()
                .id(new AdminPermission.AdminPermissionId(regularAdminUser.getId(), "USER_EDIT"))
                .grantedBy(superAdminUser.getId())
                .grantedAt(Instant.now())
                .build());
        regularAdminToken = tokenService.generateAccessToken(
                regularAdminUser.getId(), regularAdminUser.getEmail(), List.of("ADMIN"), List.of("USER_READ", "USER_EDIT")
        );

        // 3. Regular Member
        memberUser = userRepository.save(User.builder()
                .email("member.it@fitflow.local")
                .passwordHash(passwordEncoder.encode("MemberPass123!"))
                .fullName("Basic Member")
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(memberUser.getId(), UserRole.Role.MEMBER)));
        memberToken = tokenService.generateAccessToken(
                memberUser.getId(), memberUser.getEmail(), List.of("MEMBER"), List.of()
        );
    }

    @Test
    @DisplayName("GET /api/v1/admin/users: unauthenticated returns 401")
    void testListUsersUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")));
    }

    @Test
    @DisplayName("GET /api/v1/admin/users: member returns 403 FORBIDDEN")
    void testListUsersForbiddenForMember() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("GET /api/v1/admin/users: admin with USER_READ returns 200 with PageResponse")
    void testListUsersSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + regularAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", notNullValue()))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(20)));
    }

    @Test
    @DisplayName("GET /api/v1/admin/users: filtering by q, role, status returns filtered results")
    void testListUsersWithFilters() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("q", "Basic Member")
                        .param("role", "MEMBER")
                        .param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + regularAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].email", is(memberUser.getEmail())));
    }

    @Test
    @DisplayName("GET /api/v1/admin/users: page size is clamped to maximum 100")
    void testListUsersSizeClampedTo100() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("size", "250")
                        .header("Authorization", "Bearer " + regularAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", is(100)));
    }

    @Test
    @DisplayName("GET /api/v1/admin/users/{id}: non-existent user returns 404")
    void testGetUserNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/999999")
                        .header("Authorization", "Bearer " + regularAdminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    @Test
    @DisplayName("GET /api/v1/admin/users/{id}: returns details without password_hash or raw tokens")
    void testGetUserSuccessNeverExposesSecrets() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/" + superAdminUser.getId())
                        .header("Authorization", "Bearer " + regularAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(superAdminUser.getId().intValue())))
                .andExpect(jsonPath("$.email", is(superAdminUser.getEmail())))
                .andExpect(jsonPath("$.permissions[0]", is("SUPER_ADMIN")))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.tokens").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/v1/admin/users: creates TRAINER with mustChangePassword=true and emits user.created")
    void testCreateTrainerSuccess() throws Exception {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "trainer.new@fitflow.local",
                "New Fitness Coach",
                UserRole.Role.TRAINER,
                "+919876543219",
                50000L, // 500 INR in paise
                null
        );

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + regularAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("trainer.new@fitflow.local")))
                .andExpect(jsonPath("$.roles[0]", is("TRAINER")))
                .andExpect(jsonPath("$.password").doesNotExist());

        User created = userRepository.findByEmail("trainer.new@fitflow.local").orElseThrow();
        assertTrue(created.getMustChangePassword());

        // Trainer profile created with ratePerSessionPaise
        TrainerProfile profile = trainerProfileRepository.findById(created.getId()).orElseThrow();
        assertEquals(50000L, profile.getRatePerSessionPaise());

        // Outbox event emitted with tempPassword
        List<OutboxEvent> events = outboxEventRepository.findAll().stream()
                .filter(e -> "user.created".equals(e.getEventType()) && e.getAggregateId().equals(created.getId()))
                .toList();
        assertEquals(1, events.size());
        assertTrue(events.get(0).getPayload().contains("tempPasswordDeliveredVia"));

        // Admin action log written
        List<AdminActionLog> logs = adminActionLogRepository.findAll().stream()
                .filter(l -> "USER_CREATE".equals(l.getAction()) && l.getTargetId().equals(created.getId()))
                .toList();
        assertEquals(1, logs.size());
        assertFalse(logs.get(0).getAfterJson().contains("password"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/users: regular admin creating ADMIN returns 403 FORBIDDEN")
    void testCreateAdminWithoutSuperAdminForbidden() throws Exception {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "subadmin@fitflow.local",
                "Sub Admin",
                UserRole.Role.ADMIN,
                null,
                null,
                List.of("USER_READ")
        );

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + regularAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")))
                .andExpect(jsonPath("$.detail", containsString("SUPER_ADMIN permission is required")));
    }

    @Test
    @DisplayName("POST /api/v1/admin/users: SUPER_ADMIN creating ADMIN succeeds")
    void testCreateAdminWithSuperAdminSuccess() throws Exception {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "newadmin@fitflow.local",
                "Newly Created Admin",
                UserRole.Role.ADMIN,
                null,
                null,
                List.of("USER_READ", "USER_EDIT")
        );

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]", is("ADMIN")));
    }

    @Test
    @DisplayName("POST /api/v1/admin/users: duplicate email returns 409 CONFLICT")
    void testCreateUserDuplicateEmailConflict() throws Exception {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                superAdminUser.getEmail(),
                "Duplicate Super Admin",
                UserRole.Role.TRAINER,
                null,
                null,
                null
        );

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + regularAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICT")));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/users/{id}: optimistic locking version mismatch returns 409")
    void testUpdateUserOptimisticLockMismatch() throws Exception {
        AdminUpdateUserRequest request = new AdminUpdateUserRequest(
                "Updated Admin Name",
                "+911122334455",
                regularAdminUser.getVersion() + 50
        );

        mockMvc.perform(patch("/api/v1/admin/users/" + regularAdminUser.getId())
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("OPTIMISTIC_LOCK")));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/users/{id}: valid update succeeds and writes admin action log")
    void testUpdateUserSuccess() throws Exception {
        AdminUpdateUserRequest request = new AdminUpdateUserRequest(
                "Updated Admin Name",
                "+911122334455",
                regularAdminUser.getVersion()
        );

        mockMvc.perform(patch("/api/v1/admin/users/" + regularAdminUser.getId())
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName", is("Updated Admin Name")))
                .andExpect(jsonPath("$.phone", is("+911122334455")));
    }

    @Test
    @DisplayName("POST /api/v1/admin/users/{id}/suspend: cannot suspend your own account (409)")
    void testSuspendSelfFails() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/" + regularAdminUser.getId() + "/suspend")
                        .header("Authorization", "Bearer " + regularAdminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICT")))
                .andExpect(jsonPath("$.detail", containsString("Cannot suspend your own account")));
    }

    @Test
    @DisplayName("POST /api/v1/admin/users/{id}/suspend: cannot suspend the last SUPER_ADMIN (409)")
    void testSuspendLastSuperAdminFails() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/" + superAdminUser.getId() + "/suspend")
                        .header("Authorization", "Bearer " + regularAdminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICT")))
                .andExpect(jsonPath("$.detail", containsString("Cannot suspend the last SUPER_ADMIN")));
    }

    @Test
    @DisplayName("POST /api/v1/admin/users/{id}/suspend: valid target succeeds and emits user.suspended")
    void testSuspendTargetSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/" + memberUser.getId() + "/suspend")
                        .header("Authorization", "Bearer " + regularAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUSPENDED")));

        User suspended = userRepository.findById(memberUser.getId()).orElseThrow();
        assertEquals(User.Status.SUSPENDED, suspended.getStatus());

        boolean hasEvent = outboxEventRepository.findAll().stream()
                .anyMatch(e -> "user.suspended".equals(e.getEventType()) && e.getAggregateId().equals(memberUser.getId()));
        assertTrue(hasEvent);
    }

    @Test
    @DisplayName("POST /api/v1/admin/users/{id}/reactivate: reactivates a suspended user")
    void testReactivateUserSuccess() throws Exception {
        memberUser.setStatus(User.Status.SUSPENDED);
        userRepository.save(memberUser);

        mockMvc.perform(post("/api/v1/admin/users/" + memberUser.getId() + "/reactivate")
                        .header("Authorization", "Bearer " + regularAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        User reactivated = userRepository.findById(memberUser.getId()).orElseThrow();
        assertEquals(User.Status.ACTIVE, reactivated.getStatus());
    }

    @Test
    @DisplayName("PUT /api/v1/admin/users/{id}/permissions: non-admin user target returns 400 VALIDATION_FAILED")
    void testReplacePermissionsNonAdminTargetFails() throws Exception {
        AdminPermissionsRequest request = new AdminPermissionsRequest(List.of("USER_READ"));

        mockMvc.perform(put("/api/v1/admin/users/" + memberUser.getId() + "/permissions")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/users/{id}/permissions: removing own SUPER_ADMIN when last one returns 409 CONFLICT")
    void testRemoveLastSuperAdminFails() throws Exception {
        AdminPermissionsRequest request = new AdminPermissionsRequest(List.of("USER_READ", "USER_EDIT"));

        mockMvc.perform(put("/api/v1/admin/users/" + superAdminUser.getId() + "/permissions")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICT")))
                .andExpect(jsonPath("$.detail", containsString("at least one active super administrator must remain")));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/users/{id}/permissions: valid permission replace replaces set and writes audit log")
    void testReplacePermissionsSuccess() throws Exception {
        AdminPermissionsRequest request = new AdminPermissionsRequest(List.of("USER_READ", "SETTINGS_EDIT"));

        mockMvc.perform(put("/api/v1/admin/users/" + regularAdminUser.getId() + "/permissions")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", hasSize(2)));

        List<AdminPermission> updated = adminPermissionRepository.findByIdUserId(regularAdminUser.getId());
        assertEquals(2, updated.size());
    }
}
