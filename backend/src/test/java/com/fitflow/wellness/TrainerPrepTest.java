package com.fitflow.wellness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.booking.entity.Booking;
import com.fitflow.booking.model.BookingStatus;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.common.error.ApiException;
import com.fitflow.security.EncryptionService;
import com.fitflow.session.entity.ClassSession;
import com.fitflow.session.repository.ClassSessionRepository;
import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.InjuryDto;
import com.fitflow.wellness.dto.TrainerPrepResponse;
import com.fitflow.wellness.entity.MemberHealthProfile;
import com.fitflow.wellness.model.ConsentPurpose;
import com.fitflow.wellness.model.ExperienceLevel;
import com.fitflow.wellness.model.Goal;
import com.fitflow.wellness.model.HealthAccessAction;
import com.fitflow.wellness.model.InjuryArea;
import com.fitflow.wellness.model.InjurySeverity;
import com.fitflow.wellness.model.Sex;
import com.fitflow.wellness.repository.HealthAccessLogRepository;
import com.fitflow.wellness.repository.MemberHealthProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrainerPrepTest {

    private ClassSessionRepository sessionRepository;
    private BookingRepository bookingRepository;
    private MemberHealthProfileRepository profileRepository;
    private HealthDataCodec healthDataCodec;
    private ConsentService consentService;
    private SafetyRuleRegistry ruleRegistry;
    private HealthAccessService healthAccessService;
    private TrainerPrepService trainerPrepService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Instant baseTime = Instant.parse("2026-10-28T10:00:00Z");

    @BeforeEach
    void setUp() {
        sessionRepository = mock(ClassSessionRepository.class);
        bookingRepository = mock(BookingRepository.class);
        profileRepository = mock(MemberHealthProfileRepository.class);
        consentService = mock(ConsentService.class);
        HealthAccessLogRepository accessLogRepo = mock(HealthAccessLogRepository.class);
        Clock clock = Clock.fixed(baseTime, ZoneOffset.UTC);
        healthAccessService = mock(HealthAccessService.class);

        EncryptionService encryptionService = mock(EncryptionService.class);
        when(encryptionService.encrypt(any())).thenAnswer(inv -> ((byte[]) inv.getArgument(0)).clone());
        when(encryptionService.decrypt(any())).thenAnswer(inv -> ((byte[]) inv.getArgument(0)).clone());
        healthDataCodec = new HealthDataCodec(encryptionService, objectMapper);

        ruleRegistry = new SafetyRuleRegistry();
        ruleRegistry.init();

        trainerPrepService = new TrainerPrepService(
                sessionRepository,
                bookingRepository,
                profileRepository,
                healthDataCodec,
                consentService,
                ruleRegistry,
                healthAccessService
        );
    }

    private MemberHealthProfile createMemberProfile(Long memberId, InjuryArea area, InjurySeverity severity) {
        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.INTERMEDIATE, null, List.of(Goal.GENERAL_FITNESS), 3,
                Collections.emptyList(),
                List.of(new InjuryDto(area, severity)),
                Collections.emptySet(), Collections.emptyMap(), false, new BigDecimal("175.0"), new BigDecimal("70.0"), Sex.MALE, null, 1
        );
        return MemberHealthProfile.builder().memberId(memberId).dataEncrypted(healthDataCodec.encode(profile)).build();
    }

    @Test
    @DisplayName("Fewer than 3 consenting attendees -> summaryAvailable=false, no counts, privacy note")
    void testFewerThan3ConsentingAttendees() {
        Long trainerId = 10L;
        Long sessionId = 100L;

        ClassSession session = ClassSession.builder().trainerId(trainerId).build();
        ReflectionTestUtils.setField(session, "id", sessionId);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        // 2 confirmed bookings with consent
        Booking b1 = Booking.builder().id(1L).sessionId(sessionId).memberId(101L).status(BookingStatus.CONFIRMED).build();
        Booking b2 = Booking.builder().id(2L).sessionId(sessionId).memberId(102L).status(BookingStatus.CONFIRMED).build();
        when(bookingRepository.findBySessionIdAndStatus(sessionId, BookingStatus.CONFIRMED)).thenReturn(List.of(b1, b2));

        when(consentService.hasConsent(101L, ConsentPurpose.SHARE_TRAINER_SUMMARY)).thenReturn(true);
        when(consentService.hasConsent(102L, ConsentPurpose.SHARE_TRAINER_SUMMARY)).thenReturn(true);

        MemberHealthProfile p1 = createMemberProfile(101L, InjuryArea.KNEE, InjurySeverity.MILD);
        MemberHealthProfile p2 = createMemberProfile(102L, InjuryArea.HIP, InjurySeverity.MODERATE);
        when(profileRepository.findByMemberId(101L)).thenReturn(Optional.of(p1));
        when(profileRepository.findByMemberId(102L)).thenReturn(Optional.of(p2));

        TrainerPrepResponse resp = trainerPrepService.getTrainerPrepSummary(trainerId, sessionId);

        assertEquals(2, resp.attendeeCount());
        assertFalse(resp.summaryAvailable(), "summaryAvailable must be false when < 3 consenting attendees");
        assertTrue(resp.restrictionCounts().isEmpty(), "Counts must be empty");
        assertTrue(resp.suggestedModifications().isEmpty(), "Modifications must be empty");
        assertEquals(TrainerPrepService.NOT_ENOUGH_DATA_NOTE, resp.note());

        // Zero access logs written
        verify(healthAccessService, never()).logAccess(any(), any(), any(), any());
    }

    @Test
    @DisplayName("3 or more consenting attendees -> summaryAvailable=true, counts only, modifications, access logs written")
    void testThreeOrMoreConsentingAttendees() throws Exception {
        Long trainerId = 10L;
        Long sessionId = 100L;

        ClassSession session = ClassSession.builder().trainerId(trainerId).build();
        ReflectionTestUtils.setField(session, "id", sessionId);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        // 4 confirmed bookings: 3 consenting, 1 non-consenting
        Booking b1 = Booking.builder().id(1L).sessionId(sessionId).memberId(101L).status(BookingStatus.CONFIRMED).build();
        Booking b2 = Booking.builder().id(2L).sessionId(sessionId).memberId(102L).status(BookingStatus.CONFIRMED).build();
        Booking b3 = Booking.builder().id(3L).sessionId(sessionId).memberId(103L).status(BookingStatus.CONFIRMED).build();
        Booking b4 = Booking.builder().id(4L).sessionId(sessionId).memberId(104L).status(BookingStatus.CONFIRMED).build();
        when(bookingRepository.findBySessionIdAndStatus(sessionId, BookingStatus.CONFIRMED)).thenReturn(List.of(b1, b2, b3, b4));

        when(consentService.hasConsent(101L, ConsentPurpose.SHARE_TRAINER_SUMMARY)).thenReturn(true);
        when(consentService.hasConsent(102L, ConsentPurpose.SHARE_TRAINER_SUMMARY)).thenReturn(true);
        when(consentService.hasConsent(103L, ConsentPurpose.SHARE_TRAINER_SUMMARY)).thenReturn(true);
        when(consentService.hasConsent(104L, ConsentPurpose.SHARE_TRAINER_SUMMARY)).thenReturn(false); // Non-consenting

        MemberHealthProfile p1 = createMemberProfile(101L, InjuryArea.KNEE, InjurySeverity.MODERATE);
        MemberHealthProfile p2 = createMemberProfile(102L, InjuryArea.KNEE, InjurySeverity.MILD);
        MemberHealthProfile p3 = createMemberProfile(103L, InjuryArea.LOWER_BACK, InjurySeverity.SEVERE);

        when(profileRepository.findByMemberId(101L)).thenReturn(Optional.of(p1));
        when(profileRepository.findByMemberId(102L)).thenReturn(Optional.of(p2));
        when(profileRepository.findByMemberId(103L)).thenReturn(Optional.of(p3));

        TrainerPrepResponse resp = trainerPrepService.getTrainerPrepSummary(trainerId, sessionId);

        assertEquals(4, resp.attendeeCount());
        assertTrue(resp.summaryAvailable());
        assertEquals(2, resp.restrictionCounts().get("KNEE"), "Knee count should be 2");
        assertEquals(1, resp.restrictionCounts().get("LOWER_BACK"), "Lower back count should be 1");
        assertNull(resp.note());

        // Verify modifications from modifications.yml
        assertTrue(resp.suggestedModifications().stream().anyMatch(m -> "LOW_IMPACT_ALTERNATIVE".equals(m.code())));
        assertTrue(resp.suggestedModifications().stream().anyMatch(m -> "AVOID_HEAVY_LOAD".equals(m.code())));

        // Verify JSON response has ZERO user ids or emails
        String json = objectMapper.writeValueAsString(resp);
        JsonNode root = objectMapper.readTree(json);
        assertFalse(root.has("userId"));
        assertFalse(root.has("memberId"));
        assertFalse(root.has("email"));
        assertFalse(root.has("name"));
        assertFalse(json.contains("101"));
        assertFalse(json.contains("102"));
        assertFalse(json.contains("103"));
        assertFalse(json.contains("104"));

        // Verify access log rows written for each of the 3 included members
        verify(healthAccessService, times(1)).logAccess(eq(101L), eq(trainerId), eq(HealthAccessAction.READ), eq("TRAINER_SUMMARY"));
        verify(healthAccessService, times(1)).logAccess(eq(102L), eq(trainerId), eq(HealthAccessAction.READ), eq("TRAINER_SUMMARY"));
        verify(healthAccessService, times(1)).logAccess(eq(103L), eq(trainerId), eq(HealthAccessAction.READ), eq("TRAINER_SUMMARY"));
        verify(healthAccessService, never()).logAccess(eq(104L), any(), any(), any());
    }

    @Test
    @DisplayName("Trainer attempting to prep another trainer's session returns 404 NOT_FOUND")
    void testAnotherTrainerSessionReturns404() {
        Long callerTrainerId = 10L;
        Long otherTrainerId = 99L;
        Long sessionId = 100L;

        ClassSession session = ClassSession.builder().trainerId(otherTrainerId).build();
        ReflectionTestUtils.setField(session, "id", sessionId);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        ApiException ex = assertThrows(ApiException.class,
                () -> trainerPrepService.getTrainerPrepSummary(callerTrainerId, sessionId));
        assertEquals(404, ex.getStatus().value(), "Must return 404 when querying another trainer's session");
    }
}
