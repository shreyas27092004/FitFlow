package com.fitflow.security;

import com.fitflow.user.entity.AdminPermission;
import com.fitflow.user.repository.AdminPermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Slf4j
@Component("perm")
@RequiredArgsConstructor
public class PermissionChecker {

    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    private static final Set<String> SENSITIVE_PERMISSIONS = Set.of(
            "REFUND_APPROVE",
            "PAYOUT_APPROVE",
            "USER_EDIT",
            "SETTINGS_EDIT"
    );

    private final AdminPermissionRepository adminPermissionRepository;

    public boolean has(String permission) {
        if (permission == null || permission.isBlank()) {
            return false;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Object principalObj = authentication.getPrincipal();
        if (!(principalObj instanceof UserPrincipal principal)) {
            return false;
        }

        if (principal.getRoles() == null || !principal.getRoles().contains("ADMIN")) {
            return false;
        }

        List<String> tokenPermissions = principal.getPermissions();
        if (tokenPermissions == null) {
            return false;
        }

        boolean hasInToken = tokenPermissions.contains(SUPER_ADMIN) || tokenPermissions.contains(permission);
        if (!hasInToken) {
            return false;
        }

        // Re-validate against database for sensitive actions to ensure immediate revocation takes effect
        if (isSensitive(permission)) {
            return checkAgainstDatabase(principal.getId(), permission);
        }

        return true;
    }

    public boolean isSensitive(String permission) {
        return SENSITIVE_PERMISSIONS.contains(permission);
    }

    private boolean checkAgainstDatabase(Long userId, String requiredPermission) {
        List<AdminPermission> dbPerms = adminPermissionRepository.findByIdUserId(userId);
        if (dbPerms == null || dbPerms.isEmpty()) {
            log.warn("Sensitive permission check failed: no DB permissions found for user {}", userId);
            return false;
        }

        for (AdminPermission ap : dbPerms) {
            String perm = ap.getId().getPermission();
            if (SUPER_ADMIN.equalsIgnoreCase(perm) || requiredPermission.equalsIgnoreCase(perm)) {
                return true;
            }
        }

        log.warn("Sensitive permission check failed: permission {} revoked in DB for user {}", requiredPermission, userId);
        return false;
    }
}
