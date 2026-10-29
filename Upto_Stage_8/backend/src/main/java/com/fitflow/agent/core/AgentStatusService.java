package com.fitflow.agent.core;

import com.fitflow.agent.LlmKeyProvider;
import com.fitflow.agent.dto.AgentStatusResponse;
import com.fitflow.settings.entity.AppSetting;
import com.fitflow.settings.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AgentStatusService {

    public static final String SETTING_AI_MODEL = "ai_model";

    private final LlmKeyProvider llmKeyProvider;
    private final AppSettingRepository appSettingRepository;

    @Value("${fitflow.ai.default-model:gemini-1.5-flash}")
    private String defaultModel;

    public AgentStatusResponse getStatus() {
        Optional<String> keyOpt = llmKeyProvider.currentKey();
        boolean configured = keyOpt.isPresent() && !keyOpt.get().isBlank();
        String mode = configured ? "live" : "mock";

        String model = appSettingRepository.findById(SETTING_AI_MODEL)
                .map(AppSetting::getValue)
                .filter(v -> !v.isBlank())
                .orElse(defaultModel);

        return new AgentStatusResponse(mode, model, configured);
    }
}
