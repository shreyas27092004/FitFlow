package com.fitflow.auth;

import com.fitflow.security.TokenService;
import com.fitflow.user.entity.AdminPermission;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.AdminPermissionRepository;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AuthorizationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private AdminPermissionRepository adminPermissionRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("GET /api/v1/test-admin/class-edit: MEMBER role returns 403 FORBIDDEN")
    void testMemberCannotReachAdminEndpoint() throws Exception {
        User member = createUser("member1@fitflow.local", List.of(UserRole.Role.MEMBER));
        String token = tokenService.generateAccessToken(member.getId(), member.getEmail(), List.of("MEMBER"), List.of());

        mockMvc.perform(get("/api/v1/test-admin/class-edit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/v1/test-admin/class-edit: ADMIN without CLASS_EDIT permission returns 403 FORBIDDEN")
    void testAdminWithoutPermissionGets403() throws Exception {
        User admin = createUser("admin.noperm@fitflow.local", List.of(UserRole.Role.ADMIN));
        // Admin with only USER_READ
        String token = tokenService.generateAccessToken(admin.getId(), admin.getEmail(), List.of("ADMIN"), List.of("USER_READ"));

        mockMvc.perform(get("/api/v1/test-admin/class-edit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/v1/test-admin/class-edit: ADMIN with SUPER_ADMIN passes authorization")
    void testSuperAdminPasses() throws Exception {
        User admin = createUser("superadmin@fitflow.local", List.of(UserRole.Role.ADMIN));
        String token = tokenService.generateAccessToken(admin.getId(), admin.getEmail(), List.of("ADMIN"), List.of("SUPER_ADMIN"));

        mockMvc.perform(get("/api/v1/test-admin/class-edit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("class-edit-ok"));
    }

    @Test
    @DisplayName("POST /api/v1/test-admin/refund-approve: revoked sensitive permission takes effect immediately against DB")
    void testRevokedPermissionTakesEffectImmediatelyForSensitiveAction() throws Exception {
        User admin = createUser("finance.admin@fitflow.local", List.of(UserRole.Role.ADMIN));

        AdminPermission perm = AdminPermission.builder()
                .id(new AdminPermission.AdminPermissionId(admin.getId(), "REFUND_APPROVE"))
                .grantedBy(admin.getId())
                .grantedAt(Instant.now())
                .build();
        adminPermissionRepository.save(perm);

        // JWT token includes REFUND_APPROVE
        String token = tokenService.generateAccessToken(admin.getId(), admin.getEmail(), List.of("ADMIN"), List.of("REFUND_APPROVE"));

        // 1st request: granted in DB -> 200 OK
        mockMvc.perform(post("/api/v1/test-admin/refund-approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("refund-approve-ok"));

        // Revoke in DB without reissuing JWT
        adminPermissionRepository.delete(perm);

        // 2nd request: token still has claim, but DB re-validation detects revocation -> 403 FORBIDDEN!
        mockMvc.perform(post("/api/v1/test-admin/refund-approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/v1/me/permissions: authenticated user receives roles and permissions")
    void testGetMyPermissionsEndpoint() throws Exception {
        User user = createUser("me.user@fitflow.local", List.of(UserRole.Role.MEMBER));
        String token = tokenService.generateAccessToken(user.getId(), user.getEmail(), List.of("MEMBER"), List.of());

        mockMvc.perform(get("/api/v1/me/permissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("MEMBER")));
    }

    @Test
    @DisplayName("GET /api/v1/me/permissions: unauthenticated request returns 401 UNAUTHENTICATED ProblemDetail")
    void testUnauthenticatedReturns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/me/permissions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.title").value("Unauthenticated"));
    }

    private User createUser(String email, List<UserRole.Role> roles) {
        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .fullName("Auth Test User")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .status(User.Status.ACTIVE)
                .mustChangePassword(false)
                .failedLoginCount(0)
                .build());

        for (UserRole.Role role : roles) {
            userRoleRepository.save(UserRole.builder()
                    .id(new UserRole.UserRoleId(user.getId(), role))
                    .build());
        }

        return user;
    }
}
