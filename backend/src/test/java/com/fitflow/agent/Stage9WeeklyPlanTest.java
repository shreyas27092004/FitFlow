package com.fitflow.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fitflow.agent.entity.WeeklyPlan;
import com.fitflow.agent.repository.PendingActionRepository;
import com.fitflow.agent.repository.WeeklyPlanRepository;
import com.fitflow.agent.service.WeeklyPlanService;
import com.fitflow.agent.service.WeeklyPlanService.CandidatePlanResult;
import com.fitflow.booking.BookingService;
import com.fitflow.booking.dto.CreateBookingRequest;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.common.error.ApiException;
import com.fitflow.membership.MembershipService;
import com.fitflow.membership.dto.MemberMembershipSummaryResponse;
import com.fitflow.session.dto.SessionDetailResponse;
import com.fitflow.wellness.WellnessProfileService;
import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.ProfileResponse;
import com.fitflow.wellness.dto.SafeClassesResponse;
import com.fitflow.wellness.dto.SafeSessionItem;
import com.fitflow.wellness.model.ExperienceLevel;
import com.fitflow.wellness.model.Goal;
import com.fitflow.wellness.model.SafetyVerdictType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

class Stage9WeeklyPlanTest {

    private MembershipService membershipService;
    private WellnessProfileService wellnessProfileService;
    private BookingService bookingService;
    private BookingRepository bookingRepository;
    private WeeklyPlanRepository weeklyPlanRepository;
    private PendingActionRepository pendingActionRepository;
    private ObjectMapper objectMapper;
    private Clock fixedClock;

    private WeeklyPlanService service;
    private final LocalDate weekStart = LocalDate.of(2026, 11, 2); // Monday

    @BeforeEach
    void setUp() {
        membershipService = Mockito.mock(MembershipService.class);
        wellnessProfileService = Mockito.mock(WellnessProfileService.class);
        bookingService = Mockito.mock(BookingService.class);
        bookingRepository = Mockito.mock(BookingRepository.class);
        weeklyPlanRepository = Mockito.mock(WeeklyPlanRepository.class);
        pendingActionRepository = Mockito.mock(PendingActionRepository.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        fixedClock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC);

        service = new WeeklyPlanService(
                membershipService,
                wellnessProfileService,
                bookingService,
                bookingRepository,
                weeklyPlanRepository,
                pendingActionRepository,
                objectMapper,
                fixedClock
        );
        service.initGoalCategories();
        WeeklyPlanService.sanityBreakSkipCandidateSubsetCheck = false;
    }

    @AfterEach
    void tearDown() {
        WeeklyPlanService.sanityBreakSkipCandidateSubsetCheck = false;
    }

    private SessionDetailResponse createSession(Long id, String name, String category, String intensity, Instant startsAt) {
        return new SessionDetailResponse(
                id,
                100L,
                name,
                category,
                45,
                intensity,
                "LOW",
                1L,
                "Trainer Test",
                startsAt,
                startsAt.plusSeconds(45 * 60),
                "Studio 1",
                20,
                5,
                15,
                com.fitflow.session.entity.ClassSession.Status.SCHEDULED,
                null,
                null,
                null,
                1,
                startsAt
        );
    }

    private FitnessProfile createTestProfile(ExperienceLevel level, Goal goal, int sessionsPlanned) {
        return new FitnessProfile(
                level,
                null,
                List.of(goal),
                sessionsPlanned,
                List.of(),
                List.of(),
                Set.of(),
                Map.of(),
                true,
                new BigDecimal("175.0"),
                new BigDecimal("75.0"),
                com.fitflow.wellness.model.Sex.MALE,
                com.fitflow.wellness.model.DietPreference.VEG,
                1
        );
    }

    @Test
    @DisplayName("Plan Candidate: Bound by entitlement remaining")
    void candidatesBoundByEntitlement() {
        MemberMembershipSummaryResponse summary = new MemberMembershipSummaryResponse(2, null);
        Mockito.when(membershipService.getMemberMembership(1L)).thenReturn(summary);

        FitnessProfile profile = createTestProfile(ExperienceLevel.INTERMEDIATE, Goal.BUILD_STRENGTH, 5);
        Mockito.when(wellnessProfileService.getProfile(1L)).thenReturn(new ProfileResponse(profile, null));

        SessionDetailResponse s1 = createSession(1L, "Strength 1", "STRENGTH", "MEDIUM", Instant.parse("2026-11-02T10:00:00Z"));
        SessionDetailResponse s2 = createSession(2L, "Strength 2", "STRENGTH", "MEDIUM", Instant.parse("2026-11-04T10:00:00Z"));
        SessionDetailResponse s3 = createSession(3L, "Strength 3", "STRENGTH", "MEDIUM", Instant.parse("2026-11-06T10:00:00Z"));

        SafeClassesResponse safeResp = new SafeClassesResponse(
                false, "Disclaimer", "Note",
                List.of(new SafeSessionItem(s1, SafetyVerdictType.ALLOWED, List.of(), List.of()),
                        new SafeSessionItem(s2, SafetyVerdictType.ALLOWED, List.of(), List.of()),
                        new SafeSessionItem(s3, SafetyVerdictType.ALLOWED, List.of(), List.of())),
                List.of()
        );
        Mockito.when(wellnessProfileService.getSafeClasses(eq(1L), any(), any(), anyInt(), anyInt())).thenReturn(safeResp);
        Mockito.when(bookingRepository.findOverlappingConfirmedBookings(eq(1L), any(), any())).thenReturn(List.of());

        CandidatePlanResult res = service.buildWeeklyPlanCandidates(1L, weekStart);
        assertEquals(2, res.candidates().size());
        assertEquals(2, res.targetCount());
    }

    @Test
    @DisplayName("Recovery Rule: High intensity sessions never scheduled on > 2 consecutive days")
    void recoveryRuleNoThreeConsecutiveHighDays() {
        MemberMembershipSummaryResponse summary = new MemberMembershipSummaryResponse(10, null);
        Mockito.when(membershipService.getMemberMembership(1L)).thenReturn(summary);

        FitnessProfile profile = createTestProfile(ExperienceLevel.ADVANCED, Goal.LOSE_WEIGHT, 5);
        Mockito.when(wellnessProfileService.getProfile(1L)).thenReturn(new ProfileResponse(profile, null));

        // Mon, Tue, Wed all HIGH
        SessionDetailResponse sMon = createSession(1L, "HIIT Mon", "HIIT", "HIGH", Instant.parse("2026-11-02T10:00:00Z"));
        SessionDetailResponse sTue = createSession(2L, "HIIT Tue", "HIIT", "HIGH", Instant.parse("2026-11-03T10:00:00Z"));
        SessionDetailResponse sWed = createSession(3L, "HIIT Wed", "HIIT", "HIGH", Instant.parse("2026-11-04T10:00:00Z"));

        SafeClassesResponse safeResp = new SafeClassesResponse(
                false, "Disclaimer", "Note",
                List.of(new SafeSessionItem(sMon, SafetyVerdictType.ALLOWED, List.of(), List.of()),
                        new SafeSessionItem(sTue, SafetyVerdictType.ALLOWED, List.of(), List.of()),
                        new SafeSessionItem(sWed, SafetyVerdictType.ALLOWED, List.of(), List.of())),
                List.of()
        );
        Mockito.when(wellnessProfileService.getSafeClasses(eq(1L), any(), any(), anyInt(), anyInt())).thenReturn(safeResp);
        Mockito.when(bookingRepository.findOverlappingConfirmedBookings(eq(1L), any(), any())).thenReturn(List.of());

        CandidatePlanResult res = service.buildWeeklyPlanCandidates(1L, weekStart);
        assertEquals(2, res.candidates().size());
        assertEquals(1L, res.candidates().get(0).id());
        assertEquals(2L, res.candidates().get(1).id());
    }

    @Test
    @DisplayName("Recovery Rule: Beginner capped at at most 2 HIGH intensity sessions per week")
    void recoveryRuleBeginnerCappedAt2High() {
        MemberMembershipSummaryResponse summary = new MemberMembershipSummaryResponse(10, null);
        Mockito.when(membershipService.getMemberMembership(1L)).thenReturn(summary);

        FitnessProfile profile = createTestProfile(ExperienceLevel.BEGINNER, Goal.LOSE_WEIGHT, 5);
        Mockito.when(wellnessProfileService.getProfile(1L)).thenReturn(new ProfileResponse(profile, null));

        SessionDetailResponse sMon = createSession(1L, "HIIT Mon", "HIIT", "HIGH", Instant.parse("2026-11-02T10:00:00Z"));
        SessionDetailResponse sWed = createSession(2L, "HIIT Wed", "HIIT", "HIGH", Instant.parse("2026-11-04T10:00:00Z"));
        SessionDetailResponse sFri = createSession(3L, "HIIT Fri", "HIIT", "HIGH", Instant.parse("2026-11-06T10:00:00Z"));

        SafeClassesResponse safeResp = new SafeClassesResponse(
                false, "Disclaimer", "Note",
                List.of(new SafeSessionItem(sMon, SafetyVerdictType.ALLOWED, List.of(), List.of()),
                        new SafeSessionItem(sWed, SafetyVerdictType.ALLOWED, List.of(), List.of()),
                        new SafeSessionItem(sFri, SafetyVerdictType.ALLOWED, List.of(), List.of())),
                List.of()
        );
        Mockito.when(wellnessProfileService.getSafeClasses(eq(1L), any(), any(), anyInt(), anyInt())).thenReturn(safeResp);
        Mockito.when(bookingRepository.findOverlappingConfirmedBookings(eq(1L), any(), any())).thenReturn(List.of());

        CandidatePlanResult res = service.buildWeeklyPlanCandidates(1L, weekStart);
        assertEquals(2, res.candidates().size());
    }

    @Test
    @DisplayName("Variety Rule: At most 2 sessions of the same category")
    void varietyRuleMax2PerCategory() {
        MemberMembershipSummaryResponse summary = new MemberMembershipSummaryResponse(10, null);
        Mockito.when(membershipService.getMemberMembership(1L)).thenReturn(summary);

        FitnessProfile profile = createTestProfile(ExperienceLevel.ADVANCED, Goal.IMPROVE_FLEXIBILITY, 5);
        Mockito.when(wellnessProfileService.getProfile(1L)).thenReturn(new ProfileResponse(profile, null));

        SessionDetailResponse s1 = createSession(10L, "Yoga Mon", "YOGA", "LOW", Instant.parse("2026-11-02T08:00:00Z"));
        SessionDetailResponse s2 = createSession(11L, "Yoga Wed", "YOGA", "LOW", Instant.parse("2026-11-04T08:00:00Z"));
        SessionDetailResponse s3 = createSession(12L, "Yoga Fri", "YOGA", "LOW", Instant.parse("2026-11-06T08:00:00Z"));

        SafeClassesResponse safeResp = new SafeClassesResponse(
                false, "Disclaimer", "Note",
                List.of(new SafeSessionItem(s1, SafetyVerdictType.ALLOWED, List.of(), List.of()),
                        new SafeSessionItem(s2, SafetyVerdictType.ALLOWED, List.of(), List.of()),
                        new SafeSessionItem(s3, SafetyVerdictType.ALLOWED, List.of(), List.of())),
                List.of()
        );
        Mockito.when(wellnessProfileService.getSafeClasses(eq(1L), any(), any(), anyInt(), anyInt())).thenReturn(safeResp);
        Mockito.when(bookingRepository.findOverlappingConfirmedBookings(eq(1L), any(), any())).thenReturn(List.of());

        CandidatePlanResult res = service.buildWeeklyPlanCandidates(1L, weekStart);
        assertEquals(2, res.candidates().size());
    }

    @Test
    @DisplayName("Proposal Subset Check: Throws 400 when sessionIds is not a subset of candidates")
    void proposePlanRejectsNonSubset() {
        MemberMembershipSummaryResponse summary = new MemberMembershipSummaryResponse(5, null);
        Mockito.when(membershipService.getMemberMembership(1L)).thenReturn(summary);

        SessionDetailResponse s1 = createSession(101L, "Hatha Yoga", "YOGA", "LOW", Instant.parse("2026-11-02T09:00:00Z"));
        SafeClassesResponse safeResp = new SafeClassesResponse(
                false, "Disclaimer", "Note",
                List.of(new SafeSessionItem(s1, SafetyVerdictType.ALLOWED, List.of(), List.of())), List.of()
        );
        Mockito.when(wellnessProfileService.getSafeClasses(eq(1L), any(), any(), anyInt(), anyInt())).thenReturn(safeResp);
        Mockito.when(bookingRepository.findOverlappingConfirmedBookings(eq(1L), any(), any())).thenReturn(List.of());

        assertThrows(ApiException.class, () ->
                service.proposeWeeklyPlan(1L, weekStart, List.of(999L), 10L)
        );
    }

    @Test
    @DisplayName("Sanity Break 1: Skipping candidate subset check allows arbitrary sessions")
    void sanityBreak1AllowsArbitrarySessions() {
        WeeklyPlanService.sanityBreakSkipCandidateSubsetCheck = true;

        MemberMembershipSummaryResponse summary = new MemberMembershipSummaryResponse(5, null);
        Mockito.when(membershipService.getMemberMembership(1L)).thenReturn(summary);
        Mockito.when(wellnessProfileService.getSafeClasses(eq(1L), any(), any(), anyInt(), anyInt()))
                .thenReturn(new SafeClassesResponse(false, "d", "n", List.of(), List.of()));

        WeeklyPlan plan = WeeklyPlan.builder()
                .id(77L)
                .memberId(1L)
                .status("PROPOSED")
                .build();
        Mockito.when(weeklyPlanRepository.save(any(WeeklyPlan.class))).thenReturn(plan);

        Long planId = service.proposeWeeklyPlan(1L, weekStart, List.of(999L), 10L);
        assertNotNull(planId);
        assertEquals(77L, planId);
    }

    @Test
    @DisplayName("Plan Execution: Marks BOOKED, PARTIAL, or FAILED with idempotency keys")
    void executePlanSetsAppropriateStatus() {
        String planJson = "{\"weekStart\":\"2026-11-02\",\"sessionIds\":[201, 202]}";
        WeeklyPlan plan = WeeklyPlan.builder()
                .id(88L)
                .memberId(1L)
                .plan(planJson)
                .status("PROPOSED")
                .build();
        Mockito.when(weeklyPlanRepository.findById(88L)).thenReturn(Optional.of(plan));

        // 201 succeeds, 202 fails
        Mockito.when(bookingService.createBooking(eq(new CreateBookingRequest(201L)), eq("agent-55-201"), eq(1L)))
                .thenReturn(new BookingService.BookingResult(200, Map.of("bookingId", 201L), false));
        Mockito.doThrow(new RuntimeException("Class is full"))
                .when(bookingService).createBooking(eq(new CreateBookingRequest(202L)), eq("agent-55-202"), eq(1L));

        Map<String, Object> result = service.executeWeeklyPlanConfirmation(88L, 1L, 55L);
        assertNotNull(result);
        assertEquals("PARTIAL", result.get("status"));
        assertEquals(1, result.get("confirmedCount"));
        assertEquals(1, result.get("failedCount"));
    }
}
