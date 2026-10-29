package com.fitflow.wellness;

import com.fitflow.booking.entity.Booking;
import com.fitflow.booking.model.BookingStatus;
import com.fitflow.booking.model.ConsumedFrom;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.common.error.ApiException;
import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.model.ClassImpactLevel;
import com.fitflow.fitnessclass.model.ClassIntensity;
import com.fitflow.fitnessclass.repository.FitnessClassRepository;
import com.fitflow.notification.entity.OutboxEvent;
import com.fitflow.notification.repository.OutboxEventRepository;
import com.fitflow.security.EncryptionService;
import com.fitflow.session.entity.ClassSession;
import com.fitflow.session.repository.ClassSessionRepository;
import com.fitflow.user.AgeService;
import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.wellness.dto.BodyMetricRequest;
import com.fitflow.wellness.dto.DeleteHealthDataRequest;
import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.HealthExportResponse;
import com.fitflow.wellness.dto.InjuryDto;
import com.fitflow.wellness.entity.ClassFeedback;
import com.fitflow.wellness.entity.HealthConsent;
import com.fitflow.wellness.entity.RestrictionProposal;
import com.fitflow.wellness.model.ConsentPurpose;
import com.fitflow.wellness.model.ExperienceLevel;
import com.fitflow.wellness.model.Goal;
import com.fitflow.wellness.model.HealthCondition;
import com.fitflow.wellness.model.InjuryArea;
import com.fitflow.wellness.model.InjurySeverity;
import com.fitflow.wellness.model.Sex;
import com.fitflow.wellness.repository.BodyMetricRepository;
import com.fitflow.wellness.repository.ClassFeedbackRepository;
import com.fitflow.wellness.repository.HealthAccessLogRepository;
import com.fitflow.wellness.repository.HealthConsentRepository;
import com.fitflow.wellness.repository.MemberHealthProfileRepository;
import com.fitflow.wellness.repository.ProgressLogRepository;
import com.fitflow.wellness.repository.RestrictionProposalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DataRightsTest {

    @Autowired
    private WellnessProfileService profileService;

    @Autowired
    private MemberHealthProfileRepository profileRepository;

    @Autowired
    private HealthConsentRepository consentRepository;

    @Autowired
    private BodyMetricRepository metricRepository;

    @Autowired
    private ProgressLogRepository progressRepository;

    @Autowired
    private ClassFeedbackRepository feedbackRepository;

    @Autowired
    private HealthAccessLogRepository accessLogRepository;

    @Autowired
    private RestrictionProposalRepository proposalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FitnessClassRepository fitnessClassRepository;

    @Autowired
    private ClassSessionRepository sessionRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    @Qualifier("mysqlDataSource")
    private DataSource mysqlDataSource;

    private Long memberId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("datarights." + System.currentTimeMillis() + "@example.com")
                .fullName("Data Rights User")
                .passwordHash("hash")
                .dateOfBirth(LocalDate.of(1990, 1, 1)) // 36 years old (18+)
                .status(User.Status.ACTIVE)
                .build());
        memberId = user.getId();

        // Grant consents
        consentRepository.save(HealthConsent.builder()
                .memberId(memberId)
                .consentVersion("v1")
                .purpose(ConsentPurpose.PERSONALIZED_CLASSES.name())
                .grantedAt(Instant.now())
                .createdAt(Instant.now())
                .build());

        consentRepository.save(HealthConsent.builder()
                .memberId(memberId)
                .consentVersion("v1")
                .purpose(ConsentPurpose.CALORIE_ESTIMATES.name())
                .grantedAt(Instant.now())
                .createdAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("Export returns complete decrypted dataset including profile, metrics, feedback, consents, and access logs")
    void testExportCompleteDataset() {
        // Create profile
        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.ADVANCED,
                "Exportable health notes",
                List.of(Goal.GENERAL_FITNESS),
                3,
                Collections.emptyList(),
                List.of(new InjuryDto(InjuryArea.ANKLE, InjurySeverity.MILD)),
                Set.of(HealthCondition.ASTHMA),
                Map.of("Q1", false),
                true,
                new BigDecimal("178.0"),
                new BigDecimal("72.5"),
                Sex.MALE,
                null,
                1
        );
        profileService.saveProfile(memberId, profile);

        // Record metrics
        profileService.recordMetric(memberId, new BodyMetricRequest(LocalDate.now(), new BigDecimal("72.0"), new BigDecimal("178.0")));

        // Export
        HealthExportResponse export = profileService.exportHealthData(memberId);

        assertNotNull(export.profile());
        assertEquals("Exportable health notes", export.profile().pastExperienceNote());
        assertEquals(new BigDecimal("72.0"), export.profile().weightKg());

        assertFalse(export.metrics().isEmpty());
        assertFalse(export.consentHistory().isEmpty());
        assertFalse(export.accessLogs().isEmpty());
    }

    @Test
    @DisplayName("Delete hard-deletes health profile, metrics, progress, proposals, nulls feedback, keeps consent history, and profile returns 404")
    void testDeleteHealthDataHardDeletesAndPreservesConsent() {
        // 1. Create health profile
        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.BEGINNER,
                "Delete me please",
                List.of(Goal.GENERAL_FITNESS),
                2,
                Collections.emptyList(),
                List.of(new InjuryDto(InjuryArea.KNEE, InjurySeverity.MODERATE)),
                Set.of(),
                Map.of(),
                false,
                new BigDecimal("170.0"),
                new BigDecimal("65.0"),
                Sex.FEMALE,
                null,
                1
        );
        profileService.saveProfile(memberId, profile);

        // 2. Add metrics and proposals
        profileService.recordMetric(memberId, new BodyMetricRequest(LocalDate.now(), new BigDecimal("65.0"), new BigDecimal("170.0")));
        proposalRepository.save(RestrictionProposal.builder()
                .memberId(memberId).area("KNEE").suggestedSeverity("MILD").status("PENDING").createdAt(Instant.now()).build());

        // 3. Add feedback
        FitnessClass fc = fitnessClassRepository.save(FitnessClass.builder()
                .name("DataRights Yoga " + System.currentTimeMillis())
                .category("YOGA")
                .durationMinutes(50)
                .intensity(ClassIntensity.LOW)
                .impactLevel(ClassImpactLevel.LOW)
                .metValue(new BigDecimal("3.0"))
                .createdBy(memberId)
                .build());

        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(fc.getId())
                .trainerId(memberId)
                .startsAt(Instant.now().minus(java.time.Duration.ofHours(2)))
                .endsAt(Instant.now().minus(java.time.Duration.ofHours(1)))
                .capacity(10)
                .bookedCount(1)
                .room("Room 1")
                .status(ClassSession.Status.COMPLETED)
                .trainerRateSnapshotPaise(50000L)
                .build());

        Booking booking = bookingRepository.save(Booking.builder()
                .sessionId(session.getId())
                .memberId(memberId)
                .status(BookingStatus.ATTENDED)
                .consumedFrom(ConsumedFrom.FREE_SESSION)
                .bookedAt(Instant.now().minus(java.time.Duration.ofDays(1)))
                .version(0)
                .createdAt(Instant.now())
                .build());

        byte[] encComment = encryptionService.encrypt("Pain during squats".getBytes(StandardCharsets.UTF_8));
        ClassFeedback fb = feedbackRepository.save(ClassFeedback.builder()
                .bookingId(booking.getId())
                .memberId(memberId)
                .rating(4)
                .feltPain(true)
                .painAreaCode("KNEE")
                .commentEnc(encComment)
                .createdAt(Instant.now())
                .build());

        // 4. Execute Delete
        DeleteHealthDataRequest delReq = new DeleteHealthDataRequest("DELETE MY HEALTH DATA");
        profileService.deleteHealthData(memberId, delReq);

        // 5. Raw SQL checks confirming hard deletion
        JdbcTemplate jdbcTemplate = new JdbcTemplate(mysqlDataSource);

        Integer profileCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member_health_profiles WHERE member_id = ?", Integer.class, memberId);
        assertEquals(0, profileCount, "member_health_profiles must be 0 after delete");

        Integer metricsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM body_metrics WHERE member_id = ?", Integer.class, memberId);
        assertEquals(0, metricsCount, "body_metrics must be 0 after delete");

        Integer proposalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM restriction_proposals WHERE member_id = ?", Integer.class, memberId);
        assertEquals(0, proposalCount, "restriction_proposals must be 0 after delete");

        // 6. Verify feedback comments and pain areas are nulled
        ClassFeedback refreshedFb = feedbackRepository.findById(fb.getId()).orElseThrow();
        assertNull(refreshedFb.getCommentEnc(), "Comment must be nulled");
        assertNull(refreshedFb.getPainAreaCode(), "Pain area must be nulled");
        assertEquals(4, refreshedFb.getRating(), "Rating should be preserved");

        // 7. Verify consent history is PRESERVED for audit compliance
        Integer consentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM health_consents WHERE member_id = ?", Integer.class, memberId);
        assertTrue(consentCount >= 2, "health_consents must be preserved");

        // 8. Verify outbox event health.data_deleted
        List<OutboxEvent> events = outboxEventRepository.findByAggregateIdAndEventType(memberId, "health.data_deleted");
        assertFalse(events.isEmpty(), "health.data_deleted event must be present");

        // 9. Profile endpoint returns 404 NOT_FOUND after deletion
        ApiException ex = assertThrows(ApiException.class, () -> profileService.getProfile(memberId));
        assertEquals(404, ex.getStatus().value(), "Profile endpoint must return 404 after deletion");
    }
}
