package com.fitflow.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fitflow.agent.agents.AgentPromptLoader;
import com.fitflow.agent.agents.SafeClassMatcherAgent;
import com.fitflow.agent.core.AgentCircuitBreaker;
import com.fitflow.agent.core.AgentRuntime;
import com.fitflow.agent.core.AgentUsageService;
import com.fitflow.agent.core.SupervisorRouter;
import com.fitflow.agent.entity.AgentConversation;
import com.fitflow.agent.guard.InjectionGuard;
import com.fitflow.agent.guard.NoSecretsGuard;
import com.fitflow.agent.guard.NumberGuard;
import com.fitflow.agent.guard.OutputHygieneGuard;
import com.fitflow.agent.guard.RoleGuard;
import com.fitflow.agent.guard.ScopeGuard;
import com.fitflow.agent.memory.AgentMemoryService;
import com.fitflow.agent.port.ScriptedChatModel;
import com.fitflow.agent.tools.GetSafeClassesTool;
import com.fitflow.agent.tools.ToolRegistry;
import com.fitflow.session.dto.SessionDetailResponse;
import com.fitflow.session.entity.ClassSession;
import com.fitflow.wellness.WellnessProfileService;
import com.fitflow.wellness.dto.SafeClassesResponse;
import com.fitflow.wellness.dto.SafeSessionItem;
import com.fitflow.wellness.model.SafetyVerdictType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SafeClassMatcherE2ETest {

    private ScriptedChatModel scriptedModel;
    private SafeClassMatcherAgent agent;
    private SupervisorRouter router;
    private ToolRegistry toolRegistry;
    private AgentMemoryService memoryService;
    private AgentUsageService usageService;
    private AgentCircuitBreaker circuitBreaker;
    private WellnessProfileService wellnessProfileService;

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
        GetSafeClassesTool safeTool = new GetSafeClassesTool(wellnessProfileService, objectMapper);

        toolRegistry = new ToolRegistry(List.of(safeTool), mock(com.fitflow.agent.service.AgentAuditService.class));

        AgentPromptLoader promptLoader = new AgentPromptLoader();
        agent = new SafeClassMatcherAgent(promptLoader);
        router = mock(SupervisorRouter.class);
        when(router.route(any(), any(), any(), any(), any()))
                .thenReturn(new SupervisorRouter.RouteResult(agent, null));

        memoryService = mock(AgentMemoryService.class);
        AgentConversation conv = new AgentConversation();
        conv.setId(20L);
        conv.setUserId(42L);
        when(memoryService.getOrCreateConversation(any(), eq(42L), any())).thenReturn(conv);
        when(memoryService.getRecentContext(20L)).thenReturn(Collections.emptyList());

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
    @DisplayName("SafeClassMatcher E2E: Eligible sessions recommended with human reason from deterministic filter")
    void testEligibleSessionsRecommended() {
        SessionDetailResponse yogaSession = new SessionDetailResponse(
                601L, 10L, "Gentle Yoga", "YOGA", 60, "LOW", "LOW",
                5L, "Aisha Sharma", baseTime.plusSeconds(3600), baseTime.plusSeconds(7200),
                "Studio A", 20, 5, 15, ClassSession.Status.SCHEDULED, null, 100000L, null, 0, baseTime
        );
        SafeSessionItem gentleYoga = new SafeSessionItem(
                yogaSession,
                SafetyVerdictType.ALLOWED,
                List.of("LOW_IMPACT"),
                List.of("Safe: low impact, joint friendly for moderate knee condition")
        );

        SessionDetailResponse hiitSession = new SessionDetailResponse(
                801L, 15L, "HIIT Blast", "HIIT", 45, "HIGH", "HIGH",
                6L, "Rajesh", baseTime.plusSeconds(14400), baseTime.plusSeconds(18000),
                "Studio B", 20, 0, 20, ClassSession.Status.SCHEDULED, null, 150000L, null, 0, baseTime
        );
        SafeSessionItem hiitHard = new SafeSessionItem(
                hiitSession,
                SafetyVerdictType.BLOCKED,
                List.of("HIGH_IMPACT_KNEE"),
                List.of("Avoid: high impact exercise contraindicated for moderate knee injury")
        );

        SafeClassesResponse safeResponse = new SafeClassesResponse(
                false,
                "Wellness suggestions, not medical advice. Rules are illustrative and must be reviewed by a qualified professional before real use.",
                null,
                List.of(gentleYoga),
                List.of(hiitHard)
        );

        when(wellnessProfileService.getSafeClasses(eq(42L), any(), any(), anyInt(), anyInt()))
                .thenReturn(safeResponse);

        // Turn 1: Model requests get_safe_classes
        scriptedModel.queueToolCall("c1", "get_safe_classes", "{}");

        // Turn 2: Model recommends Gentle Yoga and discloses HIIT avoid
        scriptedModel.queueResponse(
                "Based on your profile, I recommend Gentle Yoga (Session #601) because it is low impact and joint friendly. Please avoid HIIT Blast (Session #801) due to knee impact."
        );

        AgentRuntime.AgentResponse response = runtime.run(
                new AgentRuntime.AgentRequest(42L, Set.of("MEMBER"), Set.of(), 20L, "What classes are safe for my knee?")
        );

        assertNotNull(response);
        assertTrue(response.reply().contains("Gentle Yoga"));
        assertTrue(response.reply().contains("601"));
        assertFalse(response.circuitBreakerTripped());
    }

    @Test
    @DisplayName("SafeClassMatcher E2E: Hallucinated recommendation of blocked/avoided class is caught by post-check and replaced")
    void testBlockedClassRecommendationReplacedByPostCheck() {
        SessionDetailResponse yogaSession = new SessionDetailResponse(
                601L, 10L, "Gentle Yoga", "YOGA", 60, "LOW", "LOW",
                5L, "Aisha", baseTime.plusSeconds(3600), baseTime.plusSeconds(7200),
                "Studio A", 20, 5, 15, ClassSession.Status.SCHEDULED, null, 100000L, null, 0, baseTime
        );
        SafeSessionItem gentleYoga = new SafeSessionItem(
                yogaSession,
                SafetyVerdictType.ALLOWED,
                List.of("LOW_IMPACT"),
                List.of("Safe for knee")
        );

        SessionDetailResponse hiitSession = new SessionDetailResponse(
                801L, 15L, "HIIT Extreme", "HIIT", 45, "HIGH", "HIGH",
                6L, "Rajesh", baseTime.plusSeconds(14400), baseTime.plusSeconds(18000),
                "Studio B", 20, 0, 20, ClassSession.Status.SCHEDULED, null, 150000L, null, 0, baseTime
        );
        SafeSessionItem hiitBlocked = new SafeSessionItem(
                hiitSession,
                SafetyVerdictType.BLOCKED,
                List.of("HIGH_IMPACT_KNEE"),
                List.of("Avoid: contraindicated for knee")
        );

        SafeClassesResponse safeResponse = new SafeClassesResponse(
                false,
                "Wellness suggestions, not medical advice.",
                null,
                List.of(gentleYoga),
                List.of(hiitBlocked)
        );

        when(wellnessProfileService.getSafeClasses(eq(42L), any(), any(), anyInt(), anyInt()))
                .thenReturn(safeResponse);

        // Turn 1: Model calls get_safe_classes
        scriptedModel.queueToolCall("c1", "get_safe_classes", "{}");

        // Turn 2: Roguish model attempts to recommend the blocked class 801
        scriptedModel.queueResponse(
                "You should push through the pain! I recommend attending session 801 today."
        );

        AgentRuntime.AgentResponse response = runtime.run(
                new AgentRuntime.AgentRequest(42L, Set.of("MEMBER"), Set.of(), 20L, "Recommend sessions for me")
        );

        assertNotNull(response);
        // Post-check replaced the unsafe recommendation with safe fallback containing turn tool summaries
        assertFalse(response.reply().contains("push through the pain"));
        assertTrue(response.reply().contains("Here are the safe sessions identified for you"));
    }
}
