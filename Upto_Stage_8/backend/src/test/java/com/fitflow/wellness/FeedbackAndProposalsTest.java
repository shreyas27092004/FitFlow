package com.fitflow.wellness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.booking.entity.AttendanceRecord;
import com.fitflow.booking.entity.Booking;
import com.fitflow.booking.model.AttendanceStatus;
import com.fitflow.booking.model.BookingStatus;
import com.fitflow.booking.repository.AttendanceRecordRepository;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.common.error.ApiException;
import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.model.ClassImpactLevel;
import com.fitflow.fitnessclass.model.ClassIntensity;
import com.fitflow.fitnessclass.repository.FitnessClassRepository;
import com.fitflow.notification.OutboxService;
import com.fitflow.security.EncryptionService;
import com.fitflow.session.entity.ClassSession;
import com.fitflow.session.repository.ClassSessionRepository;
import com.fitflow.wellness.dto.AcceptProposalRequest;
import com.fitflow.wellness.dto.ClassFeedbackRequest;
import com.fitflow.wellness.dto.FeedbackResponse;
import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.InjuryDto;
import com.fitflow.wellness.dto.RestrictionProposalDto;
import com.fitflow.wellness.entity.ClassFeedback;
import com.fitflow.wellness.entity.MemberHealthProfile;
import com.fitflow.wellness.entity.RestrictionProposal;
import com.fitflow.wellness.model.ExperienceLevel;
import com.fitflow.wellness.model.Goal;
import com.fitflow.wellness.model.InjuryArea;
import com.fitflow.wellness.model.InjurySeverity;
import com.fitflow.wellness.model.Sex;
import com.fitflow.wellness.repository.ClassFeedbackRepository;
import com.fitflow.wellness.repository.MemberHealthProfileRepository;
import com.fitflow.wellness.repository.RestrictionProposalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackAndProposalsTest {

    private BookingRepository bookingRepository;
    private AttendanceRecordRepository attendanceRecordRepository;
    private ClassSessionRepository sessionRepository;
    private FitnessClassRepository fitnessClassRepository;
    private ClassFeedbackRepository feedbackRepository;
    private RestrictionProposalRepository proposalRepository;
    private MemberHealthProfileRepository profileRepository;
    private HealthDataCodec healthDataCodec;
    private EncryptionService encryptionService;
    private OutboxService outboxService;
    private WellnessFeedbackService feedbackService;

    private final Instant baseTime = Instant.parse("2026-10-28T10:00:00Z");

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        attendanceRecordRepository = mock(AttendanceRecordRepository.class);
        sessionRepository = mock(ClassSessionRepository.class);
        fitnessClassRepository = mock(FitnessClassRepository.class);
        feedbackRepository = mock(ClassFeedbackRepository.class);
        proposalRepository = mock(RestrictionProposalRepository.class);
        profileRepository = mock(MemberHealthProfileRepository.class);
        encryptionService = mock(EncryptionService.class);
        outboxService = mock(OutboxService.class);
        Clock clock = Clock.fixed(baseTime, ZoneOffset.UTC);

        when(encryptionService.encrypt(any())).thenAnswer(inv -> ((byte[]) inv.getArgument(0)).clone());
        when(encryptionService.decrypt(any())).thenAnswer(inv -> ((byte[]) inv.getArgument(0)).clone());
        healthDataCodec = new HealthDataCodec(encryptionService, new ObjectMapper());

        feedbackService = new WellnessFeedbackService(
                bookingRepository,
                attendanceRecordRepository,
                sessionRepository,
                fitnessClassRepository,
                feedbackRepository,
                proposalRepository,
                profileRepository,
                healthDataCodec,
                mock(ConsentService.class),
                new CalorieService(null, null, null, null, null, null, null, null),
                encryptionService,
                outboxService,
                clock
        );
    }

    @Test
    @DisplayName("Feedback allowed only after booking is marked ATTENDED")
    void testFeedbackOnlyAfterAttended() {
        Long memberId = 201L;
        Long bookingId = 501L;

        Booking booking = Booking.builder().id(bookingId).memberId(memberId).sessionId(10L).status(BookingStatus.CONFIRMED).build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        // Case 1: No attendance record
        when(attendanceRecordRepository.findByBookingId(bookingId)).thenReturn(Optional.empty());
        ClassFeedbackRequest req = new ClassFeedbackRequest(5, false, null, "Great class");
        assertThrows(ApiException.class, () -> feedbackService.submitFeedback(memberId, bookingId, req));

        // Case 2: Attendance record is NO_SHOW
        AttendanceRecord noShow = AttendanceRecord.builder().bookingId(bookingId).status(AttendanceStatus.NO_SHOW).build();
        when(attendanceRecordRepository.findByBookingId(bookingId)).thenReturn(Optional.of(noShow));
        assertThrows(ApiException.class, () -> feedbackService.submitFeedback(memberId, bookingId, req));
    }

    @Test
    @DisplayName("Feedback rejected if already submitted for booking (once per booking)")
    void testOncePerBooking() {
        Long memberId = 202L;
        Long bookingId = 502L;

        Booking booking = Booking.builder().id(bookingId).memberId(memberId).sessionId(10L).build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        AttendanceRecord att = AttendanceRecord.builder().bookingId(bookingId).status(AttendanceStatus.ATTENDED).build();
        when(attendanceRecordRepository.findByBookingId(bookingId)).thenReturn(Optional.of(att));

        // Already exists
        when(feedbackRepository.findByBookingId(bookingId)).thenReturn(Optional.of(ClassFeedback.builder().id(1L).build()));

        ClassFeedbackRequest req = new ClassFeedbackRequest(4, false, null, "Good");
        ApiException ex = assertThrows(ApiException.class, () -> feedbackService.submitFeedback(memberId, bookingId, req));
        assertTrue(ex.getMessage().contains("Feedback has already been submitted"));
    }

    @Test
    @DisplayName("Pain feedback creates a restriction proposal and an event WITHOUT member id or name")
    void testPainFeedbackCreatesProposalAndAnonymousEvent() {
        Long memberId = 203L;
        Long bookingId = 503L;
        Long sessionId = 15L;
        Long classId = 44L;

        Booking booking = Booking.builder().id(bookingId).memberId(memberId).sessionId(sessionId).build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        AttendanceRecord att = AttendanceRecord.builder().bookingId(bookingId).status(AttendanceStatus.ATTENDED).build();
        when(attendanceRecordRepository.findByBookingId(bookingId)).thenReturn(Optional.of(att));
        when(feedbackRepository.findByBookingId(bookingId)).thenReturn(Optional.empty());

        ClassSession session = ClassSession.builder().classId(classId).startsAt(Instant.now()).endsAt(Instant.now()).build();
        ReflectionTestUtils.setField(session, "id", sessionId);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        FitnessClass fc = FitnessClass.builder().name("Spin Express").intensity(ClassIntensity.HIGH).impactLevel(ClassImpactLevel.LOW).metadataReviewed(true).createdBy(1L).build();
        ReflectionTestUtils.setField(fc, "id", classId);
        when(fitnessClassRepository.findById(classId)).thenReturn(Optional.of(fc));

        RestrictionProposal savedProposal = RestrictionProposal.builder().id(777L).memberId(memberId).area("KNEE").suggestedSeverity("MILD").status("PENDING").build();
        when(proposalRepository.save(any())).thenReturn(savedProposal);

        ClassFeedbackRequest req = new ClassFeedbackRequest(3, true, InjuryArea.KNEE, "Felt knee twinge during sprints");
        FeedbackResponse resp = feedbackService.submitFeedback(memberId, bookingId, req);

        assertEquals(777L, resp.proposalId());
        assertEquals("Would you like to add this to your profile?", resp.message());

        // Verify outbox event feedback.pain_reported
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outboxService).append(eq("SESSION"), eq(sessionId), eq("feedback.pain_reported"), payloadCaptor.capture());

        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals(sessionId, payload.get("sessionId"));
        assertEquals(classId, payload.get("classId"));
        assertEquals("KNEE", payload.get("areaCode"));

        // Critical privacy assertions: NO member id and NO name in payload!
        assertFalse(payload.containsKey("memberId"), "Payload must NEVER contain memberId");
        assertFalse(payload.containsKey("userId"), "Payload must NEVER contain userId");
        assertFalse(payload.containsKey("name"), "Payload must NEVER contain name");
        assertFalse(payload.containsKey("email"), "Payload must NEVER contain email");
    }

    @Test
    @DisplayName("Accepting proposal adds or raises injury severity; never lowers existing severity")
    void testAcceptProposalNeverLowersSeverity() {
        Long memberId = 204L;
        Long proposalId = 888L;

        RestrictionProposal proposal = RestrictionProposal.builder()
                .id(proposalId).memberId(memberId).area("KNEE").suggestedSeverity("MILD").status("PENDING").build();
        when(proposalRepository.findByIdAndMemberId(proposalId, memberId)).thenReturn(Optional.of(proposal));
        when(proposalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Member already has KNEE with MODERATE severity
        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.INTERMEDIATE, null, List.of(Goal.GENERAL_FITNESS), 3,
                Collections.emptyList(),
                List.of(new InjuryDto(InjuryArea.KNEE, InjurySeverity.MODERATE)),
                Collections.emptySet(), Collections.emptyMap(), false, new BigDecimal("170.0"), new BigDecimal("65.0"), Sex.MALE, null, 1
        );
        MemberHealthProfile entity = MemberHealthProfile.builder().memberId(memberId).dataEncrypted(healthDataCodec.encode(profile)).build();
        when(profileRepository.findByMemberId(memberId)).thenReturn(Optional.of(entity));

        // Member attempts to accept proposal with MILD (which is lower than current MODERATE)
        AcceptProposalRequest acceptMild = new AcceptProposalRequest(InjurySeverity.MILD);
        feedbackService.acceptProposal(memberId, proposalId, acceptMild);

        // Verify that profile retains MODERATE (never lowered!)
        FitnessProfile updatedProfile = healthDataCodec.decode(entity.getDataEncrypted());
        InjuryDto kneeInjury = updatedProfile.injuries().stream().filter(i -> i.area() == InjuryArea.KNEE).findFirst().orElseThrow();
        assertEquals(InjurySeverity.MODERATE, kneeInjury.severity(), "Accepting MILD must not lower existing MODERATE severity");

        // Member accepts with SEVERE (higher than MODERATE) -> should be raised
        proposal.setStatus("PENDING");
        AcceptProposalRequest acceptSevere = new AcceptProposalRequest(InjurySeverity.SEVERE);
        feedbackService.acceptProposal(memberId, proposalId, acceptSevere);

        FitnessProfile raisedProfile = healthDataCodec.decode(entity.getDataEncrypted());
        InjuryDto raisedKnee = raisedProfile.injuries().stream().filter(i -> i.area() == InjuryArea.KNEE).findFirst().orElseThrow();
        assertEquals(InjurySeverity.SEVERE, raisedKnee.severity(), "Accepting SEVERE must raise severity to SEVERE");
    }

    @Test
    @DisplayName("Dismissing proposal sets status to DISMISSED without altering profile")
    void testDismissProposal() {
        Long memberId = 205L;
        Long proposalId = 999L;

        RestrictionProposal proposal = RestrictionProposal.builder()
                .id(proposalId).memberId(memberId).area("ANKLE").suggestedSeverity("MILD").status("PENDING").build();
        when(proposalRepository.findByIdAndMemberId(proposalId, memberId)).thenReturn(Optional.of(proposal));
        when(proposalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RestrictionProposalDto result = feedbackService.dismissProposal(memberId, proposalId);
        assertEquals("DISMISSED", result.status());

        // Profile is completely untouched
        verify(profileRepository, never()).save(any());
    }
}
