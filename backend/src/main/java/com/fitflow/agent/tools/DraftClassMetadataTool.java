package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fitflow.agent.tools.dto.DraftClassMetadataArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DraftClassMetadataTool implements Tool {

    private final ObjectMapper objectMapper;
    private final Map<String, MetRange> metRanges = new HashMap<>();

    public record MetRange(double min, double max) {}

    @jakarta.annotation.PostConstruct
    public void initMetRanges() {
        try {
            ClassPathResource res = new ClassPathResource("agents/met-ranges.yml");
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            try (InputStream is = res.getInputStream()) {
                Map<String, Object> root = yamlMapper.readValue(is, Map.class);
                Map<String, Map<String, Object>> rangesMap = (Map<String, Map<String, Object>>) root.get("met_ranges");
                if (rangesMap != null) {
                    for (Map.Entry<String, Map<String, Object>> e : rangesMap.entrySet()) {
                        double min = ((Number) e.getValue().get("min")).doubleValue();
                        double max = ((Number) e.getValue().get("max")).doubleValue();
                        metRanges.put(e.getKey().toUpperCase(Locale.ROOT), new MetRange(min, max));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not load agents/met-ranges.yml, using fallback defaults: {}", e.getMessage());
            metRanges.put("YOGA", new MetRange(2.0, 4.0));
            metRanges.put("PILATES", new MetRange(2.5, 4.5));
            metRanges.put("HIIT", new MetRange(7.0, 12.0));
            metRanges.put("STRENGTH", new MetRange(3.5, 7.0));
            metRanges.put("CARDIO", new MetRange(5.0, 10.0));
            metRanges.put("DANCE", new MetRange(4.0, 8.0));
            metRanges.put("MOBILITY", new MetRange(2.0, 3.5));
            metRanges.put("BOXING", new MetRange(6.0, 12.0));
        }
    }

    @Override
    public String name() {
        return "draft_class_metadata";
    }

    @Override
    public String description() {
        return "Draft and validate fitness class metadata (category, intensity, impact level, MET value, contraindications, capacity, duration).";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("name", "description"),
                "properties", Map.of(
                        "name", Map.of("type", "string", "description", "Class name"),
                        "description", Map.of("type", "string", "description", "Class description")
                )
        );
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public Set<String> requiredRoles() {
        return Set.of("ADMIN");
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of("CLASS_EDIT");
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> rawArgs) {
        DraftClassMetadataArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), DraftClassMetadataArgs.class);
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        if (args.name() == null || args.name().isBlank()) {
            return ToolResult.error("INVALID_ARGUMENTS", "name is required");
        }

        // Return guidance to model for crafting the draft
        Map<String, Object> data = Map.of(
                "name", args.name().trim(),
                "description", args.description() != null ? args.description().trim() : "",
                "allowedCategories", List.of("YOGA", "PILATES", "HIIT", "STRENGTH", "CARDIO", "DANCE", "MOBILITY", "BOXING"),
                "allowedIntensities", List.of("LOW", "MEDIUM", "HIGH"),
                "allowedImpactLevels", List.of("LOW", "MEDIUM", "HIGH"),
                "metRanges", metRanges
        );

        String summary = String.format("Ready to draft class '%s'. Validate draft attributes against allowed ranges.", args.name());
        return ToolResult.ok(data, summary);
    }

    public List<String> validateDraft(Map<String, Object> draft) {
        List<String> errors = new ArrayList<>();
        if (draft == null || draft.isEmpty()) {
            errors.add("Draft cannot be null or empty");
            return errors;
        }

        String cat = draft.get("category") != null ? draft.get("category").toString().toUpperCase(Locale.ROOT) : null;
        if (cat == null || !metRanges.containsKey(cat)) {
            errors.add("Invalid category '" + cat + "'. Allowed: " + metRanges.keySet());
        }

        String intensity = draft.get("intensity") != null ? draft.get("intensity").toString().toUpperCase(Locale.ROOT) : null;
        if (intensity == null || (!"LOW".equals(intensity) && !"MEDIUM".equals(intensity) && !"HIGH".equals(intensity))) {
            errors.add("Invalid intensity '" + intensity + "'. Allowed: LOW, MEDIUM, HIGH");
        }

        String impact = draft.get("impactLevel") != null ? draft.get("impactLevel").toString().toUpperCase(Locale.ROOT) : null;
        if (impact == null || (!"LOW".equals(impact) && !"MEDIUM".equals(impact) && !"HIGH".equals(impact))) {
            errors.add("Invalid impactLevel '" + impact + "'. Allowed: LOW, MEDIUM, HIGH");
        }

        if (cat != null && metRanges.containsKey(cat)) {
            MetRange range = metRanges.get(cat);
            Number metNum = (Number) draft.get("metValue");
            if (metNum == null) {
                errors.add("metValue is required");
            } else {
                double met = metNum.doubleValue();
                if (met < range.min() || met > range.max()) {
                    errors.add(String.format("metValue %.1f out of range for category %s (must be between %.1f and %.1f)",
                            met, cat, range.min(), range.max()));
                }
            }
        }

        Number minAge = (Number) draft.get("minAge");
        if (minAge != null && minAge.intValue() < 14) {
            errors.add("minAge must be at least 14");
        }

        Number cap = (Number) draft.get("defaultCapacity");
        if (cap != null && (cap.intValue() < 1 || cap.intValue() > 200)) {
            errors.add("defaultCapacity must be between 1 and 200");
        }

        Number dur = (Number) draft.get("durationMinutes");
        if (dur != null && (dur.intValue() < 15 || dur.intValue() > 180)) {
            errors.add("durationMinutes must be between 15 and 180");
        }

        return errors;
    }
}
