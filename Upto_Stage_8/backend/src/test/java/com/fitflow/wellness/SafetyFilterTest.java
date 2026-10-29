package com.fitflow.wellness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.model.ClassImpactLevel;
import com.fitflow.fitnessclass.model.ClassIntensity;
import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.InjuryDto;
import com.fitflow.wellness.dto.SafetyVerdict;
import com.fitflow.wellness.model.ExperienceLevel;
import com.fitflow.wellness.model.Goal;
import com.fitflow.wellness.model.HealthCondition;
import com.fitflow.wellness.model.InjuryArea;
import com.fitflow.wellness.model.InjurySeverity;
import com.fitflow.wellness.model.SafetyVerdictType;
import com.fitflow.wellness.model.Sex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetyFilterTest {

    private SafetyRuleRegistry ruleRegistry;
    private SafetyFilter safetyFilter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ruleRegistry = new SafetyRuleRegistry();
        ruleRegistry.init();
        ScreeningService screeningService = new ScreeningService();
        safetyFilter = new SafetyFilter(ruleRegistry, screeningService, objectMapper);
    }

    private FitnessClass createClass(String name, ClassIntensity intensity, List<String> tags, boolean reviewed) {
        try {
            String tagsJson = tags != null ? objectMapper.writeValueAsString(tags) : "[]";
            return FitnessClass.builder()
                    .name(name)
                    .category("STRENGTH")
                    .durationMinutes(45)
                    .intensity(intensity)
                    .impactLevel(ClassImpactLevel.LOW)
                    .metValue(new BigDecimal("5.0"))
                    .contraindicationTags(tagsJson)
                    .metadataReviewed(reviewed)
                    .createdBy(1L)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private FitnessProfile profileWithInjury(InjuryArea area, InjurySeverity severity) {
        return new FitnessProfile(
                ExperienceLevel.INTERMEDIATE,
                null,
                List.of(Goal.GENERAL_FITNESS),
                3,
                Collections.emptyList(),
                List.of(new InjuryDto(area, severity)),
                Collections.emptySet(),
                Collections.emptyMap(),
                false,
                new BigDecimal("170.0"),
                new BigDecimal("65.0"),
                Sex.MALE,
                null,
                1
        );
    }

    private FitnessProfile profileWithCondition(HealthCondition condition) {
        return new FitnessProfile(
                ExperienceLevel.INTERMEDIATE,
                null,
                List.of(Goal.GENERAL_FITNESS),
                3,
                Collections.emptyList(),
                Collections.emptyList(),
                Set.of(condition),
                Collections.emptyMap(),
                false,
                new BigDecimal("170.0"),
                new BigDecimal("65.0"),
                Sex.FEMALE,
                null,
                1
        );
    }

    @Test
    @DisplayName("Metadata unreviewed class is BLOCKED with METADATA_UNREVIEWED")
    void testUnreviewedMetadataBlocked() {
        FitnessClass unreviewedClass = createClass("Unreviewed Yoga", ClassIntensity.LOW, List.of(), false);
        FitnessProfile profile = profileWithInjury(InjuryArea.KNEE, InjurySeverity.MILD);

        SafetyVerdict verdict = safetyFilter.evaluate(profile, unreviewedClass);
        assertEquals(SafetyVerdictType.BLOCKED, verdict.verdict());
        assertTrue(verdict.reasonCodes().contains("METADATA_UNREVIEWED"));
        assertFalse(verdict.humanReasons().isEmpty());
    }

    // 27 cases: 9 injury areas x 3 severities
    @ParameterizedTest(name = "{0} {1} -> avoid {2}, cap {3}")
    @CsvSource({
            "KNEE, MILD, 'high_impact', ''",
            "KNEE, MODERATE, 'high_impact,deep_flexion', ''",
            "KNEE, SEVERE, 'high_impact,deep_flexion,heavy_lifting', 'LOW'",

            "ANKLE, MILD, '', ''",
            "ANKLE, MODERATE, 'high_impact,balance_risk', ''",
            "ANKLE, SEVERE, 'high_impact,balance_risk', 'LOW'",

            "HIP, MILD, 'deep_flexion', ''",
            "HIP, MODERATE, 'deep_flexion,high_impact', ''",
            "HIP, SEVERE, 'deep_flexion,high_impact,heavy_lifting', 'LOW'",

            "LOWER_BACK, MILD, 'heavy_lifting', ''",
            "LOWER_BACK, MODERATE, 'heavy_lifting,deep_flexion', ''",
            "LOWER_BACK, SEVERE, 'heavy_lifting,deep_flexion,high_impact,inversion', 'LOW'",

            "UPPER_BACK, MILD, 'inversion', ''",
            "UPPER_BACK, MODERATE, 'inversion,heavy_lifting', ''",
            "UPPER_BACK, SEVERE, 'inversion,heavy_lifting,high_impact', 'LOW'",

            "NECK, MILD, 'inversion', ''",
            "NECK, MODERATE, 'inversion,heavy_lifting', ''",
            "NECK, SEVERE, 'inversion,heavy_lifting,high_impact', 'LOW'",

            "SHOULDER, MILD, 'inversion', ''",
            "SHOULDER, MODERATE, 'inversion,heavy_lifting', ''",
            "SHOULDER, SEVERE, 'inversion,heavy_lifting', 'LOW'",

            "ELBOW, MILD, '', ''",
            "ELBOW, MODERATE, 'heavy_lifting', ''",
            "ELBOW, SEVERE, 'heavy_lifting,inversion', 'LOW'",

            "WRIST, MILD, '', ''",
            "WRIST, MODERATE, 'inversion,heavy_lifting', ''",
            "WRIST, SEVERE, 'inversion,heavy_lifting', 'LOW'"
    })
    @DisplayName("Assert avoid sets and intensity caps for all 27 injury combinations against literals")
    void testAll27InjuryRules(String areaStr, String severityStr, String expectedAvoidCsv, String expectedCapStr) {
        InjuryArea area = InjuryArea.valueOf(areaStr);
        InjurySeverity severity = InjurySeverity.valueOf(severityStr);

        SafetyRuleRegistry.RuleDefinition rule = ruleRegistry.getRule(area, severity);

        List<String> expectedAvoid = expectedAvoidCsv.isBlank()
                ? List.of()
                : List.of(expectedAvoidCsv.split(","));

        assertEquals(Set.copyOf(expectedAvoid), rule.avoidTags());

        if (expectedCapStr.isBlank()) {
            assertEquals(null, rule.intensityCap());
        } else {
            assertEquals(ClassIntensity.valueOf(expectedCapStr), rule.intensityCap());
        }

        // Test evaluation against a class with the avoided tag
        if (!expectedAvoid.isEmpty()) {
            FitnessClass conflictingClass = createClass("Tag Conflict Class", ClassIntensity.LOW, List.of(expectedAvoid.get(0)), true);
            SafetyVerdict verdict = safetyFilter.evaluate(profileWithInjury(area, severity), conflictingClass);
            assertEquals(SafetyVerdictType.BLOCKED, verdict.verdict());
            assertTrue(verdict.reasonCodes().stream().anyMatch(c -> c.startsWith("TAG_CONFLICT:" + expectedAvoid.get(0))));
        }

        // Test evaluation against a high intensity class if capped to LOW
        if ("LOW".equals(expectedCapStr)) {
            FitnessClass highClass = createClass("High Intensity Class", ClassIntensity.HIGH, List.of(), true);
            SafetyVerdict verdict = safetyFilter.evaluate(profileWithInjury(area, severity), highClass);
            assertEquals(SafetyVerdictType.BLOCKED, verdict.verdict());
            assertTrue(verdict.reasonCodes().stream().anyMatch(c -> c.startsWith("INTENSITY_CAP:LOW")));
        }
    }

    // 8 conditions test cases against exact literals
    @ParameterizedTest(name = "{0} -> avoid {1}, cap {2}")
    @CsvSource({
            "HYPERTENSION, 'heated_room,inversion,heavy_lifting', 'MODERATE'",
            "ASTHMA, 'heated_room,high_cardio', ''",
            "DIABETES, '', ''",
            "OSTEOPOROSIS, 'high_impact,deep_flexion,balance_risk', ''",
            "JOINT_ARTHRITIS, 'high_impact,deep_flexion', ''",
            "PREGNANCY, 'heated_room,inversion,high_impact,balance_risk,heavy_lifting', 'MODERATE'",
            "HEART_CONDITION, 'heated_room,inversion,heavy_lifting,high_cardio', 'LOW'",
            "OTHER_CONSULT_DOCTOR, '', 'MODERATE'"
    })
    @DisplayName("Assert avoid sets and intensity caps for all 8 health conditions against literals")
    void testAll8ConditionRules(String condStr, String expectedAvoidCsv, String expectedCapStr) {
        HealthCondition cond = HealthCondition.valueOf(condStr);
        SafetyRuleRegistry.RuleDefinition rule = ruleRegistry.getRule(cond);

        List<String> expectedAvoid = expectedAvoidCsv.isBlank()
                ? List.of()
                : List.of(expectedAvoidCsv.split(","));

        assertEquals(Set.copyOf(expectedAvoid), rule.avoidTags());

        if (expectedCapStr.isBlank()) {
            assertEquals(null, rule.intensityCap());
        } else {
            assertEquals(ClassIntensity.valueOf(expectedCapStr), rule.intensityCap());
        }

        if (!expectedAvoid.isEmpty()) {
            FitnessClass conflictingClass = createClass("Conflicting Cond Class", ClassIntensity.LOW, List.of(expectedAvoid.get(0)), true);
            SafetyVerdict verdict = safetyFilter.evaluate(profileWithCondition(cond), conflictingClass);
            assertEquals(SafetyVerdictType.BLOCKED, verdict.verdict());
            assertTrue(verdict.reasonCodes().stream().anyMatch(c -> c.startsWith("TAG_CONFLICT:" + expectedAvoid.get(0))));
        }
    }

    // 10 Combination cases (union of avoid tags + lowest cap)
    @Test
    @DisplayName("Combo 1: Knee Moderate + Asthma unions tags (high_impact, deep_flexion, heated_room, high_cardio)")
    void testCombo1() {
        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.INTERMEDIATE, null, List.of(Goal.GENERAL_FITNESS), 3,
                Collections.emptyList(),
                List.of(new InjuryDto(InjuryArea.KNEE, InjurySeverity.MODERATE)),
                Set.of(HealthCondition.ASTHMA),
                Collections.emptyMap(), false, new BigDecimal("170.0"), new BigDecimal("65.0"), Sex.MALE, null, 1
        );
        FitnessClass fc = createClass("Heated Cardio", ClassIntensity.LOW, List.of("heated_room"), true);
        SafetyVerdict verdict = safetyFilter.evaluate(profile, fc);
        assertEquals(SafetyVerdictType.BLOCKED, verdict.verdict());
        assertTrue(verdict.reasonCodes().contains("TAG_CONFLICT:heated_room(ASTHMA)"));
    }

    @Test
    @DisplayName("Combo 2: Hypertension (cap MODERATE) + Knee Severe (cap LOW) applies lowest cap LOW")
    void testCombo2LowestCap() {
        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.INTERMEDIATE, null, List.of(Goal.GENERAL_FITNESS), 3,
                Collections.emptyList(),
                List.of(new InjuryDto(InjuryArea.KNEE, InjurySeverity.SEVERE)),
                Set.of(HealthCondition.HYPERTENSION),
                Collections.emptyMap(), false, new BigDecimal("170.0"), new BigDecimal("65.0"), Sex.MALE, null, 1
        );
        FitnessClass fc = createClass("Moderate HIIT", ClassIntensity.MODERATE, List.of(), true);
        SafetyVerdict verdict = safetyFilter.evaluate(profile, fc);
        assertEquals(SafetyVerdictType.BLOCKED, verdict.verdict());
        assertTrue(verdict.reasonCodes().stream().anyMatch(c -> c.startsWith("INTENSITY_CAP:LOW")));
    }

    @Test
    @DisplayName("Combo 3: Lower back mild (heavy_lifting) + Shoulder mild (inversion) blocks both tags")
    void testCombo3UnionAvoid() {
        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.INTERMEDIATE, null, List.of(Goal.GENERAL_FITNESS), 3,
                Collections.emptyList(),
                List.of(new InjuryDto(InjuryArea.LOWER_BACK, InjurySeverity.MILD),
                        new InjuryDto(InjuryArea.SHOULDER, InjurySeverity.MILD)),
                Collections.emptySet(), Collections.emptyMap(), false, new BigDecimal("170.0"), new BigDecimal("65.0"), Sex.MALE, null, 1
        );
        FitnessClass fcInversion = createClass("Inversion Yoga", ClassIntensity.LOW, List.of("inversion"), true);
        assertEquals(SafetyVerdictType.BLOCKED, safetyFilter.evaluate(profile, fcInversion).verdict());

        FitnessClass fcLifting = createClass("Strength", ClassIntensity.LOW, List.of("heavy_lifting"), true);
        assertEquals(SafetyVerdictType.BLOCKED, safetyFilter.evaluate(profile, fcLifting).verdict());
    }

    @Test
    @DisplayName("Combo 4: Osteoporosis + Knee Mild unions deep_flexion, high_impact, balance_risk")
    void testCombo4() {
        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.INTERMEDIATE, null, List.of(Goal.GENERAL_FITNESS), 3,
                Collections.emptyList(),
                List.of(new InjuryDto(InjuryArea.KNEE, InjurySeverity.MILD)),
                Set.of(HealthCondition.OSTEOPOROSIS),
                Collections.emptyMap(), false, new BigDecimal("170.0"), new BigDecimal("65.0"), Sex.FEMALE, null, 1
        );
        FitnessClass fcBalance = createClass("Balance Bosu", ClassIntensity.LOW, List.of("balance_risk"), true);
        assertEquals(SafetyVerdictType.BLOCKED, safetyFilter.evaluate(profile, fcBalance).verdict());
    }

    @Test
    @DisplayName("Combo 5: Joint arthritis (high_impact, deep_flexion) + Hip mild (deep_flexion) deduplicates tags")
    void testCombo5Deduplicate() {
        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.INTERMEDIATE, null, List.of(Goal.GENERAL_FITNESS), 3,
                Collections.emptyList(),
                List.of(new InjuryDto(InjuryArea.HIP, InjurySeverity.MILD)),
                Set.of(HealthCondition.JOINT_ARTHRITIS),
                Collections.emptyMap(), false, new BigDecimal("170.0"), new BigDecimal("65.0"), Sex.FEMALE, null, 1
        );
        FitnessClass fcFlexion = createClass("Deep Squats", ClassIntensity.LOW, List.of("deep_flexion"), true);
        SafetyVerdict verdict = safetyFilter.evaluate(profile, fcFlexion);
        assertEquals(SafetyVerdictType.BLOCKED, verdict.verdict());
    }

    @Test
    @DisplayName("Combo 6: Pregnancy + Neck Mild applies cap MODERATE and avoids heated_room, inversion, high_impact, balance_risk, heavy_lifting")
    void testCombo6Pregnancy() {
        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.INTERMEDIATE, null, List.of(Goal.GENERAL_FITNESS), 3,
                Collections.emptyList(),
                List.of(new InjuryDto(InjuryArea.NECK, InjurySeverity.MILD)),
                Set.of(HealthCondition.PREGNANCY),
                Collections.emptyMap(), true, new BigDecimal("165.0"), new BigDecimal("60.0"), Sex.FEMALE, null, 1
        );
        FitnessClass fcHigh = createClass("High Step", ClassIntensity.HIGH, List.of(), true);
        assertEquals(SafetyVerdictType.BLOCKED, safetyFilter.evaluate(profile, fcHigh).verdict());
    }

    @Test
    @DisplayName("Combo 7: Ankle moderate (high_impact, balance_risk) + Elbow moderate (heavy_lifting)")
    void testCombo7() {
        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.INTERMEDIATE, null, List.of(Goal.GENERAL_FITNESS), 3,
                Collections.emptyList(),
                List.of(new InjuryDto(InjuryArea.ANKLE, InjurySeverity.MODERATE),
                        new InjuryDto(InjuryArea.ELBOW, InjurySeverity.MODERATE)),
                Collections.emptySet(), Collections.emptyMap(), false, new BigDecimal("170.0"), new BigDecimal("65.0"), Sex.MALE, null, 1
        );
        FitnessClass fcLift = createClass("Barbell", ClassIntensity.LOW, List.of("heavy_lifting"), true);
        assertEquals(SafetyVerdictType.BLOCKED, safetyFilter.evaluate(profile, fcLift).verdict());
    }

    @Test
    @DisplayName("Combo 8: Wrist mild (no avoid) + Diabetes (no avoid) allows classes and marks CAUTION due to injury/condition")
    void testCombo8AllowedCaution() {
        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.INTERMEDIATE, null, List.of(Goal.GENERAL_FITNESS), 3,
                Collections.emptyList(),
                List.of(new InjuryDto(InjuryArea.WRIST, InjurySeverity.MILD)),
                Set.of(HealthCondition.DIABETES),
                Collections.emptyMap(), false, new BigDecimal("170.0"), new BigDecimal("65.0"), Sex.MALE, null, 1
        );
        FitnessClass fc = createClass("Light Walking", ClassIntensity.LOW, List.of(), true);
        SafetyVerdict verdict = safetyFilter.evaluate(profile, fc);
        assertEquals(SafetyVerdictType.CAUTION, verdict.verdict());
    }

    @Test
    @DisplayName("Combo 9: CLEAR profile allows class with ALLOWED verdict")
    void testCombo9ClearAllowed() {
        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.INTERMEDIATE, null, List.of(Goal.GENERAL_FITNESS), 3,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptySet(),
                Collections.emptyMap(), false, new BigDecimal("170.0"), new BigDecimal("65.0"), Sex.MALE, null, 1
        );
        FitnessClass fc = createClass("Gentle Yoga", ClassIntensity.LOW, List.of(), true);
        SafetyVerdict verdict = safetyFilter.evaluate(profile, fc);
        assertEquals(SafetyVerdictType.ALLOWED, verdict.verdict());
    }

    @Test
    @DisplayName("Combo 10: RED_FLAG with clearance allows low/gentle classes with CAUTION verdict")
    void testCombo10RedFlagWithClearance() {
        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.INTERMEDIATE, null, List.of(Goal.GENERAL_FITNESS), 3,
                Collections.emptyList(),
                Collections.emptyList(),
                Set.of(HealthCondition.HEART_CONDITION),
                Collections.emptyMap(), true, // clearance attested!
                new BigDecimal("170.0"), new BigDecimal("65.0"), Sex.MALE, null, 1
        );
        // Heart condition caps at LOW, avoids heated_room, inversion, heavy_lifting, high_cardio
        FitnessClass fcLow = createClass("Gentle Stretch", ClassIntensity.LOW, List.of(), true);
        SafetyVerdict verdict = safetyFilter.evaluate(profile, fcLow);
        assertEquals(SafetyVerdictType.CAUTION, verdict.verdict());
    }

    @Test
    @DisplayName("RED_FLAG without clearance: blocks any class with ANY tag or intensity > LOW")
    void testRedFlagWithoutClearanceRestrictsAll() {
        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.INTERMEDIATE, null, List.of(Goal.GENERAL_FITNESS), 3,
                Collections.emptyList(),
                Collections.emptyList(),
                Set.of(HealthCondition.HEART_CONDITION),
                Collections.emptyMap(), false, // NO clearance attested
                new BigDecimal("170.0"), new BigDecimal("65.0"), Sex.MALE, null, 1
        );

        // Class with MODERATE intensity is blocked
        FitnessClass fcMod = createClass("Moderate Walk", ClassIntensity.MODERATE, List.of(), true);
        SafetyVerdict v1 = safetyFilter.evaluate(profile, fcMod);
        assertEquals(SafetyVerdictType.BLOCKED, v1.verdict());
        assertTrue(v1.reasonCodes().contains("INTENSITY_CAP:LOW(RED_FLAG_NO_CLEARANCE)"));

        // Class with any tag (e.g. heated_room or high_impact) is blocked
        FitnessClass fcTag = createClass("Low Heated", ClassIntensity.LOW, List.of("heated_room"), true);
        SafetyVerdict v2 = safetyFilter.evaluate(profile, fcTag);
        assertEquals(SafetyVerdictType.BLOCKED, v2.verdict());
        assertTrue(v2.reasonCodes().contains("TAG_CONFLICT:heated_room(RED_FLAG_NO_CLEARANCE)"));

        // Class with LOW intensity and NO tags is allowed with CAUTION
        FitnessClass fcGentle = createClass("Pure Gentle Breathing", ClassIntensity.LOW, List.of(), true);
        SafetyVerdict v3 = safetyFilter.evaluate(profile, fcGentle);
        assertEquals(SafetyVerdictType.CAUTION, v3.verdict());
    }
}
