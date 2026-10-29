package com.fitflow.payment.settings.controller;

import com.fitflow.payment.settings.PaymentSettingsService;
import com.fitflow.payment.settings.dto.PaymentSettingsResponse;
import com.fitflow.payment.settings.dto.RazorpayTestResponse;
import com.fitflow.payment.settings.dto.SetRazorpayKeysRequest;
import com.fitflow.payment.settings.dto.SwitchGatewayRequest;
import com.fitflow.security.UserPrincipal;
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
@RequestMapping("/api/v1/admin/settings/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Settings", description = "Administrative configuration for payment gateways and Razorpay credentials")
public class AdminPaymentSettingsController {

    private final PaymentSettingsService paymentSettingsService;

    @GetMapping
    @PreAuthorize("@perm.hasAny('SETTINGS_EDIT', 'SUPER_ADMIN')")
    @Operation(summary = "Get current payment gateway configuration and masked Razorpay status")
    public ResponseEntity<PaymentSettingsResponse> getSettings() {
        return ResponseEntity.ok(paymentSettingsService.getSettings());
    }

    @PutMapping("/razorpay")
    @PreAuthorize("@perm.hasAny('SETTINGS_EDIT', 'SUPER_ADMIN')")
    @Operation(summary = "Configure runtime Razorpay API keys (encrypted at rest)")
    public ResponseEntity<PaymentSettingsResponse> setRazorpayKeys(
            @AuthenticationPrincipal UserPrincipal actor,
            @Valid @RequestBody SetRazorpayKeysRequest request,
            HttpServletRequest servletRequest) {

        String ipHash = hashIp(extractClientIp(servletRequest));
        PaymentSettingsResponse response = paymentSettingsService.setRazorpayKeys(actor.getId(), request, ipHash);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/razorpay")
    @PreAuthorize("@perm.hasAny('SETTINGS_EDIT', 'SUPER_ADMIN')")
    @Operation(summary = "Delete Razorpay credentials and fallback to MOCK if active")
    public ResponseEntity<Void> deleteRazorpayKeys(
            @AuthenticationPrincipal UserPrincipal actor,
            HttpServletRequest servletRequest) {

        String ipHash = hashIp(extractClientIp(servletRequest));
        paymentSettingsService.deleteRazorpayKeys(actor.getId(), ipHash);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/gateway")
    @PreAuthorize("@perm.hasAny('SETTINGS_EDIT', 'SUPER_ADMIN')")
    @Operation(summary = "Switch active payment gateway (MOCK or RAZORPAY)")
    public ResponseEntity<PaymentSettingsResponse> switchGateway(
            @AuthenticationPrincipal UserPrincipal actor,
            @Valid @RequestBody SwitchGatewayRequest request,
            HttpServletRequest servletRequest) {

        String ipHash = hashIp(extractClientIp(servletRequest));
        PaymentSettingsResponse response = paymentSettingsService.switchGateway(actor.getId(), request, ipHash);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/razorpay/test")
    @PreAuthorize("@perm.hasAny('SETTINGS_EDIT', 'SUPER_ADMIN')")
    @Operation(summary = "Test Razorpay connection using configured credentials")
    public ResponseEntity<RazorpayTestResponse> testRazorpay() {
        RazorpayTestResponse response = paymentSettingsService.testConnection();
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
