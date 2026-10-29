package com.fitflow.wellness;

import com.fitflow.common.error.ApiException;
import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.repository.FitnessClassRepository;
import com.fitflow.notification.OutboxService;
import com.fitflow.security.EncryptionService;
import com.fitflow.session.dto.SessionDetailResponse;
import com.fitflow.session.entity.ClassSession;
import com.fitflow.session.repository.ClassSessionRepository;
import com.fitflow.user.AgeService;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.entity.User;
import com.fitflow.wellness.dto.BodyMetricRequest;
import com.fitflow.wellness.dto.BodyMetricResponse;
import com.fitflow.wellness.dto.DeleteHealthDataRequest;
import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.HealthExportResponse;
import com.fitflow.wellness.dto.LlmSafeProfile;
import com.fitflow.wellness.dto.ProfileResponse;
import com.fitflow.wellness.dto.SafeClassesResponse;
import com.fitflow.wellness.dto.SafeSessionItem;
import com.fitflow.wellness.dto.SafetyVerdict;
import com.fitflow.wellness.dto.ScreeningResult;
import com.fitflow.wellness.dto.WellnessMetadataResponse;
import com.fitflow.wellness.entity.BodyMetric;
import com.fitflow.wellness.entity.ClassFeedback;
import com.fitflow.wellness.entity.HealthAccessLog;
import com.fitflow.wellness.entity.HealthConsent;
import com.fitflow.wellness.entity.MemberHealthProfile;
import com.fitflow.wellness.entity.ProgressLog;
import com.fitflow.wellness.model.ConsentPurpose;
import com.fitflow.wellness.model.DietPreference;
import com.fitflow.wellness.model.ExperienceLevel;
import com.fitflow.wellness.model.Goal;
import com.fitflow.wellness.model.HealthAccessAction;
import com.fitflow.wellness.model.HealthCondition;
import com.fitflow.wellness.model.InjuryArea;
import com.fitflow.wellness.model.InjurySeverity;
import com.fitflow.wellness.model.ParqQuestions;
import com.fitflow.wellness.model.SafetyVerdictType;
import com.fitflow.wellness.model.Sex;
import com.fitflow.wellness.repository.BodyMetricRepository;
import com.fitflow.wellness.repository.ClassFeedbackRepository;
import com.fitflow.wellness.repository.HealthAccessLogRepository;
import com.fitflow.wellness.repository.HealthConsentRepository;
import com.fitflow.wellness.repository.MemberHealthProfileRepository;
import com.fitflow.wellness.repository.ProgressLogRepository;
import com.fitflow.wellness.repository.RestrictionProposalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WellnessProfileService {

    public static final String DELETE_CONFIRM_KEY = "DELETE MY HEALTH DATA";

    private final MemberHealthProfileRepository profileRepository;
    private final HealthConsentRepository consentRepository;
    private final BodyMetricRepository bodyMetricRepository;
    private final ProgressLogRepository progressLogRepository;
    private final ClassFeedbackRepository feedbackRepository;
    private final HealthAccessLogRepository accessLogRepository;
    private final RestrictionProposalRepository proposalRepository;
    private final FitnessClassRepository fitnessClassRepository;
    private final ClassSessionRepository classSessionRepository;
    private final UserRepository userRepository;
    private final HealthDataCodec healthDataCodec;
    private final ScreeningService screeningService;
    private final SafetyFilter safetyFilter;
    private final ConsentService consentService;
    private final AgeService ageService;
    private final EncryptionService encryptionService;
    private final OutboxService outboxService;
    private final HealthAccessService healthAccessService;
    private final Clock clock;

    public WellnessMetadataResponse getMetadata() {
        return new WellnessMetadataResponse(
                Arrays.asList(ExperienceLevel.values()),
                Arrays.asList(Goal.values()),
                Arrays.asList(Sex.values()),
                Arrays.asList(InjuryArea.values()),
                Arrays.asList(InjurySeverity.values()),
                Arrays.asList(HealthCondition.values()),
                Arrays.asList(DietPreference.values()),
                ParqQuestions.getQuestions()
        );
    }

    @Transactional
    public ProfileResponse saveProfile(Long memberId, FitnessProfile request) {
        // 1. Consent check: PERSONALIZED_CLASSES or CALORIE_ESTIMATES
        boolean hasPersonalized = consentService.hasConsent(memberId, ConsentPurpose.PERSONALIZED_CLASSES);
        boolean hasCalories = consentService.hasConsent(memberId, ConsentPurpose.CALORIE_ESTIMATES);
        if (!hasPersonalized && !hasCalories) {
            throw ApiException.consentRequired("Health profile requires consent for PERSONALIZED_CLASSES or CALORIE_ESTIMATES");
        }

        // 2. Age check: 18+
        User user = userRepository.findById(memberId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (!ageService.is18Plus(user.getDateOfBirth())) {
            throw ApiException.ageRestrictedForbidden("Age restricted: must be at least 18 years old to create a health profile");
        }

        // 3. Encode & Encrypt Profile blob
        byte[] encodedData = healthDataCodec.encode(request);

        Optional<MemberHealthProfile> existingOpt = profileRepository.findByMemberId(memberId);
        boolean isFirstTime = existingOpt.isEmpty();

        MemberHealthProfile profileEntity;
        if (isFirstTime) {
            profileEntity = MemberHealthProfile.builder()
                    .memberId(memberId)
                    .dataEncrypted(encodedData)
                    .schemaVersion(1)
                    .version(0)
                    .createdAt(clock.instant())
                    .build();
        } else {
            profileEntity = existingOpt.get();
            profileEntity.setDataEncrypted(encodedData);
            profileEntity.setUpdatedAt(clock.instant());
        }
        profileRepository.save(profileEntity);

        // 4. Sync latest weight and height into body_metrics history
        if (request.weightKg() != null) {
            byte[] weightEnc = encryptionService.encrypt(request.weightKg().toString().getBytes(StandardCharsets.UTF_8));
            byte[] heightEnc = request.heightCm() != null
                    ? encryptionService.encrypt(request.heightCm().toString().getBytes(StandardCharsets.UTF_8))
                    : null;
            BodyMetric metric = BodyMetric.builder()
                    .memberId(memberId)
                    .recordedOn(LocalDate.now(clock))
                    .weightKgEnc(weightEnc)
                    .heightCmEnc(heightEnc)
                    .createdAt(clock.instant())
                    .build();
            bodyMetricRepository.save(metric);
        }

        // 5. Emit Outbox Event (no health content in payload)
        if (isFirstTime) {
            outboxService.append("MEMBER", memberId, "profile.completed", Map.of("memberId", memberId));
        } else {
            outboxService.append("MEMBER", memberId, "profile.updated", Map.of("memberId", memberId));
        }

        // 6. Access log
        healthAccessService.logAccess(memberId, memberId, HealthAccessAction.WRITE, "PROFILE_SAVE");

        // 7. Screening result
        ScreeningResult screening = screeningService.evaluate(request);

        return new ProfileResponse(request, screening);
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long memberId) {
        boolean hasPersonalized = consentService.hasConsent(memberId, ConsentPurpose.PERSONALIZED_CLASSES);
        boolean hasCalories = consentService.hasConsent(memberId, ConsentPurpose.CALORIE_ESTIMATES);
        if (!hasPersonalized && !hasCalories) {
            throw ApiException.consentRequired("Viewing health profile requires consent for PERSONALIZED_CLASSES or CALORIE_ESTIMATES");
        }

        MemberHealthProfile entity = profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> ApiException.notFound("Health profile not found for member: " + memberId));

        FitnessProfile profile = healthDataCodec.decode(entity.getDataEncrypted());
        ScreeningResult screening = screeningService.evaluate(profile);

        healthAccessService.logAccess(memberId, memberId, HealthAccessAction.READ, "PROFILE_VIEW");

        return new ProfileResponse(profile, screening);
    }

    @Transactional(readOnly = true)
    public Optional<LlmSafeProfile> getLlmSafeProfile(Long memberId) {
        boolean hasConsent = consentService.hasConsent(memberId, ConsentPurpose.AI_ASSISTANT_PROCESSING);
        if (!hasConsent) {
            return Optional.empty();
        }

        Optional<MemberHealthProfile> entityOpt = profileRepository.findByMemberId(memberId);
        if (entityOpt.isEmpty()) {
            return Optional.empty();
        }

        FitnessProfile profile = healthDataCodec.decode(entityOpt.get().getDataEncrypted());
        ScreeningResult screening = screeningService.evaluate(profile);

        User user = userRepository.findById(memberId).orElse(null);
        String ageBand = "UNKNOWN";
        if (user != null && user.getDateOfBirth() != null) {
            int age = ageService.calculateAge(user.getDateOfBirth());
            if (age < 30) ageBand = "18-29";
            else if (age < 40) ageBand = "30-39";
            else if (age < 50) ageBand = "40-49";
            else ageBand = "50+";
        }

        List<String> injuryCodes = profile.injuries() != null
                ? profile.injuries().stream().map(i -> i.area() + ":" + i.severity()).toList()
                : List.of();
        List<String> conditionCodes = profile.conditions() != null
                ? profile.conditions().stream().map(Enum::name).toList()
                : List.of();

        healthAccessService.logAccess(memberId, memberId, HealthAccessAction.READ, "LLM_SAFE_PROFILE");

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

    @Transactional
    public BodyMetricResponse recordMetric(Long memberId, BodyMetricRequest request) {
        boolean hasPersonalized = consentService.hasConsent(memberId, ConsentPurpose.PERSONALIZED_CLASSES);
        boolean hasCalories = consentService.hasConsent(memberId, ConsentPurpose.CALORIE_ESTIMATES);
        if (!hasPersonalized && !hasCalories) {
            throw ApiException.consentRequired("Recording body metrics requires health consent");
        }

        byte[] weightEnc = encryptionService.encrypt(request.weightKg().toString().getBytes(StandardCharsets.UTF_8));
        byte[] heightEnc = request.heightCm() != null
                ? encryptionService.encrypt(request.heightCm().toString().getBytes(StandardCharsets.UTF_8))
                : null;

        BodyMetric metric = BodyMetric.builder()
                .memberId(memberId)
                .recordedOn(request.recordedOn())
                .weightKgEnc(weightEnc)
                .heightCmEnc(heightEnc)
                .createdAt(clock.instant())
                .build();
        BodyMetric saved = bodyMetricRepository.save(metric);

        // Update health profile with latest weight and height if profile exists
        Optional<MemberHealthProfile> profileOpt = profileRepository.findByMemberId(memberId);
        if (profileOpt.isPresent()) {
            MemberHealthProfile entity = profileOpt.get();
            FitnessProfile current = healthDataCodec.decode(entity.getDataEncrypted());
            if (current != null) {
                BigDecimal newHeight = request.heightCm() != null ? request.heightCm() : current.heightCm();
                FitnessProfile updated = new FitnessProfile(
                        current.experienceLevel(),
                        current.pastExperienceNote(),
                        current.goals(),
                        current.sessionsPerWeekPlanned(),
                        current.availability(),
                        current.injuries(),
                        current.conditions(),
                        current.parq(),
                        current.medicalClearanceAttested(),
                        newHeight,
                        request.weightKg(),
                        current.sex(),
                        current.dietPreference(),
                        current.version()
                );
                entity.setDataEncrypted(healthDataCodec.encode(updated));
                entity.setUpdatedAt(clock.instant());
                profileRepository.save(entity);
            }
        }

        healthAccessService.logAccess(memberId, memberId, HealthAccessAction.WRITE, "METRIC_RECORD");

        return new BodyMetricResponse(
                saved.getId(),
                saved.getRecordedOn(),
                request.weightKg(),
                request.heightCm(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<BodyMetricResponse> getMetrics(Long memberId, LocalDate from, LocalDate to) {
        boolean hasPersonalized = consentService.hasConsent(memberId, ConsentPurpose.PERSONALIZED_CLASSES);
        boolean hasCalories = consentService.hasConsent(memberId, ConsentPurpose.CALORIE_ESTIMATES);
        if (!hasPersonalized && !hasCalories) {
            throw ApiException.consentRequired("Viewing body metrics requires health consent");
        }

        List<BodyMetric> list;
        if (from != null && to != null) {
            list = bodyMetricRepository.findAllByMemberIdAndRecordedOnBetweenOrderByRecordedOnDesc(memberId, from, to);
        } else {
            list = bodyMetricRepository.findAllByMemberIdOrderByRecordedOnDesc(memberId);
        }

        List<BodyMetricResponse> results = new ArrayList<>();
        for (BodyMetric bm : list) {
            BigDecimal weight = decryptDecimal(bm.getWeightKgEnc());
            BigDecimal height = bm.getHeightCmEnc() != null ? decryptDecimal(bm.getHeightCmEnc()) : null;
            results.add(new BodyMetricResponse(bm.getId(), bm.getRecordedOn(), weight, height, bm.getCreatedAt()));
        }

        healthAccessService.logAccess(memberId, memberId, HealthAccessAction.READ, "METRICS_VIEW");
        return results;
    }

    @Transactional(readOnly = true)
    public HealthExportResponse exportHealthData(Long memberId) {
        // Fetch decrypted profile if present
        FitnessProfile profile = null;
        Optional<MemberHealthProfile> profOpt = profileRepository.findByMemberId(memberId);
        if (profOpt.isPresent()) {
            profile = healthDataCodec.decode(profOpt.get().getDataEncrypted());
        }

        // Fetch decrypted metrics
        List<BodyMetric> metricEntities = bodyMetricRepository.findAllByMemberIdOrderByRecordedOnDesc(memberId);
        List<BodyMetricResponse> metrics = new ArrayList<>();
        for (BodyMetric bm : metricEntities) {
            BigDecimal weight = decryptDecimal(bm.getWeightKgEnc());
            BigDecimal height = bm.getHeightCmEnc() != null ? decryptDecimal(bm.getHeightCmEnc()) : null;
            metrics.add(new BodyMetricResponse(bm.getId(), bm.getRecordedOn(), weight, height, bm.getCreatedAt()));
        }

        // Fetch decrypted progress logs
        List<ProgressLog> logEntities = progressLogRepository.findAllByMemberIdOrderByLogDateDesc(memberId);
        List<HealthExportResponse.ExportProgressItem> progress = new ArrayList<>();
        for (ProgressLog pl : logEntities) {
            String val = pl.getValueEnc() != null ? decryptString(pl.getValueEnc()) : null;
            String note = pl.getNoteEnc() != null ? decryptString(pl.getNoteEnc()) : null;
            progress.add(new HealthExportResponse.ExportProgressItem(
                    pl.getId(), pl.getLogDate().toString(), pl.getKind(), val, note, pl.getCreatedAt()));
        }

        // Fetch decrypted feedback
        List<ClassFeedback> fbEntities = feedbackRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId);
        List<HealthExportResponse.ExportFeedbackItem> feedback = new ArrayList<>();
        for (ClassFeedback cf : fbEntities) {
            String comment = cf.getCommentEnc() != null ? decryptString(cf.getCommentEnc()) : null;
            feedback.add(new HealthExportResponse.ExportFeedbackItem(
                    cf.getId(), cf.getBookingId(), cf.getRating(),
                    Boolean.TRUE.equals(cf.getFeltPain()), cf.getPainAreaCode(), comment, cf.getCreatedAt()));
        }

        // Fetch consent history
        List<HealthConsent> consentEntities = consentRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId);
        List<HealthExportResponse.ExportConsentItem> consentHistory = consentEntities.stream()
                .map(c -> new HealthExportResponse.ExportConsentItem(
                        c.getId(), c.getConsentVersion(), c.getPurpose(), c.getGrantedAt(), c.getRevokedAt(), c.getCreatedAt()))
                .toList();

        // Fetch access logs
        List<HealthAccessLog> logRows = accessLogRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId);
        List<HealthExportResponse.ExportAccessLogItem> accessLogs = logRows.stream()
                .map(l -> new HealthExportResponse.ExportAccessLogItem(
                        l.getId(), l.getActorUserId(), l.getAction().name(), l.getPurpose(), l.getCreatedAt()))
                .toList();

        healthAccessService.logAccess(memberId, memberId, HealthAccessAction.EXPORT, "DATA_EXPORT");

        return new HealthExportResponse(profile, metrics, progress, feedback, consentHistory, accessLogs);
    }

    @Transactional
    public void deleteHealthData(Long memberId, DeleteHealthDataRequest request) {
        if (!DELETE_CONFIRM_KEY.equals(request.confirm())) {
            throw ApiException.badRequest("Confirmation key must exactly match '" + DELETE_CONFIRM_KEY + "'");
        }

        // Hard delete profile, metrics, progress, proposals
        profileRepository.deleteByMemberId(memberId);
        bodyMetricRepository.deleteAllByMemberId(memberId);
        progressLogRepository.deleteAllByMemberId(memberId);
        proposalRepository.deleteAllByMemberId(memberId);

        // Null encrypted pain and comment fields in feedback (keep rating and booking link)
        List<ClassFeedback> feedbacks = feedbackRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId);
        for (ClassFeedback cf : feedbacks) {
            cf.setCommentEnc(null);
            cf.setPainAreaCode(null);
            feedbackRepository.save(cf);
        }

        feedbackRepository.flush();

        // Consent history is intentionally kept for compliance auditing
        healthAccessService.logAccess(memberId, memberId, HealthAccessAction.DELETE, "DATA_DELETION");

        // Emit outbox event
        outboxService.append("MEMBER", memberId, "health.data_deleted", Map.of("memberId", memberId));
    }

    @Transactional(readOnly = true)
    public SafeClassesResponse getSafeClasses(Long memberId, Instant from, Instant to, int page, int size) {
        consentService.checkConsent(memberId, ConsentPurpose.PERSONALIZED_CLASSES);

        MemberHealthProfile entity = profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> ApiException.notFound("Health profile not found. Please create your profile first."));

        FitnessProfile profile = healthDataCodec.decode(entity.getDataEncrypted());
        ScreeningResult screening = screeningService.evaluate(profile);

        Instant searchFrom = from != null ? from : clock.instant();
        Instant searchTo = to != null ? to : searchFrom.plusSeconds(7 * 24 * 3600);

        List<ClassSession> sessions = classSessionRepository.findByStartsAtBetween(searchFrom, searchTo)
                .stream()
                .filter(s -> s.getStatus() == ClassSession.Status.SCHEDULED)
                .sorted(Comparator.comparing(ClassSession::getStartsAt))
                .toList();

        List<SafeSessionItem> eligible = new ArrayList<>();
        List<SafeSessionItem> avoid = new ArrayList<>();

        for (ClassSession session : sessions) {
            FitnessClass fc = fitnessClassRepository.findById(session.getClassId()).orElse(null);
            if (fc == null) {
                continue;
            }

            SafetyVerdict verdict = safetyFilter.evaluate(profile, fc);
            SessionDetailResponse sessionDetail = mapToSessionDetail(session, fc);
            SafeSessionItem item = new SafeSessionItem(
                    sessionDetail,
                    verdict.verdict(),
                    verdict.reasonCodes(),
                    verdict.humanReasons()
            );

            if (verdict.verdict() == SafetyVerdictType.BLOCKED) {
                avoid.add(item);
            } else {
                eligible.add(item);
            }
        }

        String note = null;
        if (screening.doctorCheckRecommended() && !profile.isMedicalClearanceAttested()) {
            note = "Please check with a doctor before starting. We are only showing very gentle options.";
        }

        healthAccessService.logAccess(memberId, memberId, HealthAccessAction.READ, "SAFE_CLASSES_QUERY");

        return new SafeClassesResponse(
                screening.doctorCheckRecommended(),
                SafetyRuleRegistry.DISCLAIMER,
                note,
                eligible,
                avoid
        );
    }

    @Transactional(readOnly = true)
    public SafetyVerdict checkClassSafety(Long memberId, Long classId) {
        consentService.checkConsent(memberId, ConsentPurpose.PERSONALIZED_CLASSES);

        FitnessClass fitnessClass = fitnessClassRepository.findById(classId)
                .orElseThrow(() -> ApiException.notFound("Fitness class not found"));

        MemberHealthProfile entity = profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> ApiException.notFound("Health profile not found"));

        FitnessProfile profile = healthDataCodec.decode(entity.getDataEncrypted());
        healthAccessService.logAccess(memberId, memberId, HealthAccessAction.READ, "CLASS_SAFETY_CHECK");

        return safetyFilter.evaluate(profile, fitnessClass);
    }

    private BigDecimal decryptDecimal(byte[] cipherBytes) {
        try {
            byte[] plain = encryptionService.decrypt(cipherBytes);
            return new BigDecimal(new String(plain, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private String decryptString(byte[] cipherBytes) {
        try {
            byte[] plain = encryptionService.decrypt(cipherBytes);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private SessionDetailResponse mapToSessionDetail(ClassSession session, FitnessClass fc) {
        User trainer = userRepository.findById(session.getTrainerId()).orElse(null);
        String trainerName = trainer != null ? trainer.getFullName() : "Trainer";
        int seatsLeft = Math.max(0, session.getCapacity() - session.getBookedCount());

        return new SessionDetailResponse(
                session.getId(),
                session.getClassId(),
                fc.getName(),
                fc.getCategory(),
                fc.getDurationMinutes(),
                fc.getIntensity().name(),
                fc.getImpactLevel().name(),
                session.getTrainerId(),
                trainerName,
                session.getStartsAt(),
                session.getEndsAt(),
                session.getRoom(),
                session.getCapacity(),
                session.getBookedCount(),
                seatsLeft,
                session.getStatus(),
                session.getSeriesId(),
                session.getTrainerRateSnapshotPaise(),
                null,
                session.getVersion(),
                session.getCreatedAt()
        );
    }
}
