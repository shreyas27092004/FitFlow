package com.fitflow.trainer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.common.error.ApiException;
import com.fitflow.trainer.dto.AvailabilitySlotDto;
import com.fitflow.trainer.dto.TrainerProfileResponse;
import com.fitflow.trainer.dto.TrainerPublicResponse;
import com.fitflow.trainer.dto.TrainerSummaryResponse;
import com.fitflow.trainer.dto.UpdateTrainerProfileRequest;
import com.fitflow.trainer.entity.TrainerProfile;
import com.fitflow.trainer.repository.TrainerProfileRepository;
import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainerService {

    private final TrainerProfileRepository trainerProfileRepository;
    private final UserRepository userRepository;
    private final TrainerAvailabilityValidator trainerAvailabilityValidator;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<TrainerPublicResponse> listPublicTrainers() {
        List<TrainerProfile> profiles = trainerProfileRepository.findByActiveTrue();
        List<TrainerPublicResponse> results = new ArrayList<>();

        for (TrainerProfile profile : profiles) {
            userRepository.findById(profile.getUserId())
                    .filter(u -> u.getStatus() == User.Status.ACTIVE)
                    .ifPresent(user -> results.add(new TrainerPublicResponse(
                            profile.getUserId(),
                            user.getFullName(),
                            profile.getBio(),
                            parseStringList(profile.getSpecialties())
                    )));
        }

        return results;
    }

    @Transactional(readOnly = true)
    public TrainerPublicResponse getPublicTrainer(Long id) {
        TrainerProfile profile = trainerProfileRepository.findById(id)
                .filter(TrainerProfile::getActive)
                .orElseThrow(() -> ApiException.notFound("Trainer not found with id " + id));

        User user = userRepository.findById(id)
                .filter(u -> u.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Trainer not found with id " + id));

        return new TrainerPublicResponse(
                profile.getUserId(),
                user.getFullName(),
                profile.getBio(),
                parseStringList(profile.getSpecialties())
        );
    }

    @Transactional(readOnly = true)
    public TrainerProfileResponse getMyTrainerProfile(Long userId) {
        TrainerProfile profile = trainerProfileRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Trainer profile not found for user id " + userId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found with id " + userId));

        return toFullResponse(profile, user);
    }

    @Transactional
    public TrainerProfileResponse updateMyTrainerProfile(Long userId, UpdateTrainerProfileRequest request) {
        // Sending rate returns 403 FORBIDDEN
        if (request.ratePerSessionPaise() != null) {
            throw ApiException.forbidden("Trainers cannot modify their own session rates");
        }

        TrainerProfile profile = trainerProfileRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Trainer profile not found for user id " + userId));

        if (!profile.getVersion().equals(request.version())) {
            throw ApiException.optimisticLock("Trainer profile was modified concurrently");
        }

        if (request.availability() != null) {
            trainerAvailabilityValidator.validate(request.availability());
            profile.setAvailability(toJson(request.availability()));
        }

        if (request.bio() != null) {
            profile.setBio(request.bio());
        }

        if (request.specialties() != null) {
            profile.setSpecialties(toJson(request.specialties()));
        }

        if (request.certifications() != null) {
            profile.setCertifications(toJson(request.certifications()));
        }

        TrainerProfile saved = trainerProfileRepository.saveAndFlush(profile);
        log.info("Trainer profile updated for user id {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found with id " + userId));

        return toFullResponse(saved, user);
    }

    @Transactional(readOnly = true)
    public TrainerSummaryResponse getTrainerSummary(Long userId) {
        // Documented in CONTRACT.md: earnings is null until Stage 5 payouts engine is introduced
        return new TrainerSummaryResponse(0, null);
    }

    public TrainerProfileResponse toFullResponse(TrainerProfile profile, User user) {
        return new TrainerProfileResponse(
                profile.getUserId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                profile.getBio(),
                parseStringList(profile.getSpecialties()),
                parseStringList(profile.getCertifications()),
                profile.getRatePerSessionPaise(),
                parseAvailability(profile.getAvailability()),
                profile.getActive(),
                profile.getVersion()
        );
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<AvailabilitySlotDto> parseAvailability(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<AvailabilitySlotDto>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize trainer profile json", e);
            throw ApiException.internalError("Failed to serialize profile data");
        }
    }
}
