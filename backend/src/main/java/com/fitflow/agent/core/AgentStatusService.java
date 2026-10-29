package com.fitflow.agent.core;

import com.fitflow.agent.LlmKeyProvider;
import com.fitflow.agent.dto.AgentStatusResponse;
import com.fitflow.agent.dto.AgentStatusResponse.AgentInfoDto;
import com.fitflow.settings.entity.AppSetting;
import com.fitflow.settings.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AgentStatusService {

    public static final String SETTING_AI_MODEL = "ai_model";

    private final LlmKeyProvider llmKeyProvider;
    private final AppSettingRepository appSettingRepository;
    private final AgentUsageService agentUsageService;

    @Value("${fitflow.ai.default-model:gemini-1.5-flash}")
    private String defaultModel;

    public AgentStatusResponse getStatus(Long userId, Set<String> roles) {
        Optional<String> keyOpt = llmKeyProvider.currentKey();
        boolean configured = keyOpt.isPresent() && !keyOpt.get().isBlank();
        String mode = configured ? "gemini" : "mock";

        String model = appSettingRepository.findById(SETTING_AI_MODEL)
                .map(AppSetting::getValue)
                .filter(v -> !v.isBlank())
                .orElse(defaultModel);

        List<AgentInfoDto> agents = new ArrayList<>();
        if (roles != null) {
            if (roles.contains("MEMBER")) {
                agents.add(new AgentInfoDto("booking", "Booking Agent"));
                agents.add(new AgentInfoDto("membership", "Membership Agent"));
                agents.add(new AgentInfoDto("intake", "Fitness Intake Agent"));
                agents.add(new AgentInfoDto("safe_class_matcher", "Safe Class Matcher Agent"));
                agents.add(new AgentInfoDto("plan_builder", "Plan Builder Agent"));
                agents.add(new AgentInfoDto("wellness_coach", "Wellness Coach Agent"));
                agents.add(new AgentInfoDto("nutrition_numbers", "Nutrition Numbers Agent"));
            }
            if (roles.contains("TRAINER")) {
                agents.add(new AgentInfoDto("trainer_prep", "Trainer Prep Agent"));
                agents.add(new AgentInfoDto("trainer_schedule", "Trainer Schedule Agent"));
            }
            if (roles.contains("ADMIN")) {
                agents.add(new AgentInfoDto("retention", "Member Retention Agent"));
                agents.add(new AgentInfoDto("class_metadata", "Class Metadata Assistant"));
                agents.add(new AgentInfoDto("demand_forecast", "Demand Forecast Agent"));
            }
        }

        int remaining = agentUsageService.getRemainingDailyCalls(userId);

        return new AgentStatusResponse(mode, model, configured, agents, remaining);
    }
}
