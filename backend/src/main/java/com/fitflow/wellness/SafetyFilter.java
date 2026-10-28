package com.fitflow.wellness;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.model.ClassIntensity;
import com.fitflow.fitnessclass.model.ContraindicationTag;
import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.InjuryDto;
import com.fitflow.wellness.dto.SafetyVerdict;
import com.fitflow.wellness.dto.ScreeningResult;
import com.fitflow.wellness.model.HealthCondition;
import com.fitflow.wellness.model.SafetyVerdictType;
import com.fitflow.wellness.model.ScreeningStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SafetyFilter {

    private final SafetyRuleRegistry ruleRegistry;
    private final ScreeningService screeningService;
    private final ObjectMapper objectMapper;

    public SafetyVerdict evaluate(FitnessProfile profile, FitnessClass classMetadata) {
        if (classMetadata == null) {
            return new SafetyVerdict(SafetyVerdictType.BLOCKED, List.of("CLASS_NOT_FOUND"),
                    List.of("Class metadata is missing."), SafetyRuleRegistry.DISCLAIMER);
        }

        // Rule: Classes with metadata_reviewed=false are BLOCKED with reason METADATA_UNREVIEWED
        if (!Boolean.TRUE.equals(classMetadata.getMetadataReviewed())) {
            return new SafetyVerdict(
                    SafetyVerdictType.BLOCKED,
                    List.of("METADATA_UNREVIEWED"),
                    List.of("Not suggested for now: class metadata has not been reviewed by our fitness team yet."),
                    SafetyRuleRegistry.DISCLAIMER
            );
        }

        if (profile == null) {
            return new SafetyVerdict(SafetyVerdictType.ALLOWED, List.of(), List.of(), SafetyRuleRegistry.DISCLAIMER);
        }

        ScreeningResult screening = screeningService.evaluate(profile);
        List<String> reasonCodes = new ArrayList<>();
        List<String> humanReasons = new ArrayList<>();

        // Parse class contraindication tags
        Set<String> classTags = parseClassTags(classMetadata.getContraindicationTags());
        ClassIntensity classIntensity = classMetadata.getIntensity();

        // RED_FLAG without medicalClearanceAttested:
        // effective cap LOW and effective avoid set = EVERY tag in the vocabulary
        // (so only LOW intensity classes with no tags remain)
        if (screening.status() == ScreeningStatus.RED_FLAG && !profile.isMedicalClearanceAttested()) {
            boolean blocked = false;
            if (classIntensity != null && classIntensity != ClassIntensity.LOW) {
                blocked = true;
                reasonCodes.add("INTENSITY_CAP:LOW(RED_FLAG_NO_CLEARANCE)");
                humanReasons.add("Please check with a doctor before starting. We are only showing very gentle options.");
            }
            if (!classTags.isEmpty()) {
                blocked = true;
                for (String tag : classTags) {
                    reasonCodes.add("TAG_CONFLICT:" + tag + "(RED_FLAG_NO_CLEARANCE)");
                }
                humanReasons.add("Please check with a doctor before starting. We are only showing very gentle options with no intense movement tags.");
            }

            if (blocked) {
                return new SafetyVerdict(SafetyVerdictType.BLOCKED, reasonCodes, humanReasons, SafetyRuleRegistry.DISCLAIMER);
            } else {
                return new SafetyVerdict(
                        SafetyVerdictType.CAUTION,
                        List.of("RED_FLAG_NO_CLEARANCE_GENTLE"),
                        List.of("Please check with a doctor before starting. We are only showing very gentle options."),
                        SafetyRuleRegistry.DISCLAIMER
                );
            }
        }

        // Standard combination of avoid tags and lowest cap among matched rules
        Set<String> avoidTags = new HashSet<>();
        Map<String, String> tagSourceContext = new HashMap<>(); // tag -> context e.g. "KNEE_MODERATE"
        ClassIntensity lowestCap = null;
        String capSourceContext = null;

        // Process injuries
        if (profile.injuries() != null) {
            for (InjuryDto injury : profile.injuries()) {
                SafetyRuleRegistry.RuleDefinition rule = ruleRegistry.getRule(injury.area(), injury.severity());
                if (rule != null) {
                    String context = injury.area().name() + "_" + injury.severity().name();
                    for (String tag : rule.avoidTags()) {
                        avoidTags.add(tag);
                        tagSourceContext.putIfAbsent(tag, context);
                    }
                    if (rule.intensityCap() != null) {
                        if (lowestCap == null || rule.intensityCap().ordinal() < lowestCap.ordinal()) {
                            lowestCap = rule.intensityCap();
                            capSourceContext = context;
                        }
                    }
                }
            }
        }

        // Process conditions
        if (profile.conditions() != null) {
            for (HealthCondition condition : profile.conditions()) {
                SafetyRuleRegistry.RuleDefinition rule = ruleRegistry.getRule(condition);
                if (rule != null) {
                    String context = condition.name();
                    for (String tag : rule.avoidTags()) {
                        avoidTags.add(tag);
                        tagSourceContext.putIfAbsent(tag, context);
                    }
                    if (rule.intensityCap() != null) {
                        if (lowestCap == null || rule.intensityCap().ordinal() < lowestCap.ordinal()) {
                            lowestCap = rule.intensityCap();
                            capSourceContext = context;
                        }
                    }
                }
            }
        }

        // Check tag conflicts
        for (String tag : classTags) {
            if (avoidTags.contains(tag)) {
                String ctx = tagSourceContext.getOrDefault(tag, "HEALTH_RULE");
                reasonCodes.add("TAG_CONFLICT:" + tag + "(" + ctx + ")");
                humanReasons.add(buildHumanReasonForTag(tag, ctx));
            }
        }

        // Check intensity cap
        if (lowestCap != null && classIntensity != null && classIntensity.ordinal() > lowestCap.ordinal()) {
            reasonCodes.add("INTENSITY_CAP:" + lowestCap.name() + "(" + capSourceContext + ")");
            humanReasons.add(buildHumanReasonForCap(classIntensity.name(), lowestCap.name(), capSourceContext));
        }

        if (!reasonCodes.isEmpty()) {
            return new SafetyVerdict(SafetyVerdictType.BLOCKED, reasonCodes, humanReasons, SafetyRuleRegistry.DISCLAIMER);
        }

        if (screening.status() == ScreeningStatus.CAUTION || screening.status() == ScreeningStatus.RED_FLAG) {
            return new SafetyVerdict(
                    SafetyVerdictType.CAUTION,
                    List.of("SCREENING_CAUTION"),
                    List.of("Suitable with care. Please listen to your body and request modifications if needed."),
                    SafetyRuleRegistry.DISCLAIMER
            );
        }

        return new SafetyVerdict(SafetyVerdictType.ALLOWED, List.of(), List.of(), SafetyRuleRegistry.DISCLAIMER);
    }

    private Set<String> parseClassTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return Collections.emptySet();
        }
        try {
            List<String> list = objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
            Set<String> cleanSet = new HashSet<>();
            for (String item : list) {
                if (item != null) {
                    cleanSet.add(item.trim().toLowerCase());
                }
            }
            return cleanSet;
        } catch (Exception e) {
            log.warn("Failed to parse contraindication tags json: {}", tagsJson);
            return Collections.emptySet();
        }
    }

    private String buildHumanReasonForTag(String tag, String context) {
        String issueDesc = formatContextDescription(context);
        return switch (tag.toLowerCase()) {
            case "high_impact" -> "Not suggested for now: this class includes high-impact movement and you listed a " + issueDesc + ".";
            case "deep_flexion" -> "Not suggested for now: this class includes deep flexion movement and you listed a " + issueDesc + ".";
            case "heavy_lifting" -> "Not suggested for now: this class involves heavy lifting and you listed a " + issueDesc + ".";
            case "heated_room" -> "Not suggested for now: this class takes place in a heated room which is not advised with your " + issueDesc + ".";
            case "inversion" -> "Not suggested for now: this class includes inversion movements and you listed a " + issueDesc + ".";
            case "high_cardio" -> "Not suggested for now: this class involves high cardiovascular intensity which is not advised with your " + issueDesc + ".";
            case "balance_risk" -> "Not suggested for now: this class involves balance challenges and you listed a " + issueDesc + ".";
            default -> "Not suggested for now: this class contains movement tag " + tag + " which conflicts with your " + issueDesc + ".";
        };
    }

    private String buildHumanReasonForCap(String intensity, String cap, String context) {
        String issueDesc = formatContextDescription(context);
        return "Not suggested for now: this class intensity (" + intensity + ") exceeds your recommended limit of " + cap + " due to " + issueDesc + ".";
    }

    private String formatContextDescription(String context) {
        if (context == null) {
            return "health issue";
        }
        if (context.contains("_")) {
            String[] parts = context.split("_");
            return parts[0].toLowerCase() + " " + parts[1].toLowerCase();
        }
        return context.toLowerCase().replace('_', ' ');
    }
}
