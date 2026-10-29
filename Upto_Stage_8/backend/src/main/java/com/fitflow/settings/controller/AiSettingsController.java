package com.fitflow.settings.controller;

import com.fitflow.security.UserPrincipal;
import com.fitflow.settings.AiSettingsService;
import com.fitflow.settings.dto.AiSettingsResponse;
import com.fitflow.settings.dto.AiTestResponse;
import com.fitflow.settings.dto.SetAiKeyRequest;
import com.fitflow.settings.dto.SetAiModelRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@RestController
@RequestMapping("/api/v1/admin/settings/ai")
@RequiredArgsConstructor
@Tag(name = "AI Settings", description = "Administrative configuration for AI models and runtime Gemini API keys")
public class AiSettingsController {

    private final AiSettingsService aiSettingsService;

    @GetMapping
    @PreAuthorize("@perm.hasAny('SETTINGS_EDIT', 'SUPER_ADMIN')")
    @Operation(summary = "Get current AI model configuration and masked key status")
    public ResponseEntity<AiSettingsResponse> getSettings() {
        AiSettingsResponse response = aiSettingsService.getSettings();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/key")
    @PreAuthorize("@perm.hasAny('SETTINGS_EDIT', 'SUPER_ADMIN')")
    @Operation(summary = "Configure runtime Gemini API key (encrypted at rest)")
    public ResponseEntity<AiSettingsResponse> setKey(
            @AuthenticationPrincipal UserPrincipal actor,
            @Valid @RequestBody SetAiKeyRequest request,
            HttpServletRequest servletRequest) {

        String ipHash = hashIp(extractClientIp(servletRequest));
        AiSettingsResponse response = aiSettingsService.setApiKey(actor.getId(), request, ipHash);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/key")
    @PreAuthorize("@perm.hasAny('SETTINGS_EDIT', 'SUPER_ADMIN')")
    @Operation(summary = "Remove runtime Gemini API key (reverts to mock mode unless env variable is set)")
    public ResponseEntity<Void> deleteKey(
            @AuthenticationPrincipal UserPrincipal actor,
            HttpServletRequest servletRequest) {

        String ipHash = hashIp(extractClientIp(servletRequest));
        aiSettingsService.deleteApiKey(actor.getId(), ipHash);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/model")
    @PreAuthorize("@perm.hasAny('SETTINGS_EDIT', 'SUPER_ADMIN')")
    @Operation(summary = "Configure active Gemini model name")
    public ResponseEntity<AiSettingsResponse> setModel(
            @AuthenticationPrincipal UserPrincipal actor,
            @Valid @RequestBody SetAiModelRequest request,
            HttpServletRequest servletRequest) {

        String ipHash = hashIp(extractClientIp(servletRequest));
        AiSettingsResponse response = aiSettingsService.setModel(actor.getId(), request, ipHash);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/test")
    @PreAuthorize("@perm.hasAny('SETTINGS_EDIT', 'SUPER_ADMIN')")
    @Operation(summary = "Test AI connection with current key configuration")
    public ResponseEntity<AiTestResponse> testAi() {
        AiTestResponse response = aiSettingsService.testAi();
        return ResponseEntity.ok(response);
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
