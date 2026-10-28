package com.fitflow.wellness;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.common.error.ApiException;
import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.model.ClassImpactLevel;
import com.fitflow.fitnessclass.model.ClassIntensity;
import com.fitflow.notification.OutboxService;
import com.fitflow.security.EncryptionService;
import com.fitflow.user.AgeService;
import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.InjuryDto;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WellnessLogSecurityTest {

    private ListAppender<ILoggingEvent> listAppender;
    private Logger rootLogger;

    private MemberHealthProfileRepository profileRepository;
    private HealthConsentRepository consentRepository;
    private BodyMetricRepository bodyMetricRepository;
    private ProgressLogRepository progressLogRepository;
    private ClassFeedbackRepository feedbackRepository;
    private HealthAccessLogRepository accessLogRepository;
    private RestrictionProposalRepository proposalRepository;
    private UserRepository userRepository;
    private EncryptionService encryptionService;
    private OutboxService outboxService;
    private HealthAccessService healthAccessService;
    private ConsentService consentService;
    private AgeService ageService;
    private Clock clock;
    private HealthDataCodec healthDataCodec;
    private ScreeningService screeningService;
    private SafetyRuleRegistry ruleRegistry;
    private SafetyFilter safetyFilter;
    private WellnessProfileService wellnessProfileService;

    private final Instant baseTime = Instant.parse("2026-10-28T10:00:00Z");

    @BeforeEach
    void setUp() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        listAppender = new ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);

        clock = Clock.fixed(baseTime, ZoneOffset.UTC);
        ObjectMapper objectMapper = new ObjectMapper();

        profileRepository = mock(MemberHealthProfileRepository.class);
        consentRepository = mock(HealthConsentRepository.class);
        bodyMetricRepository = mock(BodyMetricRepository.class);
        progressLogRepository = mock(ProgressLogRepository.class);
        feedbackRepository = mock(ClassFeedbackRepository.class);
        accessLogRepository = mock(HealthAccessLogRepository.class);
        proposalRepository = mock(RestrictionProposalRepository.class);
        userRepository = mock(UserRepository.class);
        encryptionService = mock(EncryptionService.class);
        outboxService = mock(OutboxService.class);
        ageService = mock(AgeService.class);

        when(encryptionService.encrypt(any())).thenAnswer(inv -> ((byte[]) inv.getArgument(0)).clone());
        when(encryptionService.decrypt(any())).thenAnswer(inv -> ((byte[]) inv.getArgument(0)).clone());

        healthDataCodec = new HealthDataCodec(encryptionService, objectMapper);
        screeningService = new ScreeningService();
        healthAccessService = new HealthAccessService(accessLogRepository, clock);

        consentService = mock(ConsentService.class);
        when(consentService.hasConsent(any(), any())).thenReturn(true);

        ruleRegistry = new SafetyRuleRegistry();
        ruleRegistry.init();
        safetyFilter = new SafetyFilter(ruleRegistry, screeningService, objectMapper);

        wellnessProfileService = new WellnessProfileService(
                profileRepository,
                consentRepository,
                bodyMetricRepository,
                progressLogRepository,
                feedbackRepository,
                accessLogRepository,
                proposalRepository,
                null,
                null,
                userRepository,
                healthDataCodec,
                screeningService,
                safetyFilter,
                consentService,
                ageService,
                encryptionService,
                outboxService,
                healthAccessService,
                clock
        );
    }

    @AfterEach
    void tearDown() {
        if (rootLogger != null && listAppender != null) {
            rootLogger.detachAppender(listAppender);
        }
    }

    @Test
    @DisplayName("Assert no sensitive health data or private notes appear in logs across wellness profile operations")
    void testNoHealthDataInLogs() {
        Long memberId = 888L;
        String secretHealthNote = "CONFIDENTIAL_TORN_ROTATOR_CUFF_AND_ASTHMA_999";

        User member = User.builder()
                .email("test.member@example.com")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .status(User.Status.ACTIVE)
                .build();
        ReflectionTestUtils.setField(member, "id", memberId);
        when(userRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(ageService.is18Plus(any())).thenReturn(true);

        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.ADVANCED,
                secretHealthNote,
                List.of(Goal.GENERAL_FITNESS),
                3,
                Collections.emptyList(),
                List.of(new InjuryDto(InjuryArea.SHOULDER, InjurySeverity.SEVERE)),
                Set.of(HealthCondition.ASTHMA),
                Map.of("Q2", true),
                false,
                new BigDecimal("180.0"),
                new BigDecimal("75.0"),
                Sex.MALE,
                null,
                1
        );

        wellnessProfileService.saveProfile(memberId, profile);

        // Verify logs
        List<ILoggingEvent> events = listAppender.list;
        assertFalse(events.isEmpty(), "Log events should be recorded");

        for (ILoggingEvent event : events) {
            String msg = event.getFormattedMessage();
            assertFalse(msg.contains(secretHealthNote), "Logs must never contain past experience note");
            assertFalse(msg.contains("TORN_ROTATOR_CUFF"), "Logs must never contain free text health terms");
            assertFalse(msg.contains("SHOULDER:SEVERE"), "Logs must never contain specific injury severity strings");
        }
    }
}
