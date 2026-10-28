package com.fitflow.admin.controller;

import com.fitflow.admin.AdminUserService;
import com.fitflow.admin.dto.AdminCreateUserRequest;
import com.fitflow.admin.dto.AdminPermissionsRequest;
import com.fitflow.admin.dto.AdminUpdateUserRequest;
import com.fitflow.admin.dto.AdminUserDetailResponse;
import com.fitflow.admin.dto.UserSummaryDto;
import com.fitflow.common.pagination.PageRequestDto;
import com.fitflow.common.pagination.PageResponse;
import com.fitflow.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin Users", description = "Administrative user management and role assignment")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @PreAuthorize("@perm.has('USER_READ')")
    @Operation(summary = "List users with filters (q, role, status) and pagination")
    public ResponseEntity<PageResponse<UserSummaryDto>> listUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false, defaultValue = "createdAt,desc") String sort) {

        PageRequestDto pageRequestDto = new PageRequestDto(page, size, sort);
        PageResponse<UserSummaryDto> result = adminUserService.listUsers(q, role, status, pageRequestDto);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('USER_READ')")
    @Operation(summary = "Get detailed user view by id")
    public ResponseEntity<AdminUserDetailResponse> getUser(@PathVariable Long id) {
        AdminUserDetailResponse response = adminUserService.getUser(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("@perm.has('USER_EDIT')")
    @Operation(summary = "Create a new user (TRAINER or ADMIN) with temporary password")
    public ResponseEntity<UserSummaryDto> createUser(
            @AuthenticationPrincipal UserPrincipal actor,
            @Valid @RequestBody AdminCreateUserRequest request,
            HttpServletRequest servletRequest) {

        String ipHash = hashIp(extractClientIp(servletRequest));
        UserSummaryDto created = adminUserService.createUser(actor, request, ipHash);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@perm.has('USER_EDIT')")
    @Operation(summary = "Update user details with optimistic locking")
    public ResponseEntity<AdminUserDetailResponse> updateUser(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable Long id,
            @Valid @RequestBody AdminUpdateUserRequest request,
            HttpServletRequest servletRequest) {

        String ipHash = hashIp(extractClientIp(servletRequest));
        AdminUserDetailResponse updated = adminUserService.updateUser(actor, id, request, ipHash);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("@perm.has('USER_EDIT')")
    @Operation(summary = "Suspend an active user and revoke active tokens")
    public ResponseEntity<Map<String, String>> suspendUser(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable Long id,
            HttpServletRequest servletRequest) {

        String ipHash = hashIp(extractClientIp(servletRequest));
        adminUserService.suspendUser(actor, id, ipHash);
        return ResponseEntity.ok(Map.of("status", "SUSPENDED"));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("@perm.has('USER_EDIT')")
    @Operation(summary = "Reactivate a suspended user")
    public ResponseEntity<Map<String, String>> reactivateUser(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable Long id,
            HttpServletRequest servletRequest) {

        String ipHash = hashIp(extractClientIp(servletRequest));
        adminUserService.reactivateUser(actor, id, ipHash);
        return ResponseEntity.ok(Map.of("status", "ACTIVE"));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("@perm.has('SUPER_ADMIN')")
    @Operation(summary = "Replace administrative permissions for an admin user")
    public ResponseEntity<Map<String, Object>> replacePermissions(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable Long id,
            @Valid @RequestBody AdminPermissionsRequest request,
            HttpServletRequest servletRequest) {

        String ipHash = hashIp(extractClientIp(servletRequest));
        List<String> newPerms = adminUserService.replacePermissions(actor, id, request, ipHash);
        return ResponseEntity.ok(Map.of("userId", id, "permissions", newPerms));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    private String hashIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ip.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
