package com.fitflow.wellness;

import com.fitflow.common.error.ApiException;
import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.repository.FitnessClassRepository;
import com.fitflow.user.AgeService;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.entity.User;
import com.fitflow.wellness.dto.ClassBurnResponse;
import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.NutritionNumbersResponse;
import com.fitflow.wellness.dto.ScreeningResult;
import com.fitflow.wellness.entity.MemberHealthProfile;
import com.fitflow.wellness.model.ConsentPurpose;
import com.fitflow.wellness.model.Goal;
import com.fitflow.wellness.model.HealthAccessAction;
import com.fitflow.wellness.model.HealthCondition;
import com.fitflow.wellness.model.ScreeningStatus;
import com.fitflow.wellness.model.Sex;
import com.fitflow.wellness.repository.MemberHealthProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalorieService {

    public static final String BURN_ASSUMPTIONS = "Estimate using the class MET value and your weight";

    private final ConsentService consentService;
    private final MemberHealthProfileRepository profileRepository;
    private final HealthDataCodec healthDataCodec;
    private final ScreeningService screeningService;
    private final FitnessClassRepository fitnessClassRepository;
    private final UserRepository userRepository;
    private final AgeService ageService;
    private final HealthAccessService healthAccessService;

    /**
     * Estimates calorie burn for a class:
     * kcal = MET * weightKg * (durationMinutes / 60), rounded half-up to whole kcal.
     */
    public int calculateBurn(BigDecimal metValue, BigDecimal weightKg, int durationMinutes) {
        if (metValue == null || weightKg == null || durationMinutes <= 0) {
            return 0;
        }
        BigDecimal durationFraction = BigDecimal.valueOf(durationMinutes)
                .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP);
        BigDecimal rawKcal = metValue.multiply(weightKg).multiply(durationFraction);
        return rawKcal.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    @Transactional(readOnly = true)
    public ClassBurnResponse getClassBurn(Long memberId, Long classId) {
        consentService.checkConsent(memberId, ConsentPurpose.CALORIE_ESTIMATES);

        FitnessClass fitnessClass = fitnessClassRepository.findById(classId)
                .orElseThrow(() -> ApiException.notFound("Fitness class not found"));

        MemberHealthProfile profileEntity = profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> ApiException.notFound("Health profile not found. Please create your profile first."));

        FitnessProfile profile = healthDataCodec.decode(profileEntity.getDataEncrypted());
        if (profile == null || profile.weightKg() == null) {
            throw ApiException.badRequest("Weight is missing in health profile");
        }

        int kcal = calculateBurn(fitnessClass.getMetValue(), profile.weightKg(), fitnessClass.getDurationMinutes());
        healthAccessService.logAccess(memberId, memberId, HealthAccessAction.READ, "CLASS_BURN_CALCULATION");

        return new ClassBurnResponse(kcal, BURN_ASSUMPTIONS, SafetyRuleRegistry.DISCLAIMER);
    }

    @Transactional(readOnly = true)
    public NutritionNumbersResponse getNutritionNumbers(Long memberId) {
        consentService.checkConsent(memberId, ConsentPurpose.CALORIE_ESTIMATES);

        User user = userRepository.findById(memberId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        MemberHealthProfile profileEntity = profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> ApiException.notFound("Health profile not found"));

        FitnessProfile profile = healthDataCodec.decode(profileEntity.getDataEncrypted());
        if (profile == null) {
            throw ApiException.notFound("Health profile is empty");
        }

        healthAccessService.logAccess(memberId, memberId, HealthAccessAction.READ, "NUTRITION_NUMBERS_CALCULATION");

        // Availability check 1: Age < 18
        int age = ageService.calculateAge(user.getDateOfBirth());
        if (age < 18) {
            return new NutritionNumbersResponse(
                    false,
                    "AGE_RESTRICTED",
                    null, null, null, false, List.of(),
                    SafetyRuleRegistry.DISCLAIMER
            );
        }

        // Availability check 2: Status RED_FLAG, or any condition, or PREGNANCY
        ScreeningResult screening = screeningService.evaluate(profile);
        boolean hasAnyCondition = profile.conditions() != null && !profile.conditions().isEmpty();
        boolean isPregnancy = profile.conditions() != null && profile.conditions().contains(HealthCondition.PREGNANCY);

        if (screening.status() == ScreeningStatus.RED_FLAG || hasAnyCondition || isPregnancy) {
            return new NutritionNumbersResponse(
                    false,
                    "CONSULT_PROFESSIONAL",
                    null, null, null, false, List.of(),
                    SafetyRuleRegistry.DISCLAIMER
            );
        }

        // Full calculation
        return calculateDailyNutrition(profile, age);
    }

    public NutritionNumbersResponse calculateDailyNutrition(FitnessProfile profile, int ageYears) {
        BigDecimal weightKg = profile.weightKg();
        BigDecimal heightCm = profile.heightCm();
        Sex sex = profile.sex();
        int sessionsPerWeek = profile.sessionsPerWeekPlanned() != null ? profile.sessionsPerWeekPlanned() : 0;

        // 1. BMR (Mifflin-St Jeor): 10*weightKg + 6.25*heightCm - 5*ageYears + s
        int s = switch (sex) {
            case MALE -> 5;
            case FEMALE -> -161;
            case UNSPECIFIED -> -78; // documented midpoint: (5 + -161)/2 = -78
        };

        BigDecimal bmrRaw = BigDecimal.valueOf(10).multiply(weightKg)
                .add(new BigDecimal("6.25").multiply(heightCm))
                .subtract(BigDecimal.valueOf(5).multiply(BigDecimal.valueOf(ageYears)))
                .add(BigDecimal.valueOf(s));

        // 2. Activity factor
        BigDecimal factor = getActivityFactor(sessionsPerWeek);
        BigDecimal tdeeRaw = bmrRaw.multiply(factor);

        // 3. Target calories
        List<String> notes = new ArrayList<>();
        BigDecimal targetRaw;

        boolean wantsLoseWeight = profile.goals() != null && profile.goals().contains(Goal.LOSE_WEIGHT);
        boolean wantsBuildStrength = profile.goals() != null && profile.goals().contains(Goal.BUILD_STRENGTH);

        // Calculate BMI: weightKg / (heightCm/100)^2
        BigDecimal heightM = heightCm.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
        BigDecimal bmi = weightKg.divide(heightM.multiply(heightM), 4, RoundingMode.HALF_UP);

        if (wantsLoseWeight) {
            if (bmi.compareTo(new BigDecimal("18.5")) < 0) {
                // BMI < 18.5 replaces LOSE_WEIGHT target with maintenance and adds note UNDERWEIGHT_NO_DEFICIT
                targetRaw = tdeeRaw;
                notes.add("UNDERWEIGHT_NO_DEFICIT");
            } else {
                BigDecimal fifteenPct = tdeeRaw.multiply(new BigDecimal("0.15"));
                BigDecimal max500 = new BigDecimal("500");
                BigDecimal deficit = fifteenPct.min(max500);
                targetRaw = tdeeRaw.subtract(deficit);
            }
        } else if (wantsBuildStrength) {
            BigDecimal tenPct = tdeeRaw.multiply(new BigDecimal("0.10"));
            targetRaw = tdeeRaw.add(tenPct);
        } else {
            targetRaw = tdeeRaw;
        }

        // 4. Floor guard: never below 1200 (FEMALE, UNSPECIFIED) or 1500 (MALE)
        BigDecimal floor = (sex == Sex.MALE) ? new BigDecimal("1500") : new BigDecimal("1200");
        boolean floorApplied = false;
        if (targetRaw.compareTo(floor) < 0) {
            targetRaw = floor;
            floorApplied = true;
        }

        // Round half-up ONCE at the end
        int finalBmr = bmrRaw.setScale(0, RoundingMode.HALF_UP).intValue();
        int finalTdee = tdeeRaw.setScale(0, RoundingMode.HALF_UP).intValue();
        int finalTarget = targetRaw.setScale(0, RoundingMode.HALF_UP).intValue();

        return new NutritionNumbersResponse(
                true,
                null,
                finalBmr,
                finalTdee,
                finalTarget,
                floorApplied,
                notes,
                SafetyRuleRegistry.DISCLAIMER
        );
    }

    private BigDecimal getActivityFactor(int sessions) {
        if (sessions <= 1) {
            return new BigDecimal("1.2");
        } else if (sessions <= 3) {
            return new BigDecimal("1.375");
        } else if (sessions <= 5) {
            return new BigDecimal("1.55");
        } else {
            return new BigDecimal("1.725");
        }
    }
}
