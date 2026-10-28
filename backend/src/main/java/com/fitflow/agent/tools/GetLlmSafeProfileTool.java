package com.fitflow.agent.tools;

import com.fitflow.wellness.WellnessProfileService;
import com.fitflow.wellness.dto.LlmSafeProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetLlmSafeProfileTool implements Tool {

    private final WellnessProfileService wellnessProfileService;

    @Override
    public String name() {
        return "get_llm_safe_profile";
    }

    @Override
    public String description() {
        return "Get member's coded safe health profile if AI processing consent has been granted. Contains no free text or PII.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public Set<String> requiredRoles() {
        return Set.of("MEMBER");
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of();
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> rawArgs) {
        Optional<LlmSafeProfile> profileOpt = wellnessProfileService.getLlmSafeProfile(context.userId());

        if (profileOpt.isEmpty()) {
            return ToolResult.ok(
                    Map.of("consentGranted", false, "hasProfile", false),
                    "No AI processing consent granted or no health profile found."
            );
        }

        LlmSafeProfile p = profileOpt.get();
        Map<String, Object> data = Map.of(
                "consentGranted", true,
                "hasProfile", true,
                "experienceLevel", p.experienceLevel() != null ? p.experienceLevel().name() : "BEGINNER",
                "goals", p.goals() != null ? p.goals().stream().map(Enum::name).toList() : java.util.List.of(),
                "injuries", p.injuryCodes() != null ? p.injuryCodes() : java.util.List.of(),
                "conditions", p.conditionCodes() != null ? p.conditionCodes() : java.util.List.of(),
                "screeningStatus", p.screeningStatus() != null ? p.screeningStatus().name() : "CLEARED",
                "doctorCheckRecommended", p.doctorCheckRecommended()
        );

        String summary = String.format("Safe profile: Experience=%s, Screening=%s, DoctorCheck=%s",
                data.get("experienceLevel"), data.get("screeningStatus"), data.get("doctorCheckRecommended"));

        return ToolResult.ok(data, summary);
    }
}
