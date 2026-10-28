package com.fitflow.wellness;

import com.fitflow.booking.entity.Booking;
import com.fitflow.booking.model.BookingStatus;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.common.error.ApiException;
import com.fitflow.session.entity.ClassSession;
import com.fitflow.session.repository.ClassSessionRepository;
import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.InjuryDto;
import com.fitflow.wellness.dto.SuggestedModificationDto;
import com.fitflow.wellness.dto.TrainerPrepResponse;
import com.fitflow.wellness.entity.MemberHealthProfile;
import com.fitflow.wellness.model.ConsentPurpose;
import com.fitflow.wellness.model.HealthAccessAction;
import com.fitflow.wellness.model.InjuryArea;
import com.fitflow.wellness.repository.MemberHealthProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainerPrepService {

    public static final String NOT_ENOUGH_DATA_NOTE = "Not enough shared data to show an anonymous summary.";

    private final ClassSessionRepository sessionRepository;
    private final BookingRepository bookingRepository;
    private final MemberHealthProfileRepository profileRepository;
    private final HealthDataCodec healthDataCodec;
    private final ConsentService consentService;
    private final SafetyRuleRegistry ruleRegistry;
    private final HealthAccessService healthAccessService;

    @Transactional(readOnly = true)
    public TrainerPrepResponse getTrainerPrepSummary(Long trainerId, Long sessionId) {
        ClassSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("Session not found with id: " + sessionId));

        // Security check: TRAINER, own session only (else 404)
        if (!session.getTrainerId().equals(trainerId)) {
            throw ApiException.notFound("Session not found with id: " + sessionId);
        }

        List<Booking> confirmedBookings = bookingRepository.findBySessionIdAndStatus(sessionId, BookingStatus.CONFIRMED);
        int attendeeCount = confirmedBookings.size();

        // Included members: CONFIRMED bookings whose member currently has consent SHARE_TRAINER_SUMMARY AND a profile
        List<MemberHealthProfile> includedProfiles = new ArrayList<>();
        List<Long> includedMemberIds = new ArrayList<>();

        for (Booking booking : confirmedBookings) {
            Long memberId = booking.getMemberId();
            if (consentService.hasConsent(memberId, ConsentPurpose.SHARE_TRAINER_SUMMARY)) {
                Optional<MemberHealthProfile> profileOpt = profileRepository.findByMemberId(memberId);
                if (profileOpt.isPresent()) {
                    includedProfiles.add(profileOpt.get());
                    includedMemberIds.add(memberId);
                }
            }
        }

        // Rule: summaryAvailable=false and counts omitted when fewer than 3 members are included
        if (includedProfiles.size() < 3) {
            return new TrainerPrepResponse(
                    attendeeCount,
                    false,
                    Collections.emptyMap(),
                    Collections.emptyList(),
                    NOT_ENOUGH_DATA_NOTE
            );
        }

        // Tally injury counts and modifications anonymously
        Map<String, Integer> restrictionCounts = new LinkedHashMap<>();
        Set<InjuryArea> areasPresent = new LinkedHashSet<>();

        for (int i = 0; i < includedProfiles.size(); i++) {
            MemberHealthProfile entity = includedProfiles.get(i);
            Long memberId = includedMemberIds.get(i);

            // Log access per included member
            healthAccessService.logAccess(memberId, trainerId, HealthAccessAction.READ, "TRAINER_SUMMARY");

            FitnessProfile profile = healthDataCodec.decode(entity.getDataEncrypted());
            if (profile != null && profile.injuries() != null) {
                for (InjuryDto injury : profile.injuries()) {
                    InjuryArea area = injury.area();
                    restrictionCounts.merge(area.name(), 1, Integer::sum);
                    areasPresent.add(area);
                }
            }
        }

        // Build modifications list deduplicated by modification code
        List<SuggestedModificationDto> modifications = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();

        for (InjuryArea area : areasPresent) {
            List<SuggestedModificationDto> mods = ruleRegistry.getModifications(area);
            for (SuggestedModificationDto mod : mods) {
                if (seenCodes.add(mod.code())) {
                    modifications.add(mod);
                }
            }
        }

        return new TrainerPrepResponse(
                attendeeCount,
                true,
                restrictionCounts,
                modifications,
                null
        );
    }
}
