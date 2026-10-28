package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.tools.dto.GetClassDetailsArgs;
import com.fitflow.fitnessclass.FitnessClassService;
import com.fitflow.fitnessclass.dto.ClassDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetClassDetailsTool implements Tool {

    private final FitnessClassService fitnessClassService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "get_class_details";
    }

    @Override
    public String description() {
        return "Get details and descriptions for a specific fitness class by classId.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "classId", Map.of("type", "integer", "description", "ID of the fitness class")
                ),
                "required", java.util.List.of("classId")
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
        GetClassDetailsArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), GetClassDetailsArgs.class);
            if (args.classId() == null) {
                return ToolResult.error("INVALID_ARGUMENTS", "classId is required");
            }
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        try {
            ClassDetailResponse details = fitnessClassService.getClassById(args.classId(), false);
            Map<String, Object> data = Map.of(
                    "classId", details.id(),
                    "name", details.name(),
                    "description", details.description() != null ? details.description() : "",
                    "category", details.category(),
                    "intensity", details.intensity().name(),
                    "impactLevel", details.impactLevel().name(),
                    "durationMinutes", details.durationMinutes()
            );
            String summary = String.format("Class '%s' (%s, %s, %d mins)", details.name(), details.category(), details.intensity(), details.durationMinutes());
            return ToolResult.ok(data, summary);
        } catch (Exception e) {
            return ToolResult.error("CLASS_NOT_FOUND", "Class not found: " + e.getMessage());
        }
    }
}
