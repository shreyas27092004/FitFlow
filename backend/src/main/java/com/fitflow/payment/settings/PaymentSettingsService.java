package com.fitflow.payment.settings;

import com.fitflow.admin.AdminActionLogger;
import com.fitflow.common.error.ApiException;
import com.fitflow.payment.gateway.razorpay.RazorpayClient;
import com.fitflow.payment.settings.dto.PaymentSettingsResponse;
import com.fitflow.payment.settings.dto.PaymentSettingsResponse.RazorpaySettingsDto;
import com.fitflow.payment.settings.dto.RazorpayTestResponse;
import com.fitflow.payment.settings.dto.SetRazorpayKeysRequest;
import com.fitflow.payment.settings.dto.SwitchGatewayRequest;
import com.fitflow.settings.entity.AppSetting;
import com.fitflow.settings.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSettingsService {

    public static final String GATEWAY_SETTING = "payments.gateway";
    public static final String ALLOW_LIVE_SETTING = "payments.allow-live";
    public static final String WEBHOOK_URL_HINT = "/api/v1/payments/webhooks/razorpay";

    private final RazorpayClient razorpayClient;
    private final AppSettingRepository appSettingRepository;
    private final AdminActionLogger adminActionLogger;
    private final Environment environment;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PaymentSettingsResponse getSettings() {
        String gateway = appSettingRepository.findById(GATEWAY_SETTING)
                .map(AppSetting::getValue)
                .filter(v -> !v.isBlank())
                .orElse("MOCK")
                .toUpperCase();

        boolean configured = razorpayClient.isConfigured();
        String keyId = razorpayClient.getKeyId().orElse(null);
        String keySecretMaskedLast4 = razorpayClient.getMaskedKeySecretLast4().orElse(null);
        boolean webhookSecretConfigured = razorpayClient.isWebhookSecretConfigured();

        String mode = null;
        if (keyId != null) {
            if (keyId.startsWith("rzp_live_")) {
                mode = "live";
            } else if (keyId.startsWith("rzp_test_")) {
                mode = "test";
            }
        }

        return new PaymentSettingsResponse(
                gateway,
                new RazorpaySettingsDto(
                        configured,
                        keyId,
                        keySecretMaskedLast4,
                        webhookSecretConfigured,
                        mode
                ),
                WEBHOOK_URL_HINT
        );
    }

    @Transactional
    public PaymentSettingsResponse setRazorpayKeys(Long actorId, SetRazorpayKeysRequest request, String ipHash) {
        String keyId = request.keyId().trim();
        String keySecret = request.keySecret().trim();
        String webhookSecret = request.webhookSecret() != null && !request.webhookSecret().isBlank()
                ? request.webhookSecret().trim()
                : null;

        String mode = keyId.startsWith("rzp_live_") ? "live" : "test";

        // LIVE KEY GUARD
        if ("live".equals(mode)) {
            boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
            if (!isProd) {
                throw ApiException.badRequest("Live Razorpay keys cannot be configured in non-prod environments");
            }

            boolean allowLive = appSettingRepository.findById(ALLOW_LIVE_SETTING)
                    .map(AppSetting::getValue)
                    .map(String::trim)
                    .map(Boolean::parseBoolean)
                    .orElse(false);

            if (!allowLive) {
                throw ApiException.badRequest("Live Razorpay keys require 'payments.allow-live=true' set by a SUPER_ADMIN");
            }
        }

        razorpayClient.setCredentials(keyId, keySecret, webhookSecret, actorId);

        // Safe audit log without any secrets
        adminActionLogger.logAction(
                actorId,
                "CONFIGURE_RAZORPAY_KEYS",
                "PAYMENT_SETTINGS",
                null,
                null,
                String.format("{\"keyId\":\"%s\",\"mode\":\"%s\",\"hasWebhookSecret\":%b}",
                        keyId, mode, webhookSecret != null),
                ipHash
        );

        return getSettings();
    }

    @Transactional
    public void deleteRazorpayKeys(Long actorId, String ipHash) {
        razorpayClient.deleteCredentials();

        // If active gateway was RAZORPAY, automatically fallback to MOCK
        Optional<AppSetting> gwSetting = appSettingRepository.findById(GATEWAY_SETTING);
        if (gwSetting.isPresent() && "RAZORPAY".equalsIgnoreCase(gwSetting.get().getValue())) {
            gwSetting.get().setValue("MOCK");
            appSettingRepository.save(gwSetting.get());

            adminActionLogger.logAction(
                    actorId,
                    "FALLBACK_GATEWAY_TO_MOCK",
                    "PAYMENT_SETTINGS",
                    null,
                    "{\"gateway\":\"RAZORPAY\"}",
                    "{\"gateway\":\"MOCK\"}",
                    ipHash
            );
        }

        adminActionLogger.logAction(
                actorId,
                "DELETE_RAZORPAY_KEYS",
                "PAYMENT_SETTINGS",
                null,
                null,
                null,
                ipHash
        );
    }

    @Transactional
    public PaymentSettingsResponse switchGateway(Long actorId, SwitchGatewayRequest request, String ipHash) {
        String targetGateway = request.gateway().trim().toUpperCase();

        if ("RAZORPAY".equals(targetGateway)) {
            if (!razorpayClient.isConfigured()) {
                throw ApiException.conflict("Razorpay keys are not configured");
            }

            Optional<Instant> lastTest = razorpayClient.getLastTestSuccessAt();
            if (lastTest.isEmpty() || Duration.between(lastTest.get(), clock.instant()).toMinutes() >= 10) {
                throw ApiException.conflict("Switching to RAZORPAY requires a successful test connection within the last 10 minutes");
            }
        }

        String beforeGateway = appSettingRepository.findById(GATEWAY_SETTING)
                .map(AppSetting::getValue)
                .orElse("MOCK")
                .toUpperCase();

        AppSetting setting = appSettingRepository.findById(GATEWAY_SETTING)
                .orElseGet(() -> AppSetting.builder().name(GATEWAY_SETTING).build());
        setting.setValue(targetGateway);
        appSettingRepository.save(setting);

        adminActionLogger.logAction(
                actorId,
                "SWITCH_PAYMENT_GATEWAY",
                "PAYMENT_SETTINGS",
                null,
                String.format("{\"gateway\":\"%s\"}", beforeGateway),
                String.format("{\"gateway\":\"%s\"}", targetGateway),
                ipHash
        );

        return getSettings();
    }

    public RazorpayTestResponse testConnection() {
        return razorpayClient.testConnection();
    }
}
