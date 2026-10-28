package com.fitflow.wellness;

import com.fitflow.booking.entity.AttendanceRecord;
import com.fitflow.booking.entity.Booking;
import com.fitflow.booking.model.AttendanceStatus;
import com.fitflow.booking.model.BookingStatus;
import com.fitflow.booking.repository.AttendanceRecordRepository;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.model.ClassImpactLevel;
import com.fitflow.fitnessclass.model.ClassIntensity;
import com.fitflow.fitnessclass.repository.FitnessClassRepository;
import com.fitflow.notification.OutboxService;
import com.fitflow.security.EncryptionService;
import com.fitflow.session.entity.ClassSession;
import com.fitflow.session.repository.ClassSessionRepository;
import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.WeeklyProgressResponse;
import com.fitflow.wellness.entity.MemberHealthProfile;
import com.fitflow.wellness.model.ConsentPurpose;
import com.fitflow.wellness.model.ExperienceLevel;
import com.fitflow.wellness.model.Goal;
import com.fitflow.wellness.model.Sex;
import com.fitflow.wellness.repository.ClassFeedbackRepository;
import com.fitflow.wellness.repository.MemberHealthProfileRepository;
import com.fitflow.wellness.repository.RestrictionProposalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeeklyProgressTest {

    private BookingRepository bookingRepository;
    private AttendanceRecordRepository attendanceRecordRepository;
    private ClassSessionRepository sessionRepository;
    private FitnessClassRepository fitnessClassRepository;
    private MemberHealthProfileRepository profileRepository;
    private HealthDataCodec healthDataCodec;
    private ConsentService consentService;
    private CalorieService calorieService;
    private WellnessFeedbackService feedbackService;

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private final Instant baseTime = Instant.parse("2026-10-28T12:00:00Z");

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        attendanceRecordRepository = mock(AttendanceRecordRepository.class);
        sessionRepository = mock(ClassSessionRepository.class);
        fitnessClassRepository = mock(FitnessClassRepository.class);
        profileRepository = mock(MemberHealthProfileRepository.class);
        healthDataCodec = mock(HealthDataCodec.class);
        consentService = mock(ConsentService.class);
        calorieService = new CalorieService(null, null, null, null, null, null, null, null);
        ClassFeedbackRepository feedbackRepository = mock(ClassFeedbackRepository.class);
        RestrictionProposalRepository proposalRepository = mock(RestrictionProposalRepository.class);
        EncryptionService encryptionService = mock(EncryptionService.class);
        OutboxService outboxService = mock(OutboxService.class);
        Clock clock = Clock.fixed(baseTime, ZoneOffset.UTC);

        feedbackService = new WellnessFeedbackService(
                bookingRepository,
                attendanceRecordRepository,
                sessionRepository,
                fitnessClassRepository,
                feedbackRepository,
                proposalRepository,
                profileRepository,
                healthDataCodec,
                consentService,
                calorieService,
                encryptionService,
                outboxService,
                clock
        );
    }

    @Test
    @DisplayName("Week boundary check: Sunday night vs Monday morning in Asia/Kolkata timezone")
    void testWeekBoundaryInAsiaKolkata() {
        Long memberId = 101L;
        LocalDate mondayWeekStart = LocalDate.of(2026, 10, 26); // Monday

        // Monday 2026-10-26 00:00:00 Asia/Kolkata is 2026-10-25 18:30:00 UTC
        // Session 1: Sunday 2026-10-25 18:00:00 UTC -> 23:30:00 IST (PREVIOUS WEEK)
        Instant prevWeekInstant = Instant.parse("2026-10-25T18:00:00Z");
        ClassSession prevSession = ClassSession.builder()
                .classId(10L).startsAt(prevWeekInstant).endsAt(prevWeekInstant.plusSeconds(3600))
                .status(ClassSession.Status.COMPLETED).build();
        ReflectionTestUtils.setField(prevSession, "id", 1L);

        // Session 2: Sunday 2026-10-25 19:00:00 UTC -> Monday 00:30:00 IST (CURRENT WEEK!)
        Instant currWeekInstant = Instant.parse("2026-10-25T19:00:00Z");
        ClassSession currSession = ClassSession.builder()
                .classId(10L).startsAt(currWeekInstant).endsAt(currWeekInstant.plusSeconds(3600))
                .status(ClassSession.Status.COMPLETED).build();
        ReflectionTestUtils.setField(currSession, "id", 2L);

        Booking booking1 = Booking.builder().id(11L).memberId(memberId).sessionId(1L).status(BookingStatus.CONFIRMED).build();
        Booking booking2 = Booking.builder().id(22L).memberId(memberId).sessionId(2L).status(BookingStatus.CONFIRMED).build();

        when(bookingRepository.findAllByMemberId(memberId)).thenReturn(List.of(booking1, booking2));
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(prevSession));
        when(sessionRepository.findById(2L)).thenReturn(Optional.of(currSession));

        AttendanceRecord att2 = AttendanceRecord.builder().id(202L).bookingId(22L).markedBy(1L).markedAt(Instant.now()).status(AttendanceStatus.ATTENDED).createdAt(Instant.now()).build();
        when(attendanceRecordRepository.findByBookingId(22L)).thenReturn(Optional.of(att2));

        WeeklyProgressResponse resp = feedbackService.getWeeklyProgress(memberId, mondayWeekStart);

        // Only booking2 should be in current week
        assertEquals(1, resp.bookedCount(), "Only booking starting in Monday IST boundary should be counted in current week");
        assertEquals(1, resp.attendedCount());
        assertEquals(0, resp.noShowCount());
    }

    @Test
    @DisplayName("Streak across a gap: streak resets when a previous week has 0 attended sessions")
    void testStreakAcrossGap() {
        Long memberId = 102L;
        LocalDate week3Monday = LocalDate.of(2026, 10, 26);
        LocalDate week2Monday = week3Monday.minusWeeks(1); // 2026-10-19 (GAP WEEK: 0 attended)
        LocalDate week1Monday = week3Monday.minusWeeks(2); // 2026-10-12 (Attended)

        // Week 3 session (Attended)
        Instant w3Time = week3Monday.atStartOfDay(KOLKATA).plusHours(10).toInstant();
        ClassSession s3 = ClassSession.builder().classId(10L).startsAt(w3Time).endsAt(w3Time.plusSeconds(3600)).status(ClassSession.Status.COMPLETED).build();
        ReflectionTestUtils.setField(s3, "id", 3L);
        Booking b3 = Booking.builder().id(30L).memberId(memberId).sessionId(3L).status(BookingStatus.CONFIRMED).build();
        AttendanceRecord att3 = AttendanceRecord.builder().bookingId(30L).markedBy(1L).markedAt(Instant.now()).status(AttendanceStatus.ATTENDED).createdAt(Instant.now()).build();

        // Week 1 session (Attended)
        Instant w1Time = week1Monday.atStartOfDay(KOLKATA).plusHours(10).toInstant();
        ClassSession s1 = ClassSession.builder().classId(10L).startsAt(w1Time).endsAt(w1Time.plusSeconds(3600)).status(ClassSession.Status.COMPLETED).build();
        ReflectionTestUtils.setField(s1, "id", 1L);
        Booking b1 = Booking.builder().id(10L).memberId(memberId).sessionId(1L).status(BookingStatus.CONFIRMED).build();
        AttendanceRecord att1 = AttendanceRecord.builder().bookingId(10L).markedBy(1L).markedAt(Instant.now()).status(AttendanceStatus.ATTENDED).createdAt(Instant.now()).build();

        when(bookingRepository.findAllByMemberId(memberId)).thenReturn(List.of(b1, b3));
        when(sessionRepository.findById(3L)).thenReturn(Optional.of(s3));
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(s1));
        when(attendanceRecordRepository.findByBookingId(30L)).thenReturn(Optional.of(att3));
        when(attendanceRecordRepository.findByBookingId(10L)).thenReturn(Optional.of(att1));

        WeeklyProgressResponse resp = feedbackService.getWeeklyProgress(memberId, week3Monday);

        // Week 2 was empty, so streak should be 1 (broken by gap)
        assertEquals(1, resp.streakWeeks(), "Streak must be 1 because week 2 had 0 attended sessions");
    }

    @Test
    @DisplayName("Kcal calculation: returns null without CALORIE_ESTIMATES consent, returns kcal with consent")
    void testKcalOnlyWithConsent() {
        Long memberId = 103L;
        LocalDate weekStart = LocalDate.of(2026, 10, 26);
        Instant sessionTime = weekStart.atStartOfDay(KOLKATA).plusHours(8).toInstant();

        FitnessClass fc = FitnessClass.builder()
                .name("HIIT Burn").durationMinutes(60).metValue(new BigDecimal("8.0"))
                .intensity(ClassIntensity.HIGH).impactLevel(ClassImpactLevel.HIGH)
                .metadataReviewed(true).createdBy(1L).build();
        ReflectionTestUtils.setField(fc, "id", 99L);

        ClassSession session = ClassSession.builder()
                .classId(99L).startsAt(sessionTime).endsAt(sessionTime.plusSeconds(3600))
                .status(ClassSession.Status.COMPLETED).build();
        ReflectionTestUtils.setField(session, "id", 50L);

        Booking booking = Booking.builder().id(500L).memberId(memberId).sessionId(50L).status(BookingStatus.CONFIRMED).build();
        AttendanceRecord att = AttendanceRecord.builder().bookingId(500L).markedBy(1L).markedAt(Instant.now()).status(AttendanceStatus.ATTENDED).createdAt(Instant.now()).build();

        when(bookingRepository.findAllByMemberId(memberId)).thenReturn(List.of(booking));
        when(sessionRepository.findById(50L)).thenReturn(Optional.of(session));
        when(fitnessClassRepository.findById(99L)).thenReturn(Optional.of(fc));
        when(attendanceRecordRepository.findByBookingId(500L)).thenReturn(Optional.of(att));

        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.INTERMEDIATE, null, List.of(Goal.LOSE_WEIGHT), 3,
                Collections.emptyList(), Collections.emptyList(), Collections.emptySet(), Collections.emptyMap(),
                false, new BigDecimal("175.0"), new BigDecimal("70.0"), Sex.MALE, null, 1
        );
        MemberHealthProfile profEntity = MemberHealthProfile.builder().memberId(memberId).dataEncrypted(new byte[]{1}).build();
        when(profileRepository.findByMemberId(memberId)).thenReturn(Optional.of(profEntity));
        when(healthDataCodec.decode(any())).thenReturn(profile);

        // 1. Without CALORIE_ESTIMATES consent -> null
        when(consentService.hasConsent(memberId, ConsentPurpose.CALORIE_ESTIMATES)).thenReturn(false);
        WeeklyProgressResponse respWithoutConsent = feedbackService.getWeeklyProgress(memberId, weekStart);
        assertNull(respWithoutConsent.estimatedKcalBurned(), "estimatedKcalBurned must be null without CALORIE_ESTIMATES consent");

        // 2. With CALORIE_ESTIMATES consent -> 8.0 * 70kg * 1 hr = 560 kcal
        when(consentService.hasConsent(memberId, ConsentPurpose.CALORIE_ESTIMATES)).thenReturn(true);
        WeeklyProgressResponse respWithConsent = feedbackService.getWeeklyProgress(memberId, weekStart);
        assertNotNull(respWithConsent.estimatedKcalBurned());
        assertEquals(560, respWithConsent.estimatedKcalBurned(), "8.0 MET * 70kg * 60m should equal 560 kcal");
    }
}
