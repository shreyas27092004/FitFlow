package com.fitflow.security;

import com.fitflow.user.entity.AdminPermission;
import com.fitflow.user.repository.AdminPermissionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionCheckerTest {

    private AdminPermissionRepository adminPermissionRepository;
    private PermissionChecker permissionChecker;

    @BeforeEach
    void setUp() {
        adminPermissionRepository = mock(AdminPermissionRepository.class);
        permissionChecker = new PermissionChecker(adminPermissionRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Unauthenticated user has no permissions")
    void testUnauthenticatedHasNoPermissions() {
        assertFalse(permissionChecker.has("CLASS_EDIT"));
    }

    @Test
    @DisplayName("MEMBER role cannot have admin permissions")
    void testMemberCannotHaveAdminPermission() {
        UserPrincipal principal = UserPrincipal.builder()
                .id(1L)
                .email("member@fitflow.local")
                .roles(List.of("MEMBER"))
                .permissions(List.of("CLASS_EDIT"))
                .build();
        setAuthentication(principal);

        assertFalse(permissionChecker.has("CLASS_EDIT"));
    }

    @Test
    @DisplayName("ADMIN with matching permission in token passes non-sensitive permission check")
    void testAdminWithPermissionPassesNonSensitive() {
        UserPrincipal principal = UserPrincipal.builder()
                .id(2L)
                .email("admin@fitflow.local")
                .roles(List.of("ADMIN"))
                .permissions(List.of("CLASS_EDIT"))
                .build();
        setAuthentication(principal);

        assertTrue(permissionChecker.has("CLASS_EDIT"));
        assertFalse(permissionChecker.has("PLAN_EDIT"));
    }

    @Test
    @DisplayName("ADMIN with SUPER_ADMIN passes any non-sensitive permission check")
    void testSuperAdminPassesAnyPermission() {
        UserPrincipal principal = UserPrincipal.builder()
                .id(3L)
                .email("super@fitflow.local")
                .roles(List.of("ADMIN"))
                .permissions(List.of("SUPER_ADMIN"))
                .build();
        setAuthentication(principal);

        assertTrue(permissionChecker.has("CLASS_EDIT"));
        assertTrue(permissionChecker.has("PLAN_EDIT"));
    }

    @Test
    @DisplayName("Sensitive permission re-validates against database: passes if granted in DB")
    void testSensitivePermissionPassesIfPresentInDatabase() {
        UserPrincipal principal = UserPrincipal.builder()
                .id(4L)
                .email("admin@fitflow.local")
                .roles(List.of("ADMIN"))
                .permissions(List.of("REFUND_APPROVE"))
                .build();
        setAuthentication(principal);

        AdminPermission dbPerm = AdminPermission.builder()
                .id(new AdminPermission.AdminPermissionId(4L, "REFUND_APPROVE"))
                .grantedBy(1L)
                .grantedAt(Instant.now())
                .build();
        when(adminPermissionRepository.findByIdUserId(4L)).thenReturn(List.of(dbPerm));

        assertTrue(permissionChecker.has("REFUND_APPROVE"));
    }

    @Test
    @DisplayName("Sensitive permission re-validates against database: fails immediately if revoked in DB")
    void testSensitivePermissionFailsIfRevokedInDatabase() {
        UserPrincipal principal = UserPrincipal.builder()
                .id(5L)
                .email("admin@fitflow.local")
                .roles(List.of("ADMIN"))
                .permissions(List.of("REFUND_APPROVE"))
                .build();
        setAuthentication(principal);

        // In DB, permission was revoked (empty list returned)
        when(adminPermissionRepository.findByIdUserId(5L)).thenReturn(List.of());

        assertFalse(permissionChecker.has("REFUND_APPROVE"));
    }

    private void setAuthentication(UserPrincipal principal) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
