package com.fitflow.wellness;

import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.NutritionNumbersResponse;
import com.fitflow.wellness.model.ExperienceLevel;
import com.fitflow.wellness.model.Goal;
import com.fitflow.wellness.model.Sex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalorieMathTest {

    private CalorieService calorieService;

    @BeforeEach
    void setUp() {
        // Test pure math without Spring dependencies
        calorieService = new CalorieService(null, null, null, null, null, null, null, null);
    }

    private FitnessProfile createProfile(
            Sex sex,
            BigDecimal weightKg,
            BigDecimal heightCm,
            int sessionsPerWeek,
            Goal goal) {
        return new FitnessProfile(
                ExperienceLevel.INTERMEDIATE,
                null,
                goal != null ? List.of(goal) : List.of(Goal.GENERAL_FITNESS),
                sessionsPerWeek,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptySet(),
                Collections.emptyMap(),
                false,
                heightCm,
                weightKg,
                sex,
                null,
                1
        );
    }

    // --- PROMPT SPECIFIED TEST VECTORS ---

    @Test
    @DisplayName("Vector 1 (Prompt): MALE 30y 70kg 175cm, 4 sessions/week -> BMR 1649, TDEE 2556")
    void testPromptVector1() {
        // BMR = 10*70 + 6.25*175 - 5*30 + 5 = 700 + 1093.75 - 150 + 5 = 1648.75 -> 1649
        // At 4 sessions/week (factor 1.55): TDEE = 1648.75 * 1.55 = 2555.5625 -> 2556
        FitnessProfile profile = createProfile(Sex.MALE, new BigDecimal("70.0"), new BigDecimal("175.0"), 4, Goal.GENERAL_FITNESS);
        NutritionNumbersResponse resp = calorieService.calculateDailyNutrition(profile, 30);

        assertEquals(1649, resp.bmr());
        assertEquals(2556, resp.tdee());
        assertEquals(2556, resp.targetCalories());
        assertFalse(resp.floorApplied());
    }

    @Test
    @DisplayName("Vector 2 (Prompt): FEMALE 25y 60kg 165cm, 0 sessions/week (1.2) -> BMR 1345, TDEE 1614")
    void testPromptVector2() {
        // BMR = 10*60 + 6.25*165 - 5*25 - 161 = 600 + 1031.25 - 125 - 161 = 1345.25 -> 1345
        // Sedentary 1.2: TDEE = 1345.25 * 1.2 = 1614.3 -> 1614
        FitnessProfile profile = createProfile(Sex.FEMALE, new BigDecimal("60.0"), new BigDecimal("165.0"), 0, Goal.GENERAL_FITNESS);
        NutritionNumbersResponse resp = calorieService.calculateDailyNutrition(profile, 25);

        assertEquals(1345, resp.bmr());
        assertEquals(1614, resp.tdee());
        assertEquals(1614, resp.targetCalories());
        assertFalse(resp.floorApplied());
    }

    @Test
    @DisplayName("Vector 3 (Prompt MET): 3.0 MET, 60kg, 60 min -> 180 kcal")
    void testPromptVector3() {
        // 3.0 * 60 * (60 / 60) = 180
        int kcal = calorieService.calculateBurn(new BigDecimal("3.0"), new BigDecimal("60.0"), 60);
        assertEquals(180, kcal);
    }

    @Test
    @DisplayName("Vector 4 (Prompt MET): 8.0 MET, 70kg, 45 min -> 420 kcal")
    void testPromptVector4() {
        // 8.0 * 70 * (45 / 60) = 8.0 * 70 * 0.75 = 420
        int kcal = calorieService.calculateBurn(new BigDecimal("8.0"), new BigDecimal("70.0"), 45);
        assertEquals(420, kcal);
    }

    @Test
    @DisplayName("Vector 5 (Prompt MET): 5.5 MET, 62.5kg, 50 min -> 286 kcal")
    void testPromptVector5() {
        // 5.5 * 62.5 * (50 / 60) = 343.75 * 0.8333333333 = 286.458333... -> 286 (half-up)
        int kcal = calorieService.calculateBurn(new BigDecimal("5.5"), new BigDecimal("62.5"), 50);
        assertEquals(286, kcal);
    }

    @Test
    @DisplayName("Vector 6 (Prompt Floor): FEMALE 25y 50kg 150cm LOSE_WEIGHT sedentary -> floor 1200, floorApplied=true")
    void testPromptVector6Floor() {
        // BMR = 10*50 + 6.25*150 - 5*25 - 161 = 500 + 937.5 - 125 - 161 = 1151.5
        // TDEE = 1151.5 * 1.2 = 1381.8
        // Deficit: min(1381.8 * 0.15 = 207.27, 500) = 207.27
        // Raw target: 1381.8 - 207.27 = 1174.53
        // Floor: 1200 (FEMALE) -> floor applied!
        FitnessProfile profile = createProfile(Sex.FEMALE, new BigDecimal("50.0"), new BigDecimal("150.0"), 0, Goal.LOSE_WEIGHT);
        NutritionNumbersResponse resp = calorieService.calculateDailyNutrition(profile, 25);

        assertEquals(1152, resp.bmr()); // 1151.5 half up -> 1152
        assertEquals(1382, resp.tdee()); // 1381.8 half up -> 1382
        assertEquals(1200, resp.targetCalories());
        assertTrue(resp.floorApplied());
    }

    // --- 10 ADDITIONAL HAND-COMPUTED VECTORS WITH STEP-BY-STEP ARITHMETIC ---

    @Test
    @DisplayName("Vector 7: MALE 40y 85kg 180cm, BUILD_STRENGTH (+10%), 5 sessions/week (1.55)")
    void testVector7() {
        // BMR = 10*85 + 6.25*180 - 5*40 + 5 = 850 + 1125 - 200 + 5 = 1780.0
        // TDEE = 1780.0 * 1.55 = 2759.0
        // Target = 2759.0 + (2759.0 * 0.10) = 2759.0 + 275.9 = 3034.9 -> 3035 (half-up)
        FitnessProfile profile = createProfile(Sex.MALE, new BigDecimal("85.0"), new BigDecimal("180.0"), 5, Goal.BUILD_STRENGTH);
        NutritionNumbersResponse resp = calorieService.calculateDailyNutrition(profile, 40);

        assertEquals(1780, resp.bmr());
        assertEquals(2759, resp.tdee());
        assertEquals(3035, resp.targetCalories());
        assertFalse(resp.floorApplied());
    }

    @Test
    @DisplayName("Vector 8: UNSPECIFIED 35y 65kg 170cm, GENERAL_FITNESS, 2 sessions/week (1.375)")
    void testVector8() {
        // Midpoint s = -78:
        // BMR = 10*65 + 6.25*170 - 5*35 - 78 = 650 + 1062.5 - 175 - 78 = 1459.5 -> 1460
        // TDEE = 1459.5 * 1.375 = 2006.8125 -> 2007 (half-up)
        // Target = 2007 (maintenance)
        FitnessProfile profile = createProfile(Sex.UNSPECIFIED, new BigDecimal("65.0"), new BigDecimal("170.0"), 2, Goal.GENERAL_FITNESS);
        NutritionNumbersResponse resp = calorieService.calculateDailyNutrition(profile, 35);

        assertEquals(1460, resp.bmr());
        assertEquals(2007, resp.tdee());
        assertEquals(2007, resp.targetCalories());
        assertFalse(resp.floorApplied());
    }

    @Test
    @DisplayName("Vector 9: FEMALE 20y 45kg 165cm (BMI = 45 / 1.65^2 = 16.53 < 18.5) LOSE_WEIGHT underweight guard")
    void testVector9UnderweightGuard() {
        // BMR = 10*45 + 6.25*165 - 5*20 - 161 = 450 + 1031.25 - 100 - 161 = 1220.25 -> 1220
        // TDEE = 1220.25 * 1.2 = 1464.3 -> 1464
        // BMI < 18.5: LOSE_WEIGHT deficit is replaced with maintenance (1464) and note UNDERWEIGHT_NO_DEFICIT
        FitnessProfile profile = createProfile(Sex.FEMALE, new BigDecimal("45.0"), new BigDecimal("165.0"), 0, Goal.LOSE_WEIGHT);
        NutritionNumbersResponse resp = calorieService.calculateDailyNutrition(profile, 20);

        assertEquals(1220, resp.bmr());
        assertEquals(1464, resp.tdee());
        assertEquals(1464, resp.targetCalories());
        assertFalse(resp.floorApplied());
        assertTrue(resp.notes().contains("UNDERWEIGHT_NO_DEFICIT"));
    }

    @Test
    @DisplayName("Vector 10: MALE 22y 55kg 170cm, LOSE_WEIGHT, sedentary (1.2)")
    void testVector10() {
        // BMR = 10*55 + 6.25*170 - 5*22 + 5 = 550 + 1062.5 - 110 + 5 = 1507.5 -> 1508
        // TDEE = 1507.5 * 1.2 = 1809.0
        // Deficit: min(1809.0 * 0.15 = 271.35, 500) = 271.35
        // Raw target: 1809.0 - 271.35 = 1537.65 -> 1538 (above male floor 1500)
        FitnessProfile profile = createProfile(Sex.MALE, new BigDecimal("55.0"), new BigDecimal("170.0"), 1, Goal.LOSE_WEIGHT);
        NutritionNumbersResponse resp = calorieService.calculateDailyNutrition(profile, 22);

        assertEquals(1508, resp.bmr());
        assertEquals(1809, resp.tdee());
        assertEquals(1538, resp.targetCalories());
        assertFalse(resp.floorApplied());
    }

    @Test
    @DisplayName("Vector 11: MALE 25y 50kg 160cm, LOSE_WEIGHT, sedentary (1.2) hits MALE floor 1500")
    void testVector11MaleFloor() {
        // BMR = 10*50 + 6.25*160 - 5*25 + 5 = 500 + 1000 - 125 + 5 = 1380.0
        // TDEE = 1380.0 * 1.2 = 1656.0
        // Deficit: min(1656.0 * 0.15 = 248.4, 500) = 248.4
        // Raw target = 1656.0 - 248.4 = 1407.6
        // Floor = 1500 (MALE) -> target = 1500, floorApplied = true
        FitnessProfile profile = createProfile(Sex.MALE, new BigDecimal("50.0"), new BigDecimal("160.0"), 0, Goal.LOSE_WEIGHT);
        NutritionNumbersResponse resp = calorieService.calculateDailyNutrition(profile, 25);

        assertEquals(1380, resp.bmr());
        assertEquals(1656, resp.tdee());
        assertEquals(1500, resp.targetCalories());
        assertTrue(resp.floorApplied());
    }

    @Test
    @DisplayName("Vector 12: FEMALE 30y 80kg 160cm, LOSE_WEIGHT, 6 sessions/week (1.725)")
    void testVector12() {
        // BMR = 10*80 + 6.25*160 - 5*30 - 161 = 800 + 1000 - 150 - 161 = 1489.0
        // TDEE = 1489.0 * 1.725 = 2568.525 -> 2569
        // Deficit: min(2568.525 * 0.15 = 385.27875, 500) = 385.27875
        // Raw target: 2568.525 - 385.27875 = 2183.24625 -> 2183
        FitnessProfile profile = createProfile(Sex.FEMALE, new BigDecimal("80.0"), new BigDecimal("160.0"), 6, Goal.LOSE_WEIGHT);
        NutritionNumbersResponse resp = calorieService.calculateDailyNutrition(profile, 30);

        assertEquals(1489, resp.bmr());
        assertEquals(2569, resp.tdee());
        assertEquals(2183, resp.targetCalories());
        assertFalse(resp.floorApplied());
    }

    @Test
    @DisplayName("Vector 13: Class burn MET 4.0, 75kg, 30 min -> 150 kcal")
    void testVector13Burn() {
        // 4.0 * 75 * (30/60) = 300 * 0.5 = 150
        int kcal = calorieService.calculateBurn(new BigDecimal("4.0"), new BigDecimal("75.0"), 30);
        assertEquals(150, kcal);
    }

    @Test
    @DisplayName("Vector 14: Class burn MET 10.0, 80kg, 60 min -> 800 kcal")
    void testVector14Burn() {
        // 10.0 * 80 * (60/60) = 800
        int kcal = calorieService.calculateBurn(new BigDecimal("10.0"), new BigDecimal("80.0"), 60);
        assertEquals(800, kcal);
    }

    @Test
    @DisplayName("Vector 15: Class burn MET 6.0, 50kg, 45 min -> 225 kcal")
    void testVector15Burn() {
        // 6.0 * 50 * (45/60) = 300 * 0.75 = 225
        int kcal = calorieService.calculateBurn(new BigDecimal("6.0"), new BigDecimal("50.0"), 45);
        assertEquals(225, kcal);
    }

    @Test
    @DisplayName("Vector 16 (Half-up check): Class burn MET 7.0, 65kg, 25 min -> 190 kcal")
    void testVector16BurnHalfUp() {
        // 7.0 * 65 * (25/60) = 455 * 0.4166666667 = 189.5833333333 -> 190 (half up)
        int kcal = calorieService.calculateBurn(new BigDecimal("7.0"), new BigDecimal("65.0"), 25);
        assertEquals(190, kcal);
    }

    // --- RANDOMIZED PROPERTY TEST (1000 VALID INPUTS) ---

    @Test
    @DisplayName("Property Test: 1000 random inputs with fixed seed - target >= floor, target > 0, TDEE >= BMR")
    void testProperty1000RandomInputs() {
        Random rand = new Random(42);
        Goal[] goals = Goal.values();
        Sex[] sexes = Sex.values();

        for (int i = 0; i < 1000; i++) {
            Sex sex = sexes[rand.nextInt(sexes.length)];
            int age = 18 + rand.nextInt(60); // 18 - 77
            double weight = 40.0 + (rand.nextDouble() * 150.0); // 40 - 190 kg
            double height = 130.0 + (rand.nextDouble() * 80.0); // 130 - 210 cm
            int sessions = rand.nextInt(10); // 0 - 9
            Goal goal = goals[rand.nextInt(goals.length)];

            FitnessProfile profile = createProfile(
                    sex,
                    BigDecimal.valueOf(weight).setScale(1, BigDecimal.ROUND_HALF_UP),
                    BigDecimal.valueOf(height).setScale(1, BigDecimal.ROUND_HALF_UP),
                    sessions,
                    goal
            );

            NutritionNumbersResponse resp = calorieService.calculateDailyNutrition(profile, age);

            int minFloor = (sex == Sex.MALE) ? 1500 : 1200;

            assertTrue(resp.targetCalories() >= minFloor,
                    "Target calories (" + resp.targetCalories() + ") must never be below floor (" + minFloor + ")");
            assertTrue(resp.targetCalories() > 0, "Target calories must be positive");
            assertTrue(resp.tdee() >= resp.bmr(), "TDEE (" + resp.tdee() + ") must be >= BMR (" + resp.bmr() + ")");
        }
    }
}
