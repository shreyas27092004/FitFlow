package com.fitflow.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fitflow.agent.dto.PendingActionProposal;
import com.fitflow.agent.service.PendingActionService;
import com.fitflow.agent.tools.GetClassDetailsTool;
import com.fitflow.agent.tools.GetLlmSafeProfileTool;
import com.fitflow.agent.tools.GetMembershipStatusTool;
import com.fitflow.agent.tools.GetMyBookingsTool;
import com.fitflow.agent.tools.GetSafeClassesTool;
import com.fitflow.agent.tools.ListPlansTool;
import com.fitflow.agent.tools.PreviewMembershipChangeTool;
import com.fitflow.agent.tools.ProposeBookingTool;
import com.fitflow.agent.tools.ProposeCancelBookingTool;
import com.fitflow.agent.tools.ProposeMembershipChangeTool;
import com.fitflow.agent.tools.ProposeProfileSaveTool;
import com.fitflow.agent.tools.SearchSessionsTool;
import com.fitflow.agent.tools.ToolContext;
import com.fitflow.agent.tools.ToolResult;
import com.fitflow.booking.BookingService;
import com.fitflow.booking.dto.MemberBookingResponse;
import com.fitflow.common.error.ApiException;
import com.fitflow.common.pagination.PageResponse;
import com.fitflow.fitnessclass.FitnessClassService;
import com.fitflow.fitnessclass.dto.ClassDetailResponse;
import com.fitflow.fitnessclass.model.ClassCategory;
import com.fitflow.fitnessclass.model.ClassImpactLevel;
import com.fitflow.fitnessclass.model.ClassIntensity;
import com.fitflow.membership.MembershipService;
import com.fitflow.membership.dto.ChangePreviewResponse;
import com.fitflow.membership.dto.MemberMembershipSummaryResponse;
import com.fitflow.membership.dto.PlanDto;
import com.fitflow.session.ClassSessionService;
import com.fitflow.session.dto.SessionDetailResponse;
import com.fitflow.session.entity.ClassSession;
import com.fitflow.user.AgeService;
import com.fitflow.user.UserService;
import com.fitflow.user.dto.UserProfileResponse;
import com.fitflow.wellness.WellnessProfileService;
import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.LlmSafeProfile;
import com.fitflow.wellness.dto.SafeClassesResponse;
import com.fitflow.wellness.dto.SafeSessionItem;
import com.fitflow.wellness.model.ExperienceLevel;
import com.fitflow.wellness.model.Goal;
import com.fitflow.wellness.model.SafetyVerdictType;
import com.fitflow.wellness.model.ScreeningStatus;
import com.fitflow.wellness.model.Sex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

class ToolExecutionTest {

    private ObjectMapper objectMapper;
    private Clock clock;
    private ToolContext context;

    private ClassSessionService classSessionService;
    private FitnessClassService fitnessClassService;
    private BookingService bookingService;
    private MembershipService membershipService;
    private WellnessProfileService wellnessProfileService;
    private PendingActionService pendingActionService;
    private UserService userService;
    private AgeService ageService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        clock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC);
        context = new ToolContext(42L, Set.of("MEMBER"), Set.of(), 10L, clock);

        classSessionService = Mockito.mock(ClassSessionService.class);
        fitnessClassService = Mockito.mock(FitnessClassService.class);
        bookingService = Mockito.mock(BookingService.class);
        membershipService = Mockito.mock(MembershipService.class);
        wellnessProfileService = Mockito.mock(WellnessProfileService.class);
        pendingActionService = Mockito.mock(PendingActionService.class);
        userService = Mockito.mock(UserService.class);
        ageService = Mockito.mock(AgeService.class);
    }

    @Test
    @DisplayName("Tool: search_sessions calls ClassSessionService with caller id from context")
    void testSearchSessionsTool() {
        SearchSessionsTool tool = new SearchSessionsTool(classSessionService, objectMapper);

        SessionDetailResponse sdr = new SessionDetailResponse(
                501L, 10L, "Yoga Flow", "YOGA", 60, "LOW", "LOW",
                5L, "Aisha Sharma", clock.instant().plusSeconds(3600),
                clock.instant().plusSeconds(7200), "Studio A", 20, 5, 15,
                ClassSession.Status.SCHEDULED, null, 100000L, null, 0, clock.instant()
        );

        Mockito.when(classSessionService.getSessions(any(), any(), any(), any(), isNull(), any(), anyInt(), anyInt(), eq(42L)))
                .thenReturn(new PageResponse<>(List.of(sdr), 0, 50, 1L, 1));

        ToolResult result = tool.execute(context, Map.of("category", "YOGA"));

        assertTrue(result.success());
        assertTrue(result.summary().contains("Found 1 scheduled sessions"));
        assertNotNull(result.data().get("sessions"));
    }

    @Test
    @DisplayName("Tool: get_class_details returns class metadata")
    void testGetClassDetailsTool() {
        GetClassDetailsTool tool = new GetClassDetailsTool(fitnessClassService, objectMapper);

        ClassDetailResponse cdr = new ClassDetailResponse(
                10L, "Yoga Flow", "Mindful flow", ClassCategory.YOGA,
                60, ClassIntensity.LOW, ClassImpactLevel.LOW,
                new java.math.BigDecimal("3.0"), List.of(), 18, 20,
                true, true, 1L, 1, clock.instant(), clock.instant()
        );
        Mockito.when(fitnessClassService.getClassById(10L, false)).thenReturn(cdr);

        ToolResult result = tool.execute(context, Map.of("classId", 10));

        assertTrue(result.success());
        assertEquals("Yoga Flow", result.data().get("name"));
    }

    @Test
    @DisplayName("Tool: get_my_bookings returns caller's bookings")
    void testGetMyBookingsTool() {
        GetMyBookingsTool tool = new GetMyBookingsTool(bookingService, objectMapper);

        MemberBookingResponse mbr = new MemberBookingResponse(
                201L, 501L, "Yoga Flow", "YOGA",
                clock.instant().plusSeconds(3600), clock.instant().plusSeconds(7200),
                "Studio A", "Aisha Sharma", "CONFIRMED", clock.instant(), null
        );

        Mockito.when(bookingService.getMemberBookings(eq(42L), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(mbr), 0, 50, 1L, 1));

        ToolResult result = tool.execute(context, Map.of());

        assertTrue(result.success());
        assertEquals(1, result.data().get("count"));
    }

    @Test
    @DisplayName("Tool: get_membership_status returns active plan details")
    void testGetMembershipStatusTool() {
        GetMembershipStatusTool tool = new GetMembershipStatusTool(membershipService);

        MemberMembershipSummaryResponse.PaidMembershipDto paid = new MemberMembershipSummaryResponse.PaidMembershipDto(
                "PRO", "FitFlow Pro", clock.instant(), clock.instant().plusSeconds(30 * 86400), 12, 30
        );
        Mockito.when(membershipService.getMemberMembership(42L))
                .thenReturn(new MemberMembershipSummaryResponse(3, paid));

        ToolResult result = tool.execute(context, Map.of());

        assertTrue(result.success());
        assertEquals("PRO", result.data().get("planCode"));
        assertEquals(12, result.data().get("remainingSessions"));
    }

    @Test
    @DisplayName("Tool: list_plans returns active plan catalog")
    void testListPlansTool() {
        ListPlansTool tool = new ListPlansTool(membershipService);

        PlanDto p1 = new PlanDto(1L, "BASIC", "Basic Plan", 299900L, 30, 8, false, 0, true, 1);
        Mockito.when(membershipService.getActivePlans()).thenReturn(List.of(p1));

        ToolResult result = tool.execute(context, Map.of());

        assertTrue(result.success());
        assertNotNull(result.data().get("plans"));
    }

    @Test
    @DisplayName("Tool: preview_membership_change returns financial calculations")
    void testPreviewMembershipChangeTool() {
        PreviewMembershipChangeTool tool = new PreviewMembershipChangeTool(membershipService, objectMapper);

        ChangePreviewResponse cpr = new ChangePreviewResponse(
                "BASIC", "PRO", 299900L, 15, 30, 4, 8,
                new BigDecimal("0.5"), new BigDecimal("0.5"), new BigDecimal("0.5"),
                149950L, 349950L, 0L, "PRORATED", List.of("Credit calculated: Rs 1499"), 0L
        );
        Mockito.when(membershipService.changePreview(eq(42L), any())).thenReturn(cpr);

        ToolResult result = tool.execute(context, Map.of("targetPlanCode", "PRO"));

        assertTrue(result.success());
        assertEquals(349950L, result.data().get("amountDuePaise"));
    }

    @Test
    @DisplayName("Tool: get_safe_classes returns eligible and avoid sessions")
    void testGetSafeClassesTool() {
        GetSafeClassesTool tool = new GetSafeClassesTool(wellnessProfileService, objectMapper);

        SessionDetailResponse sdr = new SessionDetailResponse(
                501L, 10L, "Yoga Flow", "YOGA", 60, "LOW", "LOW",
                5L, "Aisha", clock.instant().plusSeconds(3600),
                clock.instant().plusSeconds(7200), "A", 20, 5, 15,
                ClassSession.Status.SCHEDULED, null, 100000L, null, 0, clock.instant()
        );
        SafeSessionItem item = new SafeSessionItem(sdr, SafetyVerdictType.ALLOWED, List.of(), List.of("Gentle yoga"));
        SafeClassesResponse scr = new SafeClassesResponse(false, "Disclaimer", null, List.of(item), List.of());

        Mockito.when(wellnessProfileService.getSafeClasses(eq(42L), any(), any(), anyInt(), anyInt())).thenReturn(scr);

        ToolResult result = tool.execute(context, Map.of());

        assertTrue(result.success());
        assertFalse((Boolean) result.data().get("doctorCheckRecommended"));
    }

    @Test
    @DisplayName("Tool: get_safe_classes passes through CONSENT_REQUIRED")
    void testGetSafeClassesConsentRequired() {
        GetSafeClassesTool tool = new GetSafeClassesTool(wellnessProfileService, objectMapper);

        Mockito.when(wellnessProfileService.getSafeClasses(eq(42L), any(), any(), anyInt(), anyInt()))
                .thenThrow(ApiException.consentRequired("Explicit consent required"));

        ToolResult result = tool.execute(context, Map.of());

        assertFalse(result.success());
        assertEquals("CONSENT_REQUIRED", result.errorCode());
    }

    @Test
    @DisplayName("Tool: get_llm_safe_profile returns profile when consent granted")
    void testGetLlmSafeProfile() {
        GetLlmSafeProfileTool tool = new GetLlmSafeProfileTool(wellnessProfileService);

        LlmSafeProfile lsp = new LlmSafeProfile(
                "30-39", Sex.FEMALE, ExperienceLevel.BEGINNER, List.of(Goal.GENERAL_FITNESS),
                List.of(), List.of(), ScreeningStatus.CLEAR, false
        );
        Mockito.when(wellnessProfileService.getLlmSafeProfile(42L)).thenReturn(Optional.of(lsp));

        ToolResult result = tool.execute(context, Map.of());

        assertTrue(result.success());
        assertTrue((Boolean) result.data().get("consentGranted"));
        assertEquals("BEGINNER", result.data().get("experienceLevel"));
    }

    @Test
    @DisplayName("Tool: propose_booking creates a pending action")
    void testProposeBookingTool() {
        ProposeBookingTool tool = new ProposeBookingTool(classSessionService, membershipService, pendingActionService, objectMapper);

        SessionDetailResponse sdr = new SessionDetailResponse(
                501L, 10L, "Yoga Flow", "YOGA", 60, "LOW", "LOW",
                5L, "Aisha", clock.instant().plusSeconds(3600),
                clock.instant().plusSeconds(7200), "A", 20, 5, 15,
                ClassSession.Status.SCHEDULED, null, 100000L, null, 0, clock.instant()
        );
        Mockito.when(classSessionService.getSessionById(501L, 42L)).thenReturn(sdr);

        MemberMembershipSummaryResponse.PaidMembershipDto paid = new MemberMembershipSummaryResponse.PaidMembershipDto(
                "PRO", "FitFlow Pro", clock.instant(), clock.instant().plusSeconds(30 * 86400), 5, 30
        );
        Mockito.when(membershipService.getMemberMembership(42L)).thenReturn(new MemberMembershipSummaryResponse(3, paid));

        Mockito.when(pendingActionService.propose(eq(10L), eq(42L), eq("propose_booking"), any(), any()))
                .thenReturn(new PendingActionProposal(99L, "propose_booking", "Book Yoga", clock.instant().plusSeconds(600)));

        ToolResult result = tool.execute(context, Map.of("sessionId", 501L));

        assertTrue(result.success());
        assertEquals(99L, result.data().get("pendingActionId"));
    }

    @Test
    @DisplayName("Tool: propose_booking fails if session is in the past")
    void testProposeBookingFailsInPast() {
        ProposeBookingTool tool = new ProposeBookingTool(classSessionService, membershipService, pendingActionService, objectMapper);

        SessionDetailResponse sdr = new SessionDetailResponse(
                501L, 10L, "Past Class", "YOGA", 60, "LOW", "LOW",
                5L, "Aisha", clock.instant().minusSeconds(3600),
                clock.instant().minusSeconds(1800), "A", 20, 5, 15,
                ClassSession.Status.SCHEDULED, null, 100000L, null, 0, clock.instant()
        );
        Mockito.when(classSessionService.getSessionById(501L, 42L)).thenReturn(sdr);

        ToolResult result = tool.execute(context, Map.of("sessionId", 501L));

        assertFalse(result.success());
        assertEquals("SESSION_IN_PAST", result.errorCode());
    }

    @Test
    @DisplayName("Tool: propose_cancel_booking creates pending action")
    void testProposeCancelBookingTool() {
        ProposeCancelBookingTool tool = new ProposeCancelBookingTool(bookingService, pendingActionService, objectMapper);

        MemberBookingResponse mbr = new MemberBookingResponse(
                201L, 501L, "Yoga Flow", "YOGA",
                clock.instant().plusSeconds(3600), clock.instant().plusSeconds(7200),
                "A", "Aisha", "CONFIRMED", clock.instant(), null
        );
        Mockito.when(bookingService.getMemberBookings(eq(42L), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(mbr), 0, 100, 1L, 1));

        Mockito.when(pendingActionService.propose(eq(10L), eq(42L), eq("propose_cancel_booking"), any(), any()))
                .thenReturn(new PendingActionProposal(101L, "propose_cancel_booking", "Cancel booking #201", clock.instant().plusSeconds(600)));

        ToolResult result = tool.execute(context, Map.of("bookingId", 201L));

        assertTrue(result.success());
        assertEquals(101L, result.data().get("pendingActionId"));
    }

    @Test
    @DisplayName("Tool: propose_membership_change creates pending action")
    void testProposeMembershipChangeTool() {
        ProposeMembershipChangeTool tool = new ProposeMembershipChangeTool(membershipService, pendingActionService, objectMapper);

        ChangePreviewResponse cpr = new ChangePreviewResponse(
                "BASIC", "PRO", 299900L, 15, 30, 4, 8,
                new BigDecimal("0.5"), new BigDecimal("0.5"), new BigDecimal("0.5"),
                149950L, 349950L, 0L, "PRORATED", List.of("Credit calculated"), 0L
        );
        Mockito.when(membershipService.changePreview(eq(42L), any())).thenReturn(cpr);

        Mockito.when(pendingActionService.propose(eq(10L), eq(42L), eq("propose_membership_change"), any(), any()))
                .thenReturn(new PendingActionProposal(102L, "propose_membership_change", "Change to PRO", clock.instant().plusSeconds(600)));

        ToolResult result = tool.execute(context, Map.of("targetPlanCode", "PRO"));

        assertTrue(result.success());
        assertEquals(102L, result.data().get("pendingActionId"));
    }

    @Test
    @DisplayName("Tool: propose_profile_save rejects under-18 member")
    void testProposeProfileSaveUnder18Fails() {
        ProposeProfileSaveTool tool = new ProposeProfileSaveTool(userService, ageService, pendingActionService, objectMapper);

        UserProfileResponse upr = new UserProfileResponse(
                42L, "teen@test.com", "Teen User", "+919876543210",
                LocalDate.of(2012, 1, 1), List.of("MEMBER"), "ACTIVE",
                clock.instant(), null, 1
        );
        Mockito.when(userService.getProfile(42L)).thenReturn(upr);
        Mockito.when(ageService.is18Plus(LocalDate.of(2012, 1, 1))).thenReturn(false);

        FitnessProfile fp = new FitnessProfile(
                ExperienceLevel.BEGINNER, null, List.of(Goal.GENERAL_FITNESS),
                3, List.of(), List.of(), Set.of(), Map.of(), false,
                new BigDecimal("170"), new BigDecimal("70"), Sex.MALE, null, 1
        );

        ToolResult result = tool.execute(context, Map.of("profileDraft", fp));

        assertFalse(result.success());
        assertEquals("AGE_RESTRICTED", result.errorCode());
    }
}
