package com.fitflow.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.common.error.ApiException;
import com.fitflow.security.UserPrincipal;
import com.fitflow.trainer.TrainerService;
import com.fitflow.trainer.dto.TrainerProfileResponse;
import com.fitflow.trainer.dto.UpdateTrainerRateRequest;
import com.fitflow.trainer.entity.TrainerProfile;
import com.fitflow.trainer.repository.TrainerProfileRepository;
import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTrainerService {

    private final TrainerProfileRepository trainerProfileRepository;
    private final UserRepository userRepository;
    private final TrainerService trainerService;
    private final AdminActionLogger adminActionLogger;
    private final ObjectMapper objectMapper;

    @Transactional
    public TrainerProfileResponse updateTrainerRate(
            UserPrincipal actor,
            Long trainerId,
            UpdateTrainerRateRequest request,
            String ipHash) {

        TrainerProfile profile = trainerProfileRepository.findById(trainerId)
                .orElseThrow(() -> ApiException.notFound("Trainer profile not found with id " + trainerId));

        if (!profile.getVersion().equals(request.version())) {
            throw ApiException.optimisticLock("Trainer profile was modified concurrently");
        }

        Map<String, Object> beforeSummary = Map.of("ratePerSessionPaise", profile.getRatePerSessionPaise());

        profile.setRatePerSessionPaise(request.ratePerSessionPaise());
        TrainerProfile saved = trainerProfileRepository.saveAndFlush(profile);

        Map<String, Object> afterSummary = Map.of("ratePerSessionPaise", saved.getRatePerSessionPaise());

        adminActionLogger.logAction(
                actor.getId(),
                "TRAINER_RATE_UPDATE",
                "TRAINER_PROFILE",
                trainerId,
                toJson(beforeSummary),
                toJson(afterSummary),
                ipHash
        );

        log.info("Trainer id {} session rate updated to {} paise by admin id {}",
                trainerId, request.ratePerSessionPaise(), actor.getId());

        User user = userRepository.findById(trainerId)
                .orElseThrow(() -> ApiException.notFound("User not found with id " + trainerId));

        return trainerService.toFullResponse(saved, user);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit json", e);
            return "{}";
        }
    }
}
