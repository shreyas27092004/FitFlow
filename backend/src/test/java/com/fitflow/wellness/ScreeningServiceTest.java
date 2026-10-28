package com.fitflow.wellness;

import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.InjuryDto;
import com.fitflow.wellness.dto.ScreeningResult;
import com.fitflow.wellness.model.ExperienceLevel;
import com.fitflow.wellness.model.Goal;
import com.fitflow.wellness.model.HealthCondition;
import com.fitflow.wellness.model.InjuryArea;
import com.fitflow.wellness.model.InjurySeverity;
import com.fitflow.wellness.model.ScreeningStatus;
import com.fitflow.wellness.model.Sex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreeningServiceTest {

    private ScreeningService screeningService;

    @BeforeEach
    void setUp() {
        screeningService = new ScreeningService();
    }

    private FitnessProfile baseProfile(
            Map<String, Boolean> parq,
            Set<HealthCondition> conditions,
            List<InjuryDto> injuries,
            boolean clearance) {
        return new FitnessProfile(
                ExperienceLevel.INTERMEDIATE,
                "No prior notes",
                List.of(Goal.GENERAL_FITNESS),
                3,
                Collections.emptyList(),
                injuries != null ? injuries : Collections.emptyList(),
                conditions != null ? conditions : Collections.emptySet(),
                parq != null ? parq : Collections.emptyMap(),
                clearance,
                new BigDecimal("175.0"),
                new BigDecimal("70.0"),
                Sex.MALE,
                null,
                1
        );
    }

    @Test
    @DisplayName("CLEAR status when no injuries, no conditions, and all PAR-Q false")
    void testClearStatus() {
        Map<String, Boolean> allFalseParq = new HashMap<>();
        for (int i = 1; i <= 7; i++) {
            allFalseParq.put("Q" + i, false);
        }
        FitnessProfile profile = baseProfile(allFalseParq, Set.of(), List.of(), false);
        ScreeningResult result = screeningService.evaluate(profile);

        assertEquals(ScreeningStatus.CLEAR, result.status());
        assertFalse(result.doctorCheckRecommended());
        assertTrue(result.reasons().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Q1", "Q2", "Q3", "Q4", "Q5", "Q6", "Q7"})
    @DisplayName("RED_FLAG status and doctorCheckRecommended=true when any PAR-Q question is true")
    void testParqQuestionsTriggerRedFlag(String qKey) {
        Map<String, Boolean> parq = new HashMap<>();
        parq.put(qKey, true);

        FitnessProfile profile = baseProfile(parq, Set.of(), List.of(), false);
        ScreeningResult result = screeningService.evaluate(profile);

        assertEquals(ScreeningStatus.RED_FLAG, result.status());
        assertTrue(result.doctorCheckRecommended());
        assertTrue(result.reasons().contains("PARQ_YES:" + qKey));
    }

    @Test
    @DisplayName("RED_FLAG triggered by HEART_CONDITION")
    void testHeartConditionTriggersRedFlag() {
        FitnessProfile profile = baseProfile(Map.of(), Set.of(HealthCondition.HEART_CONDITION), List.of(), false);
        ScreeningResult result = screeningService.evaluate(profile);

        assertEquals(ScreeningStatus.RED_FLAG, result.status());
        assertTrue(result.doctorCheckRecommended());
        assertTrue(result.reasons().contains("CONDITION:HEART_CONDITION"));
    }

    @Test
    @DisplayName("RED_FLAG triggered by PREGNANCY")
    void testPregnancyTriggersRedFlag() {
        FitnessProfile profile = baseProfile(Map.of(), Set.of(HealthCondition.PREGNANCY), List.of(), false);
        ScreeningResult result = screeningService.evaluate(profile);

        assertEquals(ScreeningStatus.RED_FLAG, result.status());
        assertTrue(result.doctorCheckRecommended());
        assertTrue(result.reasons().contains("CONDITION:PREGNANCY"));
    }

    @Test
    @DisplayName("RED_FLAG with medical clearance attested keeps doctorCheckRecommended=true")
    void testRedFlagWithClearanceKeepsDoctorCheck() {
        FitnessProfile profile = baseProfile(Map.of("Q1", true), Set.of(), List.of(), true);
        ScreeningResult result = screeningService.evaluate(profile);

        assertEquals(ScreeningStatus.RED_FLAG, result.status());
        assertTrue(result.doctorCheckRecommended());
    }

    @Test
    @DisplayName("CAUTION with SEVERE injury triggers doctorCheckRecommended=true")
    void testSevereInjuryTriggersDoctorCheck() {
        List<InjuryDto> injuries = List.of(new InjuryDto(InjuryArea.KNEE, InjurySeverity.SEVERE));
        FitnessProfile profile = baseProfile(Map.of(), Set.of(), injuries, false);
        ScreeningResult result = screeningService.evaluate(profile);

        assertEquals(ScreeningStatus.CAUTION, result.status());
        assertTrue(result.doctorCheckRecommended());
        assertTrue(result.reasons().contains("INJURY:KNEE:SEVERE"));
    }

    @Test
    @DisplayName("CAUTION with OTHER_CONSULT_DOCTOR triggers doctorCheckRecommended=true")
    void testOtherConsultDoctorTriggersDoctorCheck() {
        FitnessProfile profile = baseProfile(Map.of(), Set.of(HealthCondition.OTHER_CONSULT_DOCTOR), List.of(), false);
        ScreeningResult result = screeningService.evaluate(profile);

        assertEquals(ScreeningStatus.CAUTION, result.status());
        assertTrue(result.doctorCheckRecommended());
        assertTrue(result.reasons().contains("CONDITION:OTHER_CONSULT_DOCTOR"));
    }

    @Test
    @DisplayName("CAUTION with MILD injury has doctorCheckRecommended=false")
    void testMildInjuryHasNoDoctorCheck() {
        List<InjuryDto> injuries = List.of(new InjuryDto(InjuryArea.ANKLE, InjurySeverity.MILD));
        FitnessProfile profile = baseProfile(Map.of(), Set.of(), injuries, false);
        ScreeningResult result = screeningService.evaluate(profile);

        assertEquals(ScreeningStatus.CAUTION, result.status());
        assertFalse(result.doctorCheckRecommended());
    }

    @Test
    @DisplayName("CAUTION with MODERATE injury has doctorCheckRecommended=false")
    void testModerateInjuryHasNoDoctorCheck() {
        List<InjuryDto> injuries = List.of(new InjuryDto(InjuryArea.SHOULDER, InjurySeverity.MODERATE));
        FitnessProfile profile = baseProfile(Map.of(), Set.of(), injuries, false);
        ScreeningResult result = screeningService.evaluate(profile);

        assertEquals(ScreeningStatus.CAUTION, result.status());
        assertFalse(result.doctorCheckRecommended());
    }

    @ParameterizedTest
    @ValueSource(strings = {"HYPERTENSION", "ASTHMA", "DIABETES", "OSTEOPOROSIS", "JOINT_ARTHRITIS"})
    @DisplayName("Standard conditions trigger CAUTION with doctorCheckRecommended=false")
    void testStandardConditionsTriggerCaution(String conditionName) {
        HealthCondition cond = HealthCondition.valueOf(conditionName);
        FitnessProfile profile = baseProfile(Map.of(), Set.of(cond), List.of(), false);
        ScreeningResult result = screeningService.evaluate(profile);

        assertEquals(ScreeningStatus.CAUTION, result.status());
        assertFalse(result.doctorCheckRecommended());
        assertTrue(result.reasons().contains("CONDITION:" + conditionName));
    }

    @Test
    @DisplayName("Multiple mild injuries and conditions without severe or red flags result in CAUTION without doctor check")
    void testMultipleMildIssues() {
        List<InjuryDto> injuries = List.of(
                new InjuryDto(InjuryArea.WRIST, InjurySeverity.MILD),
                new InjuryDto(InjuryArea.LOWER_BACK, InjurySeverity.MODERATE)
        );
        Set<HealthCondition> conditions = Set.of(HealthCondition.ASTHMA);
        FitnessProfile profile = baseProfile(Map.of(), conditions, injuries, false);
        ScreeningResult result = screeningService.evaluate(profile);

        assertEquals(ScreeningStatus.CAUTION, result.status());
        assertFalse(result.doctorCheckRecommended());
        assertEquals(3, result.reasons().size());
    }
}
