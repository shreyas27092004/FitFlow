package com.fitflow.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.admin.dto.AdminCreateUserRequest;
import com.fitflow.admin.dto.AdminPermissionsRequest;
import com.fitflow.admin.dto.AdminUpdateUserRequest;
import com.fitflow.admin.dto.AdminUserDetailResponse;
import com.fitflow.admin.dto.UserSummaryDto;
import com.fitflow.common.error.ApiException;
import com.fitflow.common.pagination.PageRequestDto;
import com.fitflow.common.pagination.PageResponse;
import com.fitflow.notification.OutboxService;
import com.fitflow.security.PermissionChecker;
import com.fitflow.security.UserPrincipal;
import com.fitflow.trainer.entity.TrainerProfile;
import com.fitflow.trainer.repository.TrainerProfileRepository;
import com.fitflow.user.RefreshTokenRevoker;
import com.fitflow.user.entity.AdminPermission;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.AdminPermissionRepository;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final Set<String> WHITELISTED_SORT_FIELDS = Set.of("createdAt", "fullName", "email");
    private static final String TEMP_PW_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AdminPermissionRepository adminPermissionRepository;
    private final TrainerProfileRepository trainerProfileRepository;
    private final OutboxService outboxService;
    private final RefreshTokenRevoker refreshTokenRevoker;
    private final AdminActionLogger adminActionLogger;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public PageResponse<UserSummaryDto> listUsers(String q, String roleStr, String statusStr, PageRequestDto pageRequestDto) {
        Pageable pageable = pageRequestDto.toPageable(WHITELISTED_SORT_FIELDS, "createdAt", Sort.Direction.DESC);

        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.trim().toLowerCase() + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("fullName")), pattern);
                Predicate emailMatch = cb.like(cb.lower(root.get("email")), pattern);
                predicates.add(cb.or(nameMatch, emailMatch));
            }

            if (statusStr != null && !statusStr.isBlank()) {
                try {
                    User.Status status = User.Status.valueOf(statusStr.trim().toUpperCase());
                    predicates.add(cb.equal(root.get("status"), status));
                } catch (IllegalArgumentException e) {
                    throw ApiException.validationFailed("Invalid status filter: " + statusStr);
                }
            }

            if (roleStr != null && !roleStr.isBlank()) {
                try {
                    UserRole.Role role = UserRole.Role.valueOf(roleStr.trim().toUpperCase());
                    Subquery<Long> subquery = query.subquery(Long.class);
                    var roleRoot = subquery.from(UserRole.class);
                    subquery.select(roleRoot.get("id").get("userId"))
                            .where(
                                    cb.equal(roleRoot.get("id").get("userId"), root.get("id")),
                                    cb.equal(roleRoot.get("id").get("role"), role)
                            );
                    predicates.add(cb.exists(subquery));
                } catch (IllegalArgumentException e) {
                    throw ApiException.validationFailed("Invalid role filter: " + roleStr);
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<User> page = userRepository.findAll(spec, pageable);
        return PageResponse.of(page, user -> new UserSummaryDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                loadRoles(user.getId()),
                user.getStatus().name(),
                user.getCreatedAt()
        ));
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found with id " + id));

        return toDetailResponse(user);
    }

    @Transactional
    public UserSummaryDto createUser(UserPrincipal actor, AdminCreateUserRequest request, String ipHash) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw ApiException.conflict("Email is already registered");
        }

        // Creating an ADMIN or assigning permissions requires SUPER_ADMIN
        boolean creatingAdmin = request.role() == UserRole.Role.ADMIN;
        boolean assigningPermissions = request.permissions() != null && !request.permissions().isEmpty();
        if ((creatingAdmin || assigningPermissions) && !hasSuperAdmin(actor)) {
            throw ApiException.forbidden("SUPER_ADMIN permission is required to create an admin user or assign permissions");
        }

        String tempPassword = generateTempPassword();
        String passwordHash = passwordEncoder.encode(tempPassword);

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordHash)
                .fullName(request.fullName().trim())
                .phone(request.phone() != null ? request.phone().trim() : null)
                .status(User.Status.ACTIVE)
                .mustChangePassword(true)
                .failedLoginCount(0)
                .build();

        User savedUser = userRepository.save(user);

        // Assign Role
        UserRole userRole = UserRole.builder()
                .id(new UserRole.UserRoleId(savedUser.getId(), request.role()))
                .build();
        userRoleRepository.save(userRole);

        // If trainer, create TrainerProfile
        if (request.role() == UserRole.Role.TRAINER) {
            TrainerProfile profile = TrainerProfile.builder()
                    .userId(savedUser.getId())
                    .ratePerSessionPaise(request.ratePerSessionPaise() != null ? request.ratePerSessionPaise() : 0L)
                    .active(true)
                    .build();
            trainerProfileRepository.save(profile);
        }

        // If admin with permissions, assign permissions
        if (request.role() == UserRole.Role.ADMIN && request.permissions() != null) {
            Instant now = clock.instant();
            for (String perm : request.permissions()) {
                if (perm != null && !perm.isBlank()) {
                    AdminPermission adminPerm = AdminPermission.builder()
                            .id(new AdminPermission.AdminPermissionId(savedUser.getId(), perm.trim()))
                            .grantedBy(actor.getId())
                            .grantedAt(now)
                            .build();
                    adminPermissionRepository.save(adminPerm);
                }
            }
        }

        // Emit outbox event user.created with tempPassword encrypted in payload_secret (never plain in payload or logged)
        outboxService.appendWithSecret(
                "USER",
                savedUser.getId(),
                "user.created",
                Map.of(
                        "userId", savedUser.getId(),
                        "role", request.role().name(),
                        "tempPasswordDeliveredVia", "email"
                ),
                Map.of("tempPassword", tempPassword)
        );

        log.info("Admin created user successfully with userId={}, role={}", savedUser.getId(), request.role());

        // Audit log action without sensitive keys
        Map<String, Object> afterSummary = new HashMap<>();
        afterSummary.put("userId", savedUser.getId());
        afterSummary.put("email", savedUser.getEmail());
        afterSummary.put("role", request.role().name());
        adminActionLogger.logAction(
                actor.getId(),
                "USER_CREATE",
                "USER",
                savedUser.getId(),
                null,
                toJson(afterSummary),
                ipHash
        );

        return new UserSummaryDto(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getFullName(),
                savedUser.getPhone(),
                List.of(request.role().name()),
                savedUser.getStatus().name(),
                savedUser.getCreatedAt()
        );
    }

    @Transactional
    public AdminUserDetailResponse updateUser(UserPrincipal actor, Long id, AdminUpdateUserRequest request, String ipHash) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found with id " + id));

        if (!user.getVersion().equals(request.version())) {
            throw ApiException.optimisticLock("User was modified concurrently");
        }

        Map<String, Object> beforeSummary = new HashMap<>();
        beforeSummary.put("fullName", user.getFullName());
        beforeSummary.put("phone", user.getPhone());

        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone().isBlank() ? null : request.phone().trim());
        }

        User savedUser = userRepository.save(user);

        Map<String, Object> afterSummary = new HashMap<>();
        afterSummary.put("fullName", savedUser.getFullName());
        afterSummary.put("phone", savedUser.getPhone());

        adminActionLogger.logAction(
                actor.getId(),
                "USER_UPDATE",
                "USER",
                savedUser.getId(),
                toJson(beforeSummary),
                toJson(afterSummary),
                ipHash
        );

        return toDetailResponse(savedUser);
    }

    @Transactional
    public void suspendUser(UserPrincipal actor, Long id, String ipHash) {
        if (actor.getId().equals(id)) {
            throw ApiException.conflict("Cannot suspend your own account");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found with id " + id));

        // Check if target is a SUPER_ADMIN
        boolean isSuperAdmin = adminPermissionRepository.findByIdUserId(id).stream()
                .anyMatch(ap -> PermissionChecker.SUPER_ADMIN.equalsIgnoreCase(ap.getId().getPermission()));

        if (isSuperAdmin) {
            long superAdminCount = adminPermissionRepository.countActiveUsersWithPermission(PermissionChecker.SUPER_ADMIN);
            if (superAdminCount <= 1) {
                throw ApiException.conflict("Cannot suspend the last SUPER_ADMIN");
            }
        }

        Map<String, Object> beforeSummary = Map.of("status", user.getStatus().name());

        user.setStatus(User.Status.SUSPENDED);
        userRepository.save(user);

        refreshTokenRevoker.revokeAllForUser(id);

        outboxService.append(
                "USER",
                id,
                "user.suspended",
                Map.of("userId", id)
        );

        Map<String, Object> afterSummary = Map.of("status", User.Status.SUSPENDED.name());
        adminActionLogger.logAction(
                actor.getId(),
                "USER_SUSPEND",
                "USER",
                id,
                toJson(beforeSummary),
                toJson(afterSummary),
                ipHash
        );

        log.info("User id {} suspended by admin id {}", id, actor.getId());
    }

    @Transactional
    public void reactivateUser(UserPrincipal actor, Long id, String ipHash) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found with id " + id));

        Map<String, Object> beforeSummary = Map.of("status", user.getStatus().name());

        user.setStatus(User.Status.ACTIVE);
        userRepository.save(user);

        Map<String, Object> afterSummary = Map.of("status", User.Status.ACTIVE.name());
        adminActionLogger.logAction(
                actor.getId(),
                "USER_REACTIVATE",
                "USER",
                id,
                toJson(beforeSummary),
                toJson(afterSummary),
                ipHash
        );

        log.info("User id {} reactivated by admin id {}", id, actor.getId());
    }

    @Transactional
    public List<String> replacePermissions(UserPrincipal actor, Long id, AdminPermissionsRequest request, String ipHash) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found with id " + id));

        if (!userRoleRepository.existsByIdUserIdAndIdRole(id, UserRole.Role.ADMIN)) {
            throw ApiException.validationFailed("Permissions can only be assigned to users with role ADMIN");
        }

        List<AdminPermission> currentPerms = adminPermissionRepository.findByIdUserId(id);
        boolean hadSuperAdmin = currentPerms.stream()
                .anyMatch(ap -> PermissionChecker.SUPER_ADMIN.equalsIgnoreCase(ap.getId().getPermission()));

        boolean willHaveSuperAdmin = request.permissions().stream()
                .anyMatch(p -> PermissionChecker.SUPER_ADMIN.equalsIgnoreCase(p.trim()));

        // Cannot remove your own SUPER_ADMIN if you are the last one
        if (hadSuperAdmin && !willHaveSuperAdmin) {
            long superAdminCount = adminPermissionRepository.countActiveUsersWithPermission(PermissionChecker.SUPER_ADMIN);
            if (superAdminCount <= 1) {
                throw ApiException.conflict("Cannot remove SUPER_ADMIN: at least one active super administrator must remain");
            }
        }

        List<String> oldPermList = currentPerms.stream().map(ap -> ap.getId().getPermission()).toList();

        if (!currentPerms.isEmpty()) {
            adminPermissionRepository.deleteAll(currentPerms);
            adminPermissionRepository.flush();
        }

        Instant now = clock.instant();
        List<String> newPermList = new ArrayList<>();
        for (String perm : request.permissions()) {
            if (perm != null && !perm.isBlank()) {
                String cleanPerm = perm.trim().toUpperCase();
                adminPermissionRepository.save(AdminPermission.builder()
                        .id(new AdminPermission.AdminPermissionId(id, cleanPerm))
                        .grantedBy(actor.getId())
                        .grantedAt(now)
                        .build());
                newPermList.add(cleanPerm);
            }
        }

        adminActionLogger.logAction(
                actor.getId(),
                "PERMISSIONS_UPDATE",
                "USER",
                id,
                toJson(Map.of("permissions", oldPermList)),
                toJson(Map.of("permissions", newPermList)),
                ipHash
        );

        log.info("Permissions updated for admin user id {} by actor id {}", id, actor.getId());
        return newPermList;
    }

    private boolean hasSuperAdmin(UserPrincipal actor) {
        return actor.getPermissions() != null && actor.getPermissions().contains(PermissionChecker.SUPER_ADMIN);
    }

    private List<String> loadRoles(Long userId) {
        List<UserRole> roles = userRoleRepository.findByIdUserId(userId);
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        return roles.stream().map(r -> r.getId().getRole().name()).toList();
    }

    private AdminUserDetailResponse toDetailResponse(User user) {
        List<String> roles = loadRoles(user.getId());
        List<String> permissions = adminPermissionRepository.findByIdUserId(user.getId()).stream()
                .map(p -> p.getId().getPermission())
                .toList();

        return new AdminUserDetailResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getDateOfBirth(),
                roles,
                permissions,
                user.getStatus().name(),
                user.getMustChangePassword(),
                user.getFailedLoginCount(),
                user.getLockedUntil(),
                user.getDeletedAt(),
                user.getVersion(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(TEMP_PW_CHARS.charAt(secureRandom.nextInt(TEMP_PW_CHARS.length())));
        }
        return sb.toString();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit json", e);
            return "{}";
        }
    }
}
