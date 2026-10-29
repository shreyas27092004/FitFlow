package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.wellness.CalorieService;
import com.fitflow.wellness.dto.NutritionNumbersResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetCalorieNumbersTool implements Tool {

    private final CalorieService calorieService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "get_calorie_numbers";
    }

    @Override
    public String description() {
        return "Get daily energy expenditure numbers (BMR, TDEE, target calories) calculated deterministically using Mifflin-St Jeor.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of()
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
        try {
            NutritionNumbersResponse res = calorieService.getNutritionNumbers(context.userId());

            Map<String, Object> data = new HashMap<>();
            data.put("available", res.available());
            data.put("reason", res.reason());
            data.put("bmr", res.bmr());
            data.put("tdee", res.tdee());
            data.put("targetCalories", res.targetCalories());
            data.put("floorApplied", res.floorApplied());
            data.put("notes", res.notes());
            data.put("disclaimer", res.disclaimer());

            String summary;
            if (res.available()) {
                summary = String.format("Daily calorie numbers: BMR %d kcal, TDEE %d kcal, target %d kcal. %s",
                        res.bmr(), res.tdee(), res.targetCalories(), res.disclaimer());
            } else {
                summary = String.format("Calorie estimates unavailable: %s. %s", res.reason(), res.disclaimer());
            }

            return ToolResult.ok(data, summary);
        } catch (Exception e) {
            log.error("Failed to get calorie numbers: {}", e.getMessage(), e);
            return ToolResult.error("EXECUTION_FAILED", e.getMessage());
        }
    }
}
