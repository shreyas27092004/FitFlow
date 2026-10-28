package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.dto.PendingActionProposal;
import com.fitflow.agent.service.PendingActionService;
import com.fitflow.agent.tools.dto.ProposeProfileSaveArgs;
import com.fitflow.common.error.ApiException;
import com.fitflow.user.AgeService;
import com.fitflow.user.UserService;
import com.fitflow.user.dto.UserProfileResponse;
import com.fitflow.wellness.dto.FitnessProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProposeProfileSaveTool implements Tool {

    private final UserService userService;
    private final AgeService ageService;
    private final PendingActionService pendingActionService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "propose_profile_save";
    }

    @Override
    public String description() {
        return "Propose saving the member's wellness and fitness profile. Creates a pending action requiring human confirmation.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "profileDraft", Map.of("type", "object", "description", "The draft FitnessProfile object")
                ),
                "required", java.util.List.of("profileDraft")
        );
    }

    @Override
    public boolean isReadOnly() {
        return false;
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
        ProposeProfileSaveArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), ProposeProfileSaveArgs.class);
            if (args.profileDraft() == null) {
                return ToolResult.error("INVALID_ARGUMENTS", "profileDraft is required");
            }
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        try {
            // Dry run: age check 18+ via UserService DTO
            UserProfileResponse user = userService.getProfile(context.userId());

            if (!ageService.is18Plus(user.dateOfBirth())) {
                return ToolResult.error("AGE_RESTRICTED", "Must be at least 18 years old to save wellness profile");
            }

            FitnessProfile draft = args.profileDraft();
            String summary = String.format("Save wellness profile: Experience=%s, Goals=%s, Sessions/week=%d, Injuries=%d, Conditions=%d, Clearance attested=%s.",
                    draft.experienceLevel(),
                    draft.goals(),
                    draft.sessionsPerWeekPlanned() != null ? draft.sessionsPerWeekPlanned() : 0,
                    draft.injuries() != null ? draft.injuries().size() : 0,
                    draft.conditions() != null ? draft.conditions().size() : 0,
                    draft.isMedicalClearanceAttested());

            PendingActionProposal action = pendingActionService.propose(
                    context.conversationId(),
                    context.userId(),
                    name(),
                    args,
                    summary
            );

            Map<String, Object> data = Map.of(
                    "pendingActionId", action.id(),
                    "summary", summary,
                    "status", "PENDING"
            );

            return ToolResult.ok(data, summary);
        } catch (Exception e) {
            return ToolResult.error("VALIDATION_ERROR", "Dry run failed: " + e.getMessage());
        }
    }
}
