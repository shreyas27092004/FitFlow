package com.fitflow.agent;

import com.fitflow.agent.agents.BookingAgent;
import com.fitflow.agent.agents.IntakeAgent;
import com.fitflow.agent.agents.MembershipAgent;
import com.fitflow.agent.agents.SafeClassMatcherAgent;
import com.fitflow.agent.core.IntentClassifier;
import com.fitflow.agent.core.SupervisorRouter;
import com.fitflow.agent.port.ChatMessage;
import com.fitflow.agent.port.ChatModelPort;
import com.fitflow.agent.port.ChatRequest;
import com.fitflow.agent.port.ChatResponse;
import com.fitflow.agent.port.ScriptedChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisorRouterTest {

    private BookingAgent bookingAgent;
    private MembershipAgent membershipAgent;
    private IntakeAgent intakeAgent;
    private SafeClassMatcherAgent safeClassMatcherAgent;
    private IntentClassifier intentClassifier;
    private SupervisorRouter router;
    private ScriptedChatModel scriptedModel;

    @BeforeEach
    void setUp() {
        bookingAgent = Mockito.mock(BookingAgent.class);
        Mockito.when(bookingAgent.name()).thenReturn("BookingAgent");

        membershipAgent = Mockito.mock(MembershipAgent.class);
        Mockito.when(membershipAgent.name()).thenReturn("MembershipAgent");

        intakeAgent = Mockito.mock(IntakeAgent.class);
        Mockito.when(intakeAgent.name()).thenReturn("IntakeAgent");

        safeClassMatcherAgent = Mockito.mock(SafeClassMatcherAgent.class);
        Mockito.when(safeClassMatcherAgent.name()).thenReturn("SafeClassMatcherAgent");

        intentClassifier = new IntentClassifier();
        router = new SupervisorRouter(bookingAgent, membershipAgent, intakeAgent, safeClassMatcherAgent, intentClassifier);
        scriptedModel = new ScriptedChatModel();
    }

    @Test
    @DisplayName("Role Gate: MEMBER role maps strictly to 4 allowed member intents")
    void memberRoleAllowedIntents() {
        Set<IntentClassifier.Intent> intents = router.getAllowedIntentsForRoles(Set.of("MEMBER"));
        assertEquals(4, intents.size());
        assertTrue(intents.contains(IntentClassifier.Intent.BOOKING));
        assertTrue(intents.contains(IntentClassifier.Intent.MEMBERSHIP));
        assertTrue(intents.contains(IntentClassifier.Intent.INTAKE));
        assertTrue(intents.contains(IntentClassifier.Intent.SAFE_CLASS_MATCHER));
    }

    @Test
    @DisplayName("Role Gate: Non-member roles receive no allowed agents in Stage 8")
    void nonMemberReceivesNoAgents() {
        Set<IntentClassifier.Intent> trainer = router.getAllowedIntentsForRoles(Set.of("TRAINER"));
        assertTrue(trainer.isEmpty());

        Set<IntentClassifier.Intent> admin = router.getAllowedIntentsForRoles(Set.of("ADMIN"));
        assertTrue(admin.isEmpty());

        SupervisorRouter.RouteResult res = router.route(1L, Set.of("GUEST"), Set.of(), "hello", scriptedModel);
        assertFalse(res.hasAgent());
        assertEquals("No agent available for caller's role.", res.clarificationMessage());
    }

    @Test
    @DisplayName("Role Gate: MEMBER cannot access administrative or trainer agents")
    void memberCannotAccessAdminAgents() {
        SupervisorRouter.RouteResult res = router.route(1L, Set.of("MEMBER"), Set.of(), "admin dashboard", scriptedModel);
        // Will route to clarification if ambiguous, never an admin agent
        if (res.hasAgent()) {
            assertTrue(Set.of("BookingAgent", "MembershipAgent", "IntakeAgent", "SafeClassMatcherAgent").contains(res.agent().name()));
        } else {
            assertEquals(SupervisorRouter.CLARIFICATION_PROMPT, res.clarificationMessage());
        }
    }

    // 30 Keyword Phrases Test
    @ParameterizedTest
    @ValueSource(strings = {
            "I want to book a yoga class",
            "book my session please",
            "reserve a spot in HIIT",
            "cancel my booking tomorrow",
            "can you cancel my reservation",
            "show my bookings",
            "view my scheduled sessions",
            "what are my upcoming bookings",
            "search sessions for this friday",
            "schedule a class with trainer Aisha"
    })
    @DisplayName("Router Keyword Phrases: 10 Booking intents recognized")
    void bookingPhrasesRecognized(String phrase) {
        SupervisorRouter.RouteResult res = router.route(1L, Set.of("MEMBER"), Set.of(), phrase, scriptedModel);
        assertTrue(res.hasAgent(), "Expected agent for: " + phrase);
        assertEquals("BookingAgent", res.agent().name());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "I want to upgrade my plan",
            "change my membership to PRO",
            "downgrade my subscription",
            "switch my membership tier",
            "how much does the basic plan cost",
            "what is the price of the pro plan",
            "show me available membership plans",
            "list all membership plans",
            "cancel my membership",
            "what is my prorated refund"
    })
    @DisplayName("Router Keyword Phrases: 10 Membership intents recognized")
    void membershipPhrasesRecognized(String phrase) {
        SupervisorRouter.RouteResult res = router.route(1L, Set.of("MEMBER"), Set.of(), phrase, scriptedModel);
        assertTrue(res.hasAgent(), "Expected agent for: " + phrase);
        assertEquals("MembershipAgent", res.agent().name());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "start my health profile questionnaire",
            "fill out my fitness intake",
            "complete my onboarding questions",
            "answer PAR-Q readiness questions",
            "report my knee injury on my profile"
    })
    @DisplayName("Router Keyword Phrases: 5 Intake intents recognized")
    void intakePhrasesRecognized(String phrase) {
        SupervisorRouter.RouteResult res = router.route(1L, Set.of("MEMBER"), Set.of(), phrase, scriptedModel);
        assertTrue(res.hasAgent(), "Expected agent for: " + phrase);
        assertEquals("IntakeAgent", res.agent().name());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "what classes are safe for my knee",
            "which sessions are suitable for lower back pain",
            "safe workouts for moderate ankle injury",
            "match safe classes for my profile",
            "recommend safe sessions this week"
    })
    @DisplayName("Router Keyword Phrases: 5 Safe Class Matcher intents recognized")
    void safeClassMatcherPhrasesRecognized(String phrase) {
        SupervisorRouter.RouteResult res = router.route(1L, Set.of("MEMBER"), Set.of(), phrase, scriptedModel);
        assertTrue(res.hasAgent(), "Expected agent for: " + phrase);
        assertEquals("SafeClassMatcherAgent", res.agent().name());
    }

    @Test
    @DisplayName("Ambiguous Intent: Invokes cheap LLM call and uses validated enum")
    void ambiguousIntentCallsLlm() {
        // Query that doesn't match standard keywords
        String ambiguousQuery = "tell me something interesting";
        scriptedModel.queueResponse("BOOKING");

        SupervisorRouter.RouteResult res = router.route(1L, Set.of("MEMBER"), Set.of(), ambiguousQuery, scriptedModel);
        assertTrue(res.hasAgent());
        assertEquals("BookingAgent", res.agent().name());
        assertEquals(1, scriptedModel.getRecordedRequests().size());
    }

    @Test
    @DisplayName("Ambiguous Intent: Invalid enum or garbage from LLM falls back to clarification prompt")
    void ambiguousIntentInvalidEnumReturnsClarification() {
        String ambiguousQuery = "do some magic";
        scriptedModel.queueResponse("NOT_A_VALID_INTENT_ENUM");

        SupervisorRouter.RouteResult res = router.route(1L, Set.of("MEMBER"), Set.of(), ambiguousQuery, scriptedModel);
        assertFalse(res.hasAgent());
        assertEquals(SupervisorRouter.CLARIFICATION_PROMPT, res.clarificationMessage());
    }
}
