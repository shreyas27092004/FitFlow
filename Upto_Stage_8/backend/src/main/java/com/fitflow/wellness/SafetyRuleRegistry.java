package com.fitflow.wellness;

import com.fitflow.fitnessclass.model.ClassIntensity;
import com.fitflow.fitnessclass.model.ContraindicationTag;
import com.fitflow.wellness.dto.SuggestedModificationDto;
import com.fitflow.wellness.model.HealthCondition;
import com.fitflow.wellness.model.InjuryArea;
import com.fitflow.wellness.model.InjurySeverity;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class SafetyRuleRegistry {

    public static final String DISCLAIMER =
            "Wellness suggestions, not medical advice. Rules are illustrative and must be reviewed by a qualified professional before real use.";

    public record RuleDefinition(Set<String> avoidTags, ClassIntensity intensityCap) {}

    private final Map<InjuryArea, Map<InjurySeverity, RuleDefinition>> injuryRules = new HashMap<>();
    private final Map<HealthCondition, RuleDefinition> conditionRules = new HashMap<>();
    private final Map<InjuryArea, List<SuggestedModificationDto>> modificationRules = new HashMap<>();

    @PostConstruct
    public void init() {
        loadSafetyRules();
        loadModifications();
    }

    @SuppressWarnings("unchecked")
    private void loadSafetyRules() {
        try {
            ClassPathResource resource = new ClassPathResource("wellness/safety-rules.yml");
            Yaml yaml = new Yaml();
            Map<String, Object> root;
            try (InputStream is = resource.getInputStream()) {
                root = yaml.load(is);
            }

            if (root == null) {
                throw new IllegalStateException("safety-rules.yml is empty or could not be loaded");
            }

            // 1. Parse injuries
            Map<String, Object> injuriesMap = (Map<String, Object>) root.get("injuries");
            if (injuriesMap != null) {
                for (Map.Entry<String, Object> areaEntry : injuriesMap.entrySet()) {
                    InjuryArea area = InjuryArea.valueOf(areaEntry.getKey().toUpperCase());
                    Map<String, Object> severityMap = (Map<String, Object>) areaEntry.getValue();
                    Map<InjurySeverity, RuleDefinition> sevRules = new HashMap<>();

                    for (Map.Entry<String, Object> sevEntry : severityMap.entrySet()) {
                        InjurySeverity severity = InjurySeverity.valueOf(sevEntry.getKey().toUpperCase());
                        Map<String, Object> ruleData = (Map<String, Object>) sevEntry.getValue();

                        RuleDefinition ruleDef = parseAndValidateRule(ruleData, "injury " + area + " " + severity);
                        sevRules.put(severity, ruleDef);
                    }
                    injuryRules.put(area, Collections.unmodifiableMap(sevRules));
                }
            }

            // 2. Parse conditions
            Map<String, Object> conditionsMap = (Map<String, Object>) root.get("conditions");
            if (conditionsMap != null) {
                for (Map.Entry<String, Object> condEntry : conditionsMap.entrySet()) {
                    HealthCondition condition = HealthCondition.valueOf(condEntry.getKey().toUpperCase());
                    Map<String, Object> ruleData = (Map<String, Object>) condEntry.getValue();

                    RuleDefinition ruleDef = parseAndValidateRule(ruleData, "condition " + condition);
                    conditionRules.put(condition, ruleDef);
                }
            }

            log.info("Successfully loaded and validated {} injury safety rule sets and {} condition rules",
                    injuryRules.size(), conditionRules.size());

        } catch (Exception e) {
            log.error("Fatal failure loading safety-rules.yml: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to load and validate safety-rules.yml: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadModifications() {
        try {
            ClassPathResource resource = new ClassPathResource("wellness/modifications.yml");
            Yaml yaml = new Yaml();
            Map<String, Object> root;
            try (InputStream is = resource.getInputStream()) {
                root = yaml.load(is);
            }

            if (root != null) {
                Map<String, Object> modsMap = (Map<String, Object>) root.get("modifications");
                if (modsMap != null) {
                    for (Map.Entry<String, Object> entry : modsMap.entrySet()) {
                        InjuryArea area = InjuryArea.valueOf(entry.getKey().toUpperCase());
                        List<Map<String, String>> list = (List<Map<String, String>>) entry.getValue();
                        List<SuggestedModificationDto> dtos = new ArrayList<>();
                        for (Map<String, String> item : list) {
                            dtos.add(new SuggestedModificationDto(item.get("code"), item.get("label")));
                        }
                        modificationRules.put(area, Collections.unmodifiableList(dtos));
                    }
                }
            }
            log.info("Successfully loaded modifications for {} injury areas", modificationRules.size());
        } catch (Exception e) {
            log.error("Failed to load modifications.yml: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to load modifications.yml: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private RuleDefinition parseAndValidateRule(Map<String, Object> ruleData, String context) {
        List<String> rawAvoid = (List<String>) ruleData.get("avoid");
        Set<String> avoidTags = new HashSet<>();
        if (rawAvoid != null) {
            for (String tag : rawAvoid) {
                String cleanTag = tag.trim().toLowerCase();
                if (!ContraindicationTag.ALLOWED_VALUES.contains(cleanTag)) {
                    throw new IllegalStateException("Unknown contraindication tag in " + context + ": '" + tag + "'");
                }
                avoidTags.add(cleanTag);
            }
        }

        String rawCap = (String) ruleData.get("cap");
        ClassIntensity intensityCap = null;
        if (rawCap != null && !rawCap.isBlank()) {
            String cleanCap = rawCap.trim().toUpperCase();
            if (!ClassIntensity.ALLOWED_NAMES.contains(cleanCap)) {
                throw new IllegalStateException("Unknown intensity cap in " + context + ": '" + rawCap + "'");
            }
            intensityCap = ClassIntensity.valueOf(cleanCap);
        }

        return new RuleDefinition(Collections.unmodifiableSet(avoidTags), intensityCap);
    }

    public RuleDefinition getRule(InjuryArea area, InjurySeverity severity) {
        Map<InjurySeverity, RuleDefinition> map = injuryRules.get(area);
        return map != null ? map.get(severity) : null;
    }

    public RuleDefinition getRule(HealthCondition condition) {
        return conditionRules.get(condition);
    }

    public List<SuggestedModificationDto> getModifications(InjuryArea area) {
        return modificationRules.getOrDefault(area, Collections.emptyList());
    }
}
