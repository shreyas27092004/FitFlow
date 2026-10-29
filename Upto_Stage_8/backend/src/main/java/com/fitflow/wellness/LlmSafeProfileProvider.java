package com.fitflow.wellness;

import com.fitflow.user.AgeService;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.entity.User;
import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.LlmSafeProfile;
import com.fitflow.wellness.dto.ScreeningResult;
import com.fitflow.wellness.entity.MemberHealthProfile;
import com.fitflow.wellness.model.ConsentPurpose;
import com.fitflow.wellness.model.HealthAccessAction;
import com.fitflow.wellness.repository.MemberHealthProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmSafeProfileProvider {

    private final ConsentService consentService;
    private final MemberHealthProfileRepository profileRepository;
    private final HealthDataCodec healthDataCodec;
    private final ScreeningService screeningService;
    private final UserRepository userRepository;
    private final AgeService ageService;
    private final HealthAccessService healthAccessService;

    @Transactional(readOnly = true)
    public Optional<LlmSafeProfile> getLlmSafeProfile(Long memberId) {
        // Return empty unless AI_ASSISTANT_PROCESSING is granted
        if (!consentService.hasConsent(memberId, ConsentPurpose.AI_ASSISTANT_PROCESSING)) {
            return Optional.empty();
        }

        Optional<MemberHealthProfile> entityOpt = profileRepository.findByMemberId(memberId);
        if (entityOpt.isEmpty()) {
            return Optional.empty();
        }

        FitnessProfile profile = healthDataCodec.decode(entityOpt.get().getDataEncrypted());
        if (profile == null) {
            return Optional.empty();
        }

        User user = userRepository.findById(memberId).orElse(null);
        int age = user != null ? ageService.calculateAge(user.getDateOfBirth()) : 0;
        String ageBand = computeAgeBand(age);

        ScreeningResult screening = screeningService.evaluate(profile);

        List<String> injuryCodes = profile.injuries() != null
                ? profile.injuries().stream()
                .map(i -> i.area().name() + ":" + i.severity().name())
                .toList()
                : Collections.emptyList();

        List<String> conditionCodes = profile.conditions() != null
                ? profile.conditions().stream()
                .map(Enum::name)
                .toList()
                : Collections.emptyList();

        healthAccessService.logAccess(memberId, memberId, HealthAccessAction.READ, "AI_COACH_PROFILE_SYNC");

        return Optional.of(new LlmSafeProfile(
                ageBand,
                profile.sex(),
                profile.experienceLevel(),
                profile.goals(),
                injuryCodes,
                conditionCodes,
                screening.status(),
                screening.doctorCheckRecommended()
        ));
    }

    public static String computeAgeBand(int age) {
        if (age < 18) {
            return "UNDER_18";
        } else if (age <= 24) {
            return "18-24";
        } else if (age <= 34) {
            return "25-34";
        } else if (age <= 44) {
            return "35-44";
        } else if (age <= 54) {
            return "45-54";
        } else if (age <= 64) {
            return "55-64";
        } else {
            return "65+";
        }
    }
}
