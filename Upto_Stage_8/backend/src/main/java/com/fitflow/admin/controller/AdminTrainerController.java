package com.fitflow.admin.controller;

import com.fitflow.admin.AdminTrainerService;
import com.fitflow.security.UserPrincipal;
import com.fitflow.trainer.dto.TrainerProfileResponse;
import com.fitflow.trainer.dto.UpdateTrainerRateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@RestController
@RequestMapping("/api/v1/admin/trainers")
@RequiredArgsConstructor
@Tag(name = "Admin Trainers", description = "Administrative trainer compensation management")
public class AdminTrainerController {

    private final AdminTrainerService adminTrainerService;

    @PatchMapping("/{id}/rate")
    @PreAuthorize("@perm.hasAny('PAYOUT_APPROVE', 'SUPER_ADMIN')")
    @Operation(summary = "Update trainer per-session pay rate in paise (affects future sessions)")
    public ResponseEntity<TrainerProfileResponse> updateTrainerRate(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable Long id,
            @Valid @RequestBody UpdateTrainerRateRequest request,
            HttpServletRequest servletRequest) {

        String ipHash = hashIp(extractClientIp(servletRequest));
        TrainerProfileResponse updated = adminTrainerService.updateTrainerRate(actor, id, request, ipHash);
        return ResponseEntity.ok(updated);
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
