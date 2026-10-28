package com.fitflow.settings;

import com.fitflow.admin.AdminActionLogger;
import com.fitflow.common.error.ApiException;
import com.fitflow.settings.dto.AiSettingsResponse;
import com.fitflow.settings.dto.AiTestResponse;
import com.fitflow.settings.dto.SetAiKeyRequest;
import com.fitflow.settings.dto.SetAiModelRequest;
import com.fitflow.settings.entity.AppSecret;
import com.fitflow.settings.entity.AppSetting;
import com.fitflow.settings.repository.AppSecretRepository;
import com.fitflow.settings.repository.AppSettingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AiSettingsService {

    public static final String GEMINI_SECRET_NAME = "gemini_api_key";
    public static final String AI_MODEL_SETTING_NAME = "ai_model";
    private static final Pattern MODEL_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{3,60}$");

    private final SecretsService secretsService;
    private final AppSecretRepository appSecretRepository;
    private final AppSettingRepository appSettingRepository;
    private final AiConnectionTester aiConnectionTester;
    private final AdminActionLogger adminActionLogger;
    private final Clock clock;
    private final String envGeminiApiKey;
    private final String defaultModel;

    public AiSettingsService(
            SecretsService secretsService,
            AppSecretRepository appSecretRepository,
            AppSettingRepository appSettingRepository,
            AiConnectionTester aiConnectionTester,
            AdminActionLogger adminActionLogger,
            Clock clock,
            @Value("${GEMINI_API_KEY:}") String envGeminiApiKey,
            @Value("${fitflow.ai.default-model:gemini-1.5-flash}") String defaultModel) {
        this.secretsService = secretsService;
        this.appSecretRepository = appSecretRepository;
        this.appSettingRepository = appSettingRepository;
        this.aiConnectionTester = aiConnectionTester;
        this.adminActionLogger = adminActionLogger;
        this.clock = clock;
        this.envGeminiApiKey = envGeminiApiKey;
        this.defaultModel = defaultModel;
    }

    @Transactional(readOnly = true)
    public AiSettingsResponse getSettings() {
        String model = getModel();

        // 1. Check database secret
        Optional<AppSecret> dbSecretOpt = appSecretRepository.findById(GEMINI_SECRET_NAME);
        if (dbSecretOpt.isPresent()) {
            Optional<String> decrypted = secretsService.get(GEMINI_SECRET_NAME);
            if (decrypted.isPresent() && !decrypted.get().isBlank()) {
                String masked = secretsService.masked(GEMINI_SECRET_NAME).orElse(null);
                AppSecret secret = dbSecretOpt.get();
                return new AiSettingsResponse(
                        true,
                        masked,
                        "database",
                        model,
                        "gemini",
                        secret.getUpdatedBy(),
                        secret.getUpdatedAt()
                );
            }
        }

        // 2. Check environment variable
        if (envGeminiApiKey != null && !envGeminiApiKey.isBlank()) {
            String masked = maskKey(envGeminiApiKey.trim());
            return new AiSettingsResponse(
                    true,
                    masked,
                    "environment",
                    model,
                    "gemini",
                    null,
                    null
            );
        }

        // 3. Fallback to mock
        return new AiSettingsResponse(
                false,
                null,
                "none",
                model,
                "mock",
                null,
                null
        );
    }

    @Transactional
    public AiSettingsResponse setApiKey(Long actorId, SetAiKeyRequest request, String ipHash) {
        validateKeyFormat(request.apiKey());

        secretsService.set(GEMINI_SECRET_NAME, request.apiKey().trim(), actorId);

        // Audit row without key
        adminActionLogger.logAction(
                actorId,
                "AI_KEY_UPDATE",
                "APP_SECRET",
                null,
                null,
                "{\"configured\":true}",
                ipHash
        );

        log.info("AI API key updated by admin id {}", actorId);
        return getSettings();
    }

    @Transactional
    public void deleteApiKey(Long actorId, String ipHash) {
        secretsService.delete(GEMINI_SECRET_NAME);

        adminActionLogger.logAction(
                actorId,
                "AI_KEY_DELETE",
                "APP_SECRET",
                null,
                null,
                "{\"configured\":false}",
                ipHash
        );

        log.info("AI API key deleted by admin id {}", actorId);
    }

    @Transactional
    public AiSettingsResponse setModel(Long actorId, SetAiModelRequest request, String ipHash) {
        if (request.model() == null || !MODEL_PATTERN.matcher(request.model().trim()).matches()) {
            throw ApiException.validationFailed("Model name must match pattern ^[a-zA-Z0-9._-]{3,60}$");
        }

        String newModel = request.model().trim();
        Instant now = clock.instant();

        AppSetting setting = appSettingRepository.findById(AI_MODEL_SETTING_NAME)
                .orElseGet(() -> AppSetting.builder()
                        .name(AI_MODEL_SETTING_NAME)
                        .createdAt(now)
                        .build());

        String oldModel = setting.getValue();
        setting.setValue(newModel);
        setting.setUpdatedBy(actorId);
        setting.setUpdatedAt(now);
        appSettingRepository.save(setting);

        adminActionLogger.logAction(
                actorId,
                "AI_MODEL_UPDATE",
                "APP_SETTING",
                null,
                oldModel != null ? "{\"model\":\"" + oldModel + "\"}" : null,
                "{\"model\":\"" + newModel + "\"}",
                ipHash
        );

        log.info("AI model updated to '{}' by admin id {}", newModel, actorId);
        return getSettings();
    }

    @Transactional(readOnly = true)
    public AiTestResponse testAi() {
        Optional<String> dbKey = secretsService.get(GEMINI_SECRET_NAME);
        String key = dbKey.filter(k -> !k.isBlank()).orElse(
                (envGeminiApiKey != null && !envGeminiApiKey.isBlank()) ? envGeminiApiKey.trim() : null
        );

        if (key == null) {
            return new AiTestResponse(true, "Mock mode: no key configured");
        }

        boolean ok = aiConnectionTester.testConnection(key);
        String message = aiConnectionTester.getTestMessage();
        return new AiTestResponse(ok, message);
    }

    private String getModel() {
        return appSettingRepository.findById(AI_MODEL_SETTING_NAME)
                .map(AppSetting::getValue)
                .filter(v -> !v.isBlank())
                .orElse(defaultModel);
    }

    private void validateKeyFormat(String apiKey) {
        if (apiKey == null
                || !apiKey.startsWith("AIza")
                || apiKey.length() < 20
                || apiKey.length() > 200
                || Pattern.compile("\\s").matcher(apiKey).find()) {
            throw ApiException.validationFailed("API key must start with 'AIza', contain no whitespace, and be between 20 and 200 characters");
        }
    }

    private String maskKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return key.length() <= 4 ? key : key.substring(key.length() - 4);
    }
}
