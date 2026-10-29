package com.fitflow.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fitflow.agent.agents.AgentPromptLoader;
import com.fitflow.agent.agents.IntakeAgent;
import com.fitflow.agent.core.AgentCircuitBreaker;
import com.fitflow.agent.core.AgentRuntime;
import com.fitflow.agent.core.AgentUsageService;
import com.fitflow.agent.core.SupervisorRouter;
import com.fitflow.agent.dto.PendingActionProposal;
import com.fitflow.agent.entity.AgentConversation;
import com.fitflow.agent.entity.PendingAction;
import com.fitflow.agent.guard.InjectionGuard;
import com.fitflow.agent.guard.NoSecretsGuard;
import com.fitflow.agent.guard.NumberGuard;
import com.fitflow.agent.guard.OutputHygieneGuard;
import com.fitflow.agent.guard.RoleGuard;
import com.fitflow.agent.guard.ScopeGuard;
import com.fitflow.agent.memory.AgentMemoryService;
import com.fitflow.agent.port.ScriptedChatModel;
import com.fitflow.agent.repository.PendingActionRepository;
import com.fitflow.agent.service.PendingActionService;
import com.fitflow.agent.tools.GetLlmSafeProfileTool;
import com.fitflow.agent.tools.ProposeProfileSaveTool;
import com.fitflow.agent.tools.Tool;
import com.fitflow.agent.tools.ToolContext;
import com.fitflow.agent.tools.ToolRegistry;
import com.fitflow.agent.tools.ToolResult;
import com.fitflow.booking.BookingService;
import com.fitflow.membership.MembershipService;
import com.fitflow.user.AgeService;
import com.fitflow.user.UserService;
import com.fitflow.user.dto.UserProfileResponse;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import com.fitflow.wellness.WellnessProfileService;
import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.LlmSafeProfile;
import com.fitflow.wellness.dto.ProfileResponse;
import com.fitflow.wellness.model.ExperienceLevel;
import com.fitflow.wellness.model.Goal;
import com.fitflow.wellness.model.InjuryArea;
import com.fitflow.wellness.model.InjurySeverity;
import com.fitflow.wellness.model.ScreeningStatus;
import com.fitflow.wellness.model.Sex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntakeAgentE2ETest {

    private ScriptedChatModel scriptedModel;
    private IntakeAgent intakeAgent;
    private SupervisorRouter router;
    private ToolRegistry toolRegistry;
    private AgentMemoryService memoryService;
    private AgentUsageService usageService;
    private AgentCircuitBreaker circuitBreaker;
    private PendingActionService pendingActionService;
    private PendingActionRepository pendingActionRepository;
    private WellnessProfileService wellnessProfileService;
    private UserService userService;
    private AgeService ageService;
    private UserRepository userRepository;
    private UserRoleRepository userRoleRepository;

    private Clock fixedClock;
    private ObjectMapper objectMapper;
    private AgentRuntime runtime;

    private final Instant baseTime = Instant.parse("2026-11-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        scriptedModel = new ScriptedChatModel();
        fixedClock = Clock.fixed(baseTime, ZoneOffset.UTC);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        wellnessProfileService = mock(WellnessProfileService.class);
        userService = mock(UserService.class);
        ageService = mock(AgeService.class);
        userRepository = mock(UserRepository.class);
        userRoleRepository = mock(UserRoleRepository.class);
        pendingActionRepository = mock(PendingActionRepository.class);

        BookingService bookingService = mock(BookingService.class);
        MembershipService membershipService = mock(MembershipService.class);
        com.fitflow.user.repository.AdminPermissionRepository adminPermissionRepository = mock(com.fitflow.user.repository.AdminPermissionRepository.class);
        com.fitflow.agent.service.WeeklyPlanService weeklyPlanService = mock(com.fitflow.agent.service.WeeklyPlanService.class);
        com.fitflow.agent.service.AtRiskService atRiskService = mock(com.fitflow.agent.service.AtRiskService.class);
        com.fitflow.fitnessclass.FitnessClassService fitnessClassService = mock(com.fitflow.fitnessclass.FitnessClassService.class);
        com.fitflow.session.ClassSessionService sessionService = mock(com.fitflow.session.ClassSessionService.class);
        com.fitflow.agent.service.DemandService demandService = mock(com.fitflow.agent.service.DemandService.class);
        com.fitflow.notification.OutboxService outboxService = mock(com.fitflow.notification.OutboxService.class);

        pendingActionService = new PendingActionService(
                pendingActionRepository,
                userRepository,
                userRoleRepository,
                adminPermissionRepository,
                bookingService,
                membershipService,
                wellnessProfileService,
                weeklyPlanService,
                atRiskService,
                fitnessClassService,
                sessionService,
                demandService,
                outboxService,
                fixedClock,
                objectMapper
        );

        GetLlmSafeProfileTool safeProfileTool = new GetLlmSafeProfileTool(wellnessProfileService);
        ProposeProfileSaveTool profileSaveTool = new ProposeProfileSaveTool(userService, ageService, pendingActionService, objectMapper);

        toolRegistry = new ToolRegistry(List.of(safeProfileTool, profileSaveTool), mock(com.fitflow.agent.service.AgentAuditService.class));

        AgentPromptLoader promptLoader = new AgentPromptLoader();
        intakeAgent = new IntakeAgent(promptLoader);
        router = mock(SupervisorRouter.class);
        when(router.route(any(), any(), any(), any(), any()))
                .thenReturn(new SupervisorRouter.RouteResult(intakeAgent, null));

        memoryService = mock(AgentMemoryService.class);
        AgentConversation conv = new AgentConversation();
        conv.setId(10L);
        conv.setUserId(42L);
        when(memoryService.getOrCreateConversation(any(), eq(42L), any())).thenReturn(conv);
        when(memoryService.getRecentContext(10L)).thenReturn(Collections.emptyList());

        usageService = mock(AgentUsageService.class);
        circuitBreaker = new AgentCircuitBreaker(fixedClock);

        runtime = new AgentRuntime(
                scriptedModel,
                router,
                toolRegistry,
                memoryService,
                usageService,
                circuitBreaker,
                new NumberGuard(),
                new InjectionGuard(),
                new ScopeGuard(),
                new RoleGuard(),
                new OutputHygieneGuard(),
                new NoSecretsGuard(),
                fixedClock,
                objectMapper
        );
    }

    @Test
    @DisplayName("Intake E2E: Consent missing -> tool returns consentGranted:false and agent stops with consent prompt")
    void testConsentMissingStopsWithConsentPrompt() {
        // Mock getLlmSafeProfile returning empty (no consent)
        when(wellnessProfileService.getLlmSafeProfile(42L)).thenReturn(Optional.empty());

        // Step 1: Model calls get_llm_safe_profile
        scriptedModel.queueToolCall("c1", "get_llm_safe_profile", "{}");

        // Step 2: Model sees consentGranted: false and stops with consent instructions
        scriptedModel.queueResponse("Please review and grant wellness consent on the consent screen before we can begin your intake.");

        AgentRuntime.AgentResponse response = runtime.run(
                new AgentRuntime.AgentRequest(42L, Set.of("MEMBER"), Set.of(), 10L, "I want to start my health questionnaire")
        );

        assertNotNull(response);
        assertTrue(response.reply().contains("grant wellness consent"));
        assertFalse(response.circuitBreakerTripped());
    }

    @Test
    @DisplayName("Intake E2E: Under 18 -> propose_profile_save tool rejects with AGE_RESTRICTED")
    void testUnder18RejectedByProposeProfileSave() {
        // User is born in 2012 -> under 18
        UserProfileResponse upr = new UserProfileResponse(
                42L, "teen@test.com", "Teen User", "+919876543210",
                LocalDate.of(2012, 1, 1), List.of("MEMBER"), "ACTIVE",
                baseTime, null, 1
        );
        when(userService.getProfile(42L)).thenReturn(upr);
        when(ageService.is18Plus(LocalDate.of(2012, 1, 1))).thenReturn(false);

        ToolContext context = new ToolContext(42L, Set.of("MEMBER"), Set.of(), 10L, fixedClock);
        ProposeProfileSaveTool tool = new ProposeProfileSaveTool(userService, ageService, pendingActionService, objectMapper);

        FitnessProfile fp = new FitnessProfile(
                ExperienceLevel.BEGINNER, "beginner note", List.of(Goal.GENERAL_FITNESS),
                3, List.of(), List.of(), Set.of(), Map.of(), false,
                new BigDecimal("170"), new BigDecimal("70"), Sex.MALE, null, 1
        );

        ToolResult res = tool.execute(context, Map.of("profileDraft", fp));
        assertFalse(res.success());
        assertEquals("AGE_RESTRICTED", res.errorCode());
    }

    @Test
    @DisplayName("Intake E2E: PAR-Q YES -> model requires doctor check message")
    void testParqYesRequiresDoctorMessage() {
        // Consent is granted
        LlmSafeProfile lsp = new LlmSafeProfile(
                "30-39", Sex.FEMALE, ExperienceLevel.BEGINNER, List.of(Goal.GENERAL_FITNESS),
                List.of(), List.of(), ScreeningStatus.CAUTION, false
        );
        when(wellnessProfileService.getLlmSafeProfile(42L)).thenReturn(Optional.of(lsp));

        // Member answered YES to heart condition (Q1)
        scriptedModel.queueResponse(
                "Please check with a doctor before starting. We recommend obtaining medical clearance before participating in intense physical activity."
        );

        AgentRuntime.AgentResponse response = runtime.run(
                new AgentRuntime.AgentRequest(42L, Set.of("MEMBER"), Set.of(), 10L, "I have a heart condition and doctor advised caution")
        );

        assertNotNull(response);
        assertTrue(response.reply().contains("Please check with a doctor before starting"));
    }

    @Test
    @DisplayName("Intake E2E: Full intake flow -> pending action created -> confirmed -> profile saved with correct screening status")
    void testFullIntakeFlowToPendingActionAndConfirm() throws Exception {
        UserProfileResponse upr = new UserProfileResponse(
                42L, "adult@test.com", "Adult User", "+919876543210",
                LocalDate.of(1990, 1, 1), List.of("MEMBER"), "ACTIVE",
                baseTime, null, 1
        );
        when(userService.getProfile(42L)).thenReturn(upr);
        when(ageService.is18Plus(LocalDate.of(1990, 1, 1))).thenReturn(true);

        // When pendingActionRepository saves, return saved action with id 777L
        when(pendingActionRepository.save(any(PendingAction.class))).thenAnswer(inv -> {
            PendingAction pa = inv.getArgument(0);
            if (pa.getId() == null) pa.setId(777L);
            return pa;
        });

        // Step 1: Model calls propose_profile_save
        FitnessProfile profileDraft = new FitnessProfile(
                ExperienceLevel.BEGINNER, "Gentle workouts", List.of(Goal.LOSE_WEIGHT),
                3, List.of(), List.of(new com.fitflow.wellness.dto.InjuryDto(InjuryArea.KNEE, InjurySeverity.MODERATE)),
                Set.of(), Map.of("Q1", false, "Q2", false, "Q3", false, "Q4", false, "Q5", false, "Q6", false, "Q7", false),
                false, new BigDecimal("175"), new BigDecimal("75"), Sex.MALE, null, 1
        );

        String argsJson = objectMapper.writeValueAsString(Map.of("profileDraft", profileDraft));
        scriptedModel.queueToolCall("c1", "propose_profile_save", argsJson);

        // Step 2: Model presents summary and asks member to confirm
        scriptedModel.queueResponse("I have prepared your profile draft (Action #777). Please review and click Confirm on screen.");

        AgentRuntime.AgentResponse agentResp = runtime.run(
                new AgentRuntime.AgentRequest(42L, Set.of("MEMBER"), Set.of(), 10L, "Save my fitness profile")
        );

        assertNotNull(agentResp);
        assertTrue(agentResp.reply().contains("Action #777"));

        // Now member confirms action #777
        PendingAction savedAction = PendingAction.builder()
                .id(777L)
                .conversationId(10L)
                .userId(42L)
                .toolName("propose_profile_save")
                .argsJson(argsJson)
                .summary("Save wellness profile")
                .status(PendingAction.Status.PENDING.name())
                .expiresAt(baseTime.plusSeconds(600))
                .createdAt(baseTime)
                .build();

        when(pendingActionRepository.findById(777L)).thenReturn(Optional.of(savedAction));
        User testUser = new User();
        testUser.setId(42L);
        when(userRepository.findById(42L)).thenReturn(Optional.of(testUser));
        when(userRoleRepository.existsByIdUserIdAndIdRole(42L, UserRole.Role.MEMBER)).thenReturn(true);

        ProfileResponse pr = new ProfileResponse(
                profileDraft,
                new com.fitflow.wellness.dto.ScreeningResult(ScreeningStatus.CAUTION, List.of("KNEE_INJURY"), true)
        );
        when(wellnessProfileService.saveProfile(eq(42L), any())).thenReturn(pr);

        Map<String, Object> confirmResult = pendingActionService.confirm(777L, 42L);

        assertEquals("SAVED", confirmResult.get("status"));
        verify(wellnessProfileService).saveProfile(eq(42L), any(FitnessProfile.class));
    }
}
