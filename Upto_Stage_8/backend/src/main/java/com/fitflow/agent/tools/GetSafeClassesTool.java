package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.tools.dto.GetSafeClassesArgs;
import com.fitflow.common.error.ApiException;
import com.fitflow.wellness.WellnessProfileService;
import com.fitflow.wellness.dto.SafeClassesResponse;
import com.fitflow.wellness.dto.SafeSessionItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetSafeClassesTool implements Tool {

    private final WellnessProfileService wellnessProfileService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "get_safe_classes";
    }

    @Override
    public String description() {
        return "Get eligible and avoid class sessions based on safety screening and health profile.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "from", Map.of("type", "string", "description", "Start ISO timestamp (default now)"),
                        "to", Map.of("type", "string", "description", "End ISO timestamp (default now + 7 days)")
                )
        );
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
        GetSafeClassesArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), GetSafeClassesArgs.class);
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        Instant from = args.from() != null ? args.from() : context.clock().instant();
        Instant to = args.to() != null ? args.to() : from.plusSeconds(7 * 24 * 3600);

        try {
            SafeClassesResponse response = wellnessProfileService.getSafeClasses(context.userId(), from, to, 0, 50);

            List<Map<String, Object>> eligibleList = response.eligible().stream()
                    .map(this::mapSafeItem)
                    .toList();

            List<Map<String, Object>> avoidList = response.avoid().stream()
                    .map(this::mapSafeItem)
                    .toList();

            Map<String, Object> data = Map.of(
                    "doctorCheckRecommended", response.doctorCheckRecommended(),
                    "disclaimer", response.disclaimer(),
                    "note", response.note() != null ? response.note() : "",
                    "eligible", eligibleList,
                    "avoid", avoidList
            );

            String summary = String.format("Found %d eligible sessions and %d avoided sessions. Doctor check recommended: %s",
                    eligibleList.size(), avoidList.size(), response.doctorCheckRecommended());

            return ToolResult.ok(data, summary);
        } catch (ApiException e) {
            return ToolResult.error(e.getErrorCode() != null ? e.getErrorCode().name() : "ERROR", e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("WELLNESS_ERROR", e.getMessage());
        }
    }

    private Map<String, Object> mapSafeItem(SafeSessionItem item) {
        return Map.of(
                "sessionId", item.session().id(),
                "className", item.session().className(),
                "category", item.session().category(),
                "startsAt", item.session().startsAt().toString(),
                "verdict", item.verdict().name(),
                "reasonCodes", item.reasonCodes(),
                "humanReasons", item.humanReasons()
        );
    }
}
