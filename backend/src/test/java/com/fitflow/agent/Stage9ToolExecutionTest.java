package com.fitflow.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fitflow.agent.dto.AtRiskMemberDto;
import com.fitflow.agent.dto.DemandInsightDto;
import com.fitflow.agent.dto.PendingActionProposal;
import com.fitflow.agent.service.AtRiskService;
import com.fitflow.agent.service.DemandService;
import com.fitflow.agent.service.PendingActionService;
import com.fitflow.agent.service.WeeklyPlanService;
import com.fitflow.agent.service.WeeklyPlanService.CandidatePlanResult;
import com.fitflow.agent.tools.BuildWeeklyPlanCandidatesTool;
import com.fitflow.agent.tools.DraftClassMetadataTool;
import com.fitflow.agent.tools.GetCalorieNumbersTool;
import com.fitflow.agent.tools.GetClassBurnTool;
import com.fitflow.agent.tools.GetDemandInsightsTool;
import com.fitflow.agent.tools.GetMissedSessionsCatchupTool;
import com.fitflow.agent.tools.GetMyEarningsTool;
import com.fitflow.agent.tools.GetMySessionsTool;
import com.fitflow.agent.tools.GetSessionPrepSummaryTool;
import com.fitflow.agent.tools.GetWeeklyProgressTool;
import com.fitflow.agent.tools.ListAtRiskMembersTool;
import com.fitflow.agent.tools.ProposeAddSessionTool;
import com.fitflow.agent.tools.ProposeCreateClassTool;
import com.fitflow.agent.tools.ProposeRetentionOfferTool;
import com.fitflow.agent.tools.ProposeWeeklyPlanTool;
import com.fitflow.agent.tools.ToolContext;
import com.fitflow.agent.tools.ToolResult;
import com.fitflow.payout.PayoutService;
import com.fitflow.payout.dto.TrainerEarningsResponse;
import com.fitflow.session.ClassSessionService;
import com.fitflow.session.dto.SessionDetailResponse;
import com.fitflow.session.dto.TrainerSessionResponse;
import com.fitflow.wellness.CalorieService;
import com.fitflow.wellness.TrainerPrepService;
import com.fitflow.wellness.WellnessFeedbackService;
import com.fitflow.wellness.WellnessProfileService;
import com.fitflow.wellness.dto.ClassBurnResponse;
import com.fitflow.wellness.dto.NutritionNumbersResponse;
import com.fitflow.wellness.dto.SafeClassesResponse;
import com.fitflow.wellness.dto.SafeSessionItem;
import com.fitflow.wellness.dto.TrainerPrepResponse;
import com.fitflow.wellness.dto.WeeklyProgressResponse;
import com.fitflow.wellness.model.SafetyVerdictType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

class Stage9ToolExecutionTest {

    private ObjectMapper objectMapper;
    private Clock fixedClock;

    private ToolContext memberContext;
    private ToolContext trainerContext;
    private ToolContext adminContext;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        fixedClock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC);

        memberContext = new ToolContext(1L, Set.of("MEMBER"), Set.of(), 100L, fixedClock);
        trainerContext = new ToolContext(2L, Set.of("TRAINER"), Set.of(), 101L, fixedClock);
        adminContext = new ToolContext(3L, Set.of("ADMIN"), Set.of("USER_READ", "USER_EDIT", "CLASS_READ", "CLASS_EDIT"), 102L, fixedClock);
    }

    private SessionDetailResponse createSession(Long id, String name, String category, String intensity, Instant startsAt) {
        return new SessionDetailResponse(
                id, 100L, name, category, 45, intensity, "LOW",
                2L, "Trainer 2", startsAt, startsAt.plusSeconds(45 * 60),
                "Studio A", 20, 5, 15,
                com.fitflow.session.entity.ClassSession.Status.SCHEDULED,
                null, null, null, 1, startsAt
        );
    }

    @Test
    @DisplayName("Tool 1: BuildWeeklyPlanCandidatesTool executes and returns candidates")
    void testBuildWeeklyPlanCandidatesTool() {
        WeeklyPlanService weeklyPlanService = Mockito.mock(WeeklyPlanService.class);
        BuildWeeklyPlanCandidatesTool tool = new BuildWeeklyPlanCandidatesTool(weeklyPlanService, objectMapper);

        LocalDate mon = LocalDate.of(2026, 11, 2);
        SessionDetailResponse s = createSession(10L, "Yoga", "YOGA", "LOW", Instant.parse("2026-11-02T10:00:00Z"));
        CandidatePlanResult cpr = new CandidatePlanResult(mon, 1, 3, List.of(s), List.of("Targeting 1 session"));
        Mockito.when(weeklyPlanService.buildWeeklyPlanCandidates(eq(1L), any())).thenReturn(cpr);

        ToolResult res = tool.execute(memberContext, Map.of("weekStart", "2026-11-02"));
        assertTrue(res.success());
        assertTrue(res.summary().contains("1 candidate session"));
    }

    @Test
    @DisplayName("Tool 2: ProposeWeeklyPlanTool proposes pending action")
    void testProposeWeeklyPlanTool() {
        WeeklyPlanService weeklyPlanService = Mockito.mock(WeeklyPlanService.class);
        PendingActionService pendingActionService = Mockito.mock(PendingActionService.class);
        ProposeWeeklyPlanTool tool = new ProposeWeeklyPlanTool(weeklyPlanService, pendingActionService, objectMapper);

        Mockito.when(weeklyPlanService.proposeWeeklyPlan(eq(1L), any(), eq(List.of(10L)), eq(100L))).thenReturn(55L);
        PendingActionProposal proposal = new PendingActionProposal(
                801L, "propose_weekly_plan", "summary", Instant.now().plusSeconds(3600)
        );
        Mockito.when(pendingActionService.propose(eq(100L), eq(1L), eq("propose_weekly_plan"), any(), any()))
                .thenReturn(proposal);

        ToolResult res = tool.execute(memberContext, Map.of("weekStart", "2026-11-02", "sessionIds", List.of(10L)));
        assertTrue(res.success());
        assertEquals(801L, res.data().get("actionId"));
        assertTrue(res.summary().contains("weekly workout plan"));
    }

    @Test
    @DisplayName("Tool 3: GetWeeklyProgressTool returns progress numbers")
    void testGetWeeklyProgressTool() {
        WellnessFeedbackService wellnessFeedbackService = Mockito.mock(WellnessFeedbackService.class);
        GetWeeklyProgressTool tool = new GetWeeklyProgressTool(wellnessFeedbackService, objectMapper);

        WeeklyProgressResponse wpr = new WeeklyProgressResponse(4, 3, 0, 4, 1050, List.of());
        Mockito.when(wellnessFeedbackService.getWeeklyProgress(eq(1L), any())).thenReturn(wpr);

        ToolResult res = tool.execute(memberContext, Map.of());
        assertTrue(res.success());
        assertTrue(res.summary().contains("3 attended of 4 booked"));
    }

    @Test
    @DisplayName("Tool 4: GetMissedSessionsCatchupTool returns up to 2 safe sessions")
    void testGetMissedSessionsCatchupTool() {
        WellnessProfileService wellnessProfileService = Mockito.mock(WellnessProfileService.class);
        GetMissedSessionsCatchupTool tool = new GetMissedSessionsCatchupTool(wellnessProfileService, objectMapper);

        SessionDetailResponse s = createSession(11L, "Pilates", "PILATES", "MEDIUM", Instant.parse("2026-11-04T10:00:00Z"));
        SafeClassesResponse safeResp = new SafeClassesResponse(
                false, "Disclaimer", "Note",
                List.of(new SafeSessionItem(s, SafetyVerdictType.ALLOWED, List.of(), List.of())),
                List.of()
        );
        Mockito.when(wellnessProfileService.getSafeClasses(eq(1L), any(), any(), eq(0), eq(50))).thenReturn(safeResp);

        ToolResult res = tool.execute(memberContext, Map.of());
        assertTrue(res.success());
        assertTrue(res.summary().contains("1 recommended catch-up session"));
    }

    @Test
    @DisplayName("Tool 5: GetCalorieNumbersTool returns BMR/TDEE and passes through CONSULT_PROFESSIONAL")
    void testGetCalorieNumbersTool() {
        CalorieService calorieService = Mockito.mock(CalorieService.class);
        GetCalorieNumbersTool tool = new GetCalorieNumbersTool(calorieService, objectMapper);

        NutritionNumbersResponse nnr = new NutritionNumbersResponse(
                true, null, 1650, 2200, 1900, false, List.of(),
                "Wellness suggestions, not medical advice. Rules are illustrative and must be reviewed by a qualified professional before real use."
        );
        Mockito.when(calorieService.getNutritionNumbers(1L)).thenReturn(nnr);

        ToolResult res = tool.execute(memberContext, Map.of());
        assertTrue(res.success());
        assertTrue(res.summary().contains("BMR 1650 kcal"));
        assertTrue(res.summary().contains("Wellness suggestions, not medical advice"));
    }

    @Test
    @DisplayName("Tool 6: GetClassBurnTool returns kcal and disclaimer")
    void testGetClassBurnTool() {
        CalorieService calorieService = Mockito.mock(CalorieService.class);
        GetClassBurnTool tool = new GetClassBurnTool(calorieService, objectMapper);

        ClassBurnResponse cbr = new ClassBurnResponse(
                280,
                "Based on duration 50 min and MET 3.5",
                "Wellness suggestions, not medical advice. Rules are illustrative and must be reviewed by a qualified professional before real use."
        );
        Mockito.when(calorieService.getClassBurn(1L, 101L)).thenReturn(cbr);

        ToolResult res = tool.execute(memberContext, Map.of("classId", 101L));
        assertTrue(res.success());
        assertTrue(res.summary().contains("280 kcal"));
    }

    @Test
    @DisplayName("Tool 7: GetSessionPrepSummaryTool returns anonymous counts")
    void testGetSessionPrepSummaryTool() {
        TrainerPrepService trainerPrepService = Mockito.mock(TrainerPrepService.class);
        GetSessionPrepSummaryTool tool = new GetSessionPrepSummaryTool(trainerPrepService, objectMapper);

        TrainerPrepResponse tpr = new TrainerPrepResponse(
                12, true, Map.of("KNEE", 2),
                List.of(),
                "Anonymous aggregated summary; member identities withheld per DPDP Act."
        );
        Mockito.when(trainerPrepService.getTrainerPrepSummary(2L, 201L)).thenReturn(tpr);

        ToolResult res = tool.execute(trainerContext, Map.of("sessionId", 201L));
        assertTrue(res.success());
        assertTrue(res.summary().contains("12 attendees"));
        assertTrue(res.data().get("note").toString().contains("identities withheld"));
    }

    @Test
    @DisplayName("Tool 8: GetMySessionsTool returns trainer's sessions")
    void testGetMySessionsTool() {
        ClassSessionService sessionService = Mockito.mock(ClassSessionService.class);
        GetMySessionsTool tool = new GetMySessionsTool(sessionService, objectMapper);

        TrainerSessionResponse tsr = new TrainerSessionResponse(
                301L, 1L, "Power Yoga",
                Instant.parse("2026-11-04T10:00:00Z"), Instant.parse("2026-11-04T11:00:00Z"),
                "Studio A", 20, 8, com.fitflow.session.entity.ClassSession.Status.SCHEDULED,
                50000L, 8, List.of()
        );
        Mockito.when(sessionService.getTrainerSessions(eq(2L), any(), any())).thenReturn(List.of(tsr));

        ToolResult res = tool.execute(trainerContext, Map.of("from", "2026-11-01T00:00:00Z", "to", "2026-11-07T00:00:00Z"));
        assertTrue(res.success());
        assertTrue(res.summary().contains("1 scheduled session(s)"));
    }

    @Test
    @DisplayName("Tool 9: GetMyEarningsTool returns financial numbers from service only")
    void testGetMyEarningsTool() {
        PayoutService payoutService = Mockito.mock(PayoutService.class);
        GetMyEarningsTool tool = new GetMyEarningsTool(payoutService, objectMapper);

        TrainerEarningsResponse ter = new TrainerEarningsResponse(
                500000L, 100000L, 400000L, List.of()
        );
        Mockito.when(payoutService.getTrainerEarnings(eq(2L), any(), any())).thenReturn(ter);

        ToolResult res = tool.execute(trainerContext, Map.of("from", "2026-10-01T00:00:00Z", "to", "2026-10-31T23:59:59Z"));
        assertTrue(res.success());
        assertTrue(res.summary().contains("500000 paise"));
    }

    @Test
    @DisplayName("Tool 10: ListAtRiskMembersTool executes successfully with limit")
    void testListAtRiskMembersTool() {
        AtRiskService atRiskService = Mockito.mock(AtRiskService.class);
        ListAtRiskMembersTool tool = new ListAtRiskMembersTool(atRiskService, objectMapper);

        AtRiskMemberDto arm = new AtRiskMemberDto(15L, "Charlie", List.of("ATTENDANCE_DROP_50_PERCENT"), null);
        Mockito.when(atRiskService.listAtRiskMembers(10)).thenReturn(List.of(arm));

        ToolResult successRes = tool.execute(adminContext, Map.of("limit", 10));
        assertTrue(successRes.success());
        assertTrue(successRes.summary().contains("1 at-risk member(s)"));
    }

    @Test
    @DisplayName("Tool 11: ProposeRetentionOfferTool proposes offer when member is at-risk")
    void testProposeRetentionOfferTool() {
        AtRiskService atRiskService = Mockito.mock(AtRiskService.class);
        PendingActionService pendingActionService = Mockito.mock(PendingActionService.class);
        ProposeRetentionOfferTool tool = new ProposeRetentionOfferTool(atRiskService, pendingActionService, objectMapper);

        Mockito.when(atRiskService.isMemberAtRisk(15L)).thenReturn(true);
        PendingActionProposal pap = new PendingActionProposal(
                901L, "propose_retention_offer", "Grant 1 free session to member 15", Instant.now().plusSeconds(86400)
        );
        Mockito.when(pendingActionService.propose(eq(102L), eq(3L), eq("propose_retention_offer"), any(), any())).thenReturn(pap);

        ToolResult res = tool.execute(adminContext, Map.of("memberId", 15L, "offerType", "EXTRA_FREE_SESSION"));
        assertTrue(res.success());
        assertEquals(901L, res.data().get("actionId"));
        assertTrue(res.summary().contains("Propose retention offer"));
    }

    @Test
    @DisplayName("Tool 12: DraftClassMetadataTool returns guidance and validates draft attributes")
    void testDraftClassMetadataTool() {
        DraftClassMetadataTool tool = new DraftClassMetadataTool(objectMapper);
        tool.initMetRanges();

        ToolResult res = tool.execute(adminContext, Map.of("name", "Power Vinyasa", "description", "Dynamic flow"));
        assertTrue(res.success());
        assertTrue(res.summary().contains("Ready to draft class 'Power Vinyasa'"));

        // Validate draft method directly
        Map<String, Object> validDraft = Map.of(
                "name", "Power Vinyasa",
                "category", "YOGA",
                "intensity", "MEDIUM",
                "impactLevel", "LOW",
                "metValue", 3.0,
                "durationMinutes", 60,
                "defaultCapacity", 25,
                "minAge", 16
        );
        List<String> errors = tool.validateDraft(validDraft);
        assertTrue(errors.isEmpty());

        // Invalid MET value
        Map<String, Object> invalidDraft = Map.of(
                "name", "Power Vinyasa",
                "category", "YOGA",
                "intensity", "MEDIUM",
                "impactLevel", "LOW",
                "metValue", 9.0 // Too high for YOGA
        );
        List<String> invalidErrors = tool.validateDraft(invalidDraft);
        assertFalse(invalidErrors.isEmpty());
        assertTrue(invalidErrors.get(0).contains("out of range"));
    }

    @Test
    @DisplayName("Tool 13: ProposeCreateClassTool validates metadata draft and proposes action")
    void testProposeCreateClassTool() {
        DraftClassMetadataTool draftClassMetadataTool = Mockito.mock(DraftClassMetadataTool.class);
        PendingActionService pendingActionService = Mockito.mock(PendingActionService.class);
        ProposeCreateClassTool tool = new ProposeCreateClassTool(draftClassMetadataTool, pendingActionService, objectMapper);

        Map<String, Object> draft = Map.of(
                "name", "Pilates Reformer",
                "category", "PILATES",
                "intensity", "MEDIUM",
                "impactLevel", "LOW",
                "metValue", 3.5
        );
        Mockito.when(draftClassMetadataTool.validateDraft(draft)).thenReturn(List.of());

        PendingActionProposal pap = new PendingActionProposal(
                902L, "propose_create_class", "Create draft class Pilates Reformer", Instant.now().plusSeconds(86400)
        );
        Mockito.when(pendingActionService.propose(eq(102L), eq(3L), eq("propose_create_class"), eq(draft), any())).thenReturn(pap);

        ToolResult res = tool.execute(adminContext, Map.of("draft", draft));
        assertTrue(res.success());
        assertEquals(902L, res.data().get("actionId"));
    }

    @Test
    @DisplayName("Tool 14: GetDemandInsightsTool returns suggestions")
    void testGetDemandInsightsTool() {
        DemandService demandService = Mockito.mock(DemandService.class);
        GetDemandInsightsTool tool = new GetDemandInsightsTool(demandService, objectMapper);

        DemandInsightDto dto = new DemandInsightDto(
                List.of(new DemandInsightDto.DemandSuggestionDto("ADD_SESSION", 101L, "Vinyasa Flow", 1, 18, "Fill rate 95%")),
                List.of()
        );
        Mockito.when(demandService.getDemandInsights(anyInt())).thenReturn(dto);

        ToolResult res = tool.execute(adminContext, Map.of("weeks", 4));
        assertTrue(res.success());
        assertTrue(res.summary().contains("1 schedule recommendation(s)"));
    }

    @Test
    @DisplayName("Tool 15: ProposeAddSessionTool validates demand suggestion and creates proposal")
    void testProposeAddSessionTool() {
        DemandService demandService = Mockito.mock(DemandService.class);
        PendingActionService pendingActionService = Mockito.mock(PendingActionService.class);
        ProposeAddSessionTool tool = new ProposeAddSessionTool(demandService, pendingActionService, objectMapper);

        // Monday 18:00
        Instant startsAt = Instant.parse("2026-11-02T18:00:00Z");
        Mockito.when(demandService.hasAddSessionSuggestion(eq(101L), anyInt(), anyInt())).thenReturn(true);

        PendingActionProposal pap = new PendingActionProposal(
                903L, "propose_add_session", "Add session for class 101", Instant.now().plusSeconds(86400)
        );
        Mockito.when(pendingActionService.propose(eq(102L), eq(3L), eq("propose_add_session"), any(), any())).thenReturn(pap);

        Map<String, Object> args = Map.of(
                "classId", 101L,
                "trainerId", 2L,
                "startsAt", startsAt.toString(),
                "room", "Studio A"
        );
        ToolResult res = tool.execute(adminContext, args);
        assertTrue(res.success());
        assertEquals(903L, res.data().get("actionId"));
    }
}
