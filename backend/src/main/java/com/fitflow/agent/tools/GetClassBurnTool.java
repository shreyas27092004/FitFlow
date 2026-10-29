package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.tools.dto.GetClassBurnArgs;
import com.fitflow.wellness.CalorieService;
import com.fitflow.wellness.dto.ClassBurnResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetClassBurnTool implements Tool {

    private final CalorieService calorieService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "get_class_burn";
    }

    @Override
    public String description() {
        return "Get estimated calories burned for a specific class based on MET value and member profile.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "required", java.util.List.of("classId"),
                "properties", Map.of(
                        "classId", Map.of("type", "integer", "description", "Class ID")
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
        GetClassBurnArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), GetClassBurnArgs.class);
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        if (args.classId() == null) {
            return ToolResult.error("INVALID_ARGUMENTS", "classId is required");
        }

        try {
            ClassBurnResponse res = calorieService.getClassBurn(context.userId(), args.classId());

            Map<String, Object> data = new HashMap<>();
            data.put("classId", args.classId());
            data.put("kcal", res.kcal());
            data.put("assumptions", res.assumptions());
            data.put("disclaimer", res.disclaimer());

            String summary = String.format("Estimated burn for class %d: %d kcal. %s Disclaimer: %s",
                    args.classId(), res.kcal(), res.assumptions(), res.disclaimer());

            return ToolResult.ok(data, summary);
        } catch (Exception e) {
            log.error("Failed to get class burn: {}", e.getMessage(), e);
            return ToolResult.error("EXECUTION_FAILED", e.getMessage());
        }
    }
}
