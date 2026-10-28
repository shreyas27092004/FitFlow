package com.fitflow.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.agents.BookingAgent;
import com.fitflow.agent.agents.SpecialistAgent;
import com.fitflow.agent.core.AgentCircuitBreaker;
import com.fitflow.agent.core.AgentRuntime;
import com.fitflow.agent.core.AgentUsageService;
import com.fitflow.agent.core.IntentClassifier;
import com.fitflow.agent.core.SupervisorRouter;
import com.fitflow.agent.entity.AgentConversation;
import com.fitflow.agent.guard.InjectionGuard;
import com.fitflow.agent.guard.NoSecretsGuard;
import com.fitflow.agent.guard.NumberGuard;
import com.fitflow.agent.guard.OutputHygieneGuard;
import com.fitflow.agent.guard.RoleGuard;
import com.fitflow.agent.guard.ScopeGuard;
import com.fitflow.agent.memory.AgentMemoryService;
import com.fitflow.agent.port.ChatModelFactory;
import com.fitflow.agent.port.ChatRequest;
import com.fitflow.agent.port.ChatResponse;
import com.fitflow.agent.port.ScriptedChatModel;
import com.fitflow.agent.port.ToolCall;
import com.fitflow.agent.tools.ToolRegistry;
import com.fitflow.agent.tools.ToolResult;
import com.fitflow.settings.entity.AppSetting;
import com.fitflow.settings.repository.AppSettingRepository;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRuntimeTest {

    private ScriptedChatModel scriptedModel;
    private SupervisorRouter router;
    private ToolRegistry toolRegistry;
    private AgentMemoryService memoryService;
    private AgentUsageService usageService;
    private AgentCircuitBreaker circuitBreaker;
    private NumberGuard numberGuard;
    private InjectionGuard injectionGuard;
    private ScopeGuard scopeGuard;
    private RoleGuard roleGuard;
    private OutputHygieneGuard outputHygieneGuard;
    private NoSecretsGuard noSecretsGuard;
    private Clock fixedClock;
    private ObjectMapper objectMapper;

    private AgentRuntime runtime;

    private final Instant baseTime = Instant.parse("2026-11-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        scriptedModel = new ScriptedChatModel();
        router = mock(SupervisorRouter.class);
        toolRegistry = mock(ToolRegistry.class);
        memoryService = mock(AgentMemoryService.class);
        usageService = mock(AgentUsageService.class);
        fixedClock = Clock.fixed(baseTime, ZoneOffset.UTC);
        circuitBreaker = new AgentCircuitBreaker(fixedClock);

        numberGuard = new NumberGuard();
        injectionGuard = new InjectionGuard();
        scopeGuard = new ScopeGuard();
        roleGuard = new RoleGuard();
        outputHygieneGuard = new OutputHygieneGuard();
        noSecretsGuard = new NoSecretsGuard();
        objectMapper = new ObjectMapper();

        runtime = new AgentRuntime(
                scriptedModel,
                router,
                toolRegistry,
                memoryService,
                usageService,
                circuitBreaker,
                numberGuard,
                injectionGuard,
                scopeGuard,
                roleGuard,
                outputHygieneGuard,
                noSecretsGuard,
                fixedClock,
                objectMapper
        );

        // Standard conversation mock
        AgentConversation conv = new AgentConversation();
        conv.setId(10L);
        conv.setUserId(42L);
        when(memoryService.getOrCreateConversation(any(), eq(42L), any())).thenReturn(conv);
        when(memoryService.getRecentContext(10L)).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("Loop Termination: Turn terminates at maximum 6 model calls")
    void testLoopTerminatesAtMax6ModelCalls() {
        BookingAgent agent = mock(BookingAgent.class);
        when(agent.name()).thenReturn("BookingAgent");
        when(agent.systemPrompt()).thenReturn("Booking agent prompt");
        when(agent.allowedTools()).thenReturn(Set.of("search_sessions"));
        when(agent.temperature()).thenReturn(0.2);
        when(agent.maxOutputTokens()).thenReturn(800);

        when(router.route(eq(42L), any(), any(), eq("book me"), any()))
                .thenReturn(new SupervisorRouter.RouteResult(agent, null));

        when(toolRegistry.execute(eq("BookingAgent"), any(), eq("search_sessions"), any(), any()))
                .thenReturn(ToolResult.ok(Map.of("found", 1), "Found 1 session"));

        // Queue 10 tool call responses in a row (would loop indefinitely without max 6 limit)
        for (int i = 0; i < 10; i++) {
            scriptedModel.queueToolCall("call_" + i, "search_sessions", "{\"category\":\"YOGA\"}");
        }

        AgentRuntime.AgentResponse response = runtime.run(
                new AgentRuntime.AgentRequest(42L, Set.of("MEMBER"), Set.of(), 10L, "book me")
        );

        assertNotNull(response);
        assertEquals(AgentRuntime.MAX_MODEL_CALLS, scriptedModel.getRecordedRequests().size());
    }

    @Test
    @DisplayName("Tool Limit: At most 8 tool calls per turn are executed")
    void testMax8ToolCallsPerTurn() {
        BookingAgent agent = mock(BookingAgent.class);
        when(agent.name()).thenReturn("BookingAgent");
        when(agent.systemPrompt()).thenReturn("Booking prompt");
        when(agent.allowedTools()).thenReturn(Set.of("search_sessions"));
        when(agent.temperature()).thenReturn(0.2);
        when(agent.maxOutputTokens()).thenReturn(800);

        when(router.route(eq(42L), any(), any(), eq("find sessions"), any()))
                .thenReturn(new SupervisorRouter.RouteResult(agent, null));

        AtomicInteger executedCount = new AtomicInteger(0);
        when(toolRegistry.execute(eq("BookingAgent"), any(), eq("search_sessions"), any(), any()))
                .thenAnswer(inv -> {
                    executedCount.incrementAndGet();
                    return ToolResult.ok(Map.of("ok", true), "ok");
                });

        // Step 1: Model returns 5 tool calls
        List<ToolCall> batch1 = List.of(
                new ToolCall("1", "search_sessions", "{}"),
                new ToolCall("2", "search_sessions", "{}"),
                new ToolCall("3", "search_sessions", "{}"),
                new ToolCall("4", "search_sessions", "{}"),
                new ToolCall("5", "search_sessions", "{}")
        );
        scriptedModel.queueResponse(new ChatResponse("", batch1, "TOOL_CALLS"));

        // Step 2: Model returns 5 more tool calls (total 10 requested)
        List<ToolCall> batch2 = List.of(
                new ToolCall("6", "search_sessions", "{}"),
                new ToolCall("7", "search_sessions", "{}"),
                new ToolCall("8", "search_sessions", "{}"),
                new ToolCall("9", "search_sessions", "{}"),
                new ToolCall("10", "search_sessions", "{}")
        );
        scriptedModel.queueResponse(new ChatResponse("", batch2, "TOOL_CALLS"));

        // Step 3: Final answer
        scriptedModel.queueResponse("Here are your sessions.");

        AgentRuntime.AgentResponse response = runtime.run(
                new AgentRuntime.AgentRequest(42L, Set.of("MEMBER"), Set.of(), 10L, "find sessions")
        );

        assertNotNull(response);
        assertEquals(8, executedCount.get(), "Expected exactly 8 tool executions (capped at max 8)");
    }

    @Test
    @DisplayName("Circuit Breaker: 5 consecutive failures opens circuit breaker for 60s and provides fallback")
    void testCircuitBreakerOpensAndRecovers() {
        assertEquals(0, circuitBreaker.getConsecutiveFailures());
        assertFalse(circuitBreaker.isOpen());

        for (int i = 0; i < 4; i++) {
            circuitBreaker.recordFailure();
            assertFalse(circuitBreaker.isOpen());
        }

        // 5th failure trips the breaker
        circuitBreaker.recordFailure();
        assertTrue(circuitBreaker.isOpen());

        // Fast rejection when circuit is open
        AgentRuntime.AgentResponse response = runtime.run(
                new AgentRuntime.AgentRequest(42L, Set.of("MEMBER"), Set.of(), 10L, "book session")
        );
        assertTrue(response.circuitBreakerTripped());
        assertEquals(AgentCircuitBreaker.FALLBACK_MESSAGE, response.reply());

        // Recovery: after 60s
        Clock futureClock = Clock.fixed(baseTime.plusSeconds(61), ZoneOffset.UTC);
        AgentCircuitBreaker recoveredBreaker = new AgentCircuitBreaker(futureClock);
        assertFalse(recoveredBreaker.isOpen());
    }

    @Test
    @DisplayName("Client Construction: GeminiLlmClient mock mode when key is absent")
    void testGeminiLlmClientMockMode() {
        LlmKeyProvider keyProvider = mock(LlmKeyProvider.class);
        when(keyProvider.currentKey()).thenReturn(Optional.empty());

        AppSettingRepository appSettingRepo = mock(AppSettingRepository.class);
        when(appSettingRepo.findById("ai_model")).thenReturn(Optional.empty());

        GeminiLlmClient client = new GeminiLlmClient(keyProvider, appSettingRepo, objectMapper, "gemini-1.5-flash");

        assertEquals("mock", client.getMode());
        assertFalse(client.isConfigured());
        assertEquals(GeminiLlmClient.MOCK_MESSAGE, client.generateResponse("Hello"));
    }

    @Test
    @DisplayName("Client Construction: Dynamic key change rebuilds client and changes hash")
    void testDynamicKeyChangeRebuildsClient() {
        LlmKeyProvider keyProvider = mock(LlmKeyProvider.class);
        java.util.concurrent.atomic.AtomicReference<String> keyRef =
                new java.util.concurrent.atomic.AtomicReference<>("key-1111111111");
        when(keyProvider.currentKey()).thenAnswer(inv -> Optional.of(keyRef.get()));

        AppSettingRepository appSettingRepo = mock(AppSettingRepository.class);
        when(appSettingRepo.findById("ai_model")).thenReturn(Optional.of(AppSetting.builder().name("ai_model").value("gemini-1.5-pro").build()));

        AtomicInteger constructions = new AtomicInteger(0);
        ChatModel mockModel = mock(ChatModel.class);
        dev.langchain4j.model.chat.response.ChatResponse mockLcResp = dev.langchain4j.model.chat.response.ChatResponse.builder()
                .aiMessage(dev.langchain4j.data.message.AiMessage.from("Hello back"))
                .build();
        when(mockModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class))).thenReturn(mockLcResp);

        ChatModelFactory factory = (k, m) -> {
            constructions.incrementAndGet();
            return mockModel;
        };

        GeminiLlmClient client = new GeminiLlmClient(keyProvider, appSettingRepo, objectMapper, "gemini-1.5-flash", factory);

        assertEquals("live", client.getMode());
        assertEquals("gemini-1.5-pro", client.getCurrentModelName());

        // Request 1 uses key-1
        client.chat(new ChatRequest(List.of(com.fitflow.agent.port.ChatMessage.user("Hi")), List.of(), 0.2, 800));
        assertEquals(1, constructions.get());

        // Change key
        keyRef.set("key-2222222222");

        // Request 2 uses key-2 -> rebuilds client
        client.chat(new ChatRequest(List.of(com.fitflow.agent.port.ChatMessage.user("Hi")), List.of(), 0.2, 800));
        assertEquals(2, constructions.get());
    }

    @Test
    @DisplayName("Client Construction: AppSetting ai_model change is dynamically picked up")
    void testModelNameChangeDynamicallyPickedUp() {
        LlmKeyProvider keyProvider = mock(LlmKeyProvider.class);
        when(keyProvider.currentKey()).thenReturn(Optional.of("fixed-test-key-12345"));

        AppSettingRepository appSettingRepo = mock(AppSettingRepository.class);
        when(appSettingRepo.findById("ai_model"))
                .thenReturn(Optional.of(AppSetting.builder().name("ai_model").value("gemini-1.5-flash").build()))
                .thenReturn(Optional.of(AppSetting.builder().name("ai_model").value("gemini-1.5-pro").build()));

        AtomicInteger constructions = new AtomicInteger(0);
        ChatModel mockModel = mock(ChatModel.class);
        dev.langchain4j.model.chat.response.ChatResponse mockLcResp = dev.langchain4j.model.chat.response.ChatResponse.builder()
                .aiMessage(dev.langchain4j.data.message.AiMessage.from("Hello back"))
                .build();
        when(mockModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class))).thenReturn(mockLcResp);

        ChatModelFactory factory = (k, m) -> {
            constructions.incrementAndGet();
            return mockModel;
        };

        GeminiLlmClient client = new GeminiLlmClient(keyProvider, appSettingRepo, objectMapper, "gemini-1.5-flash", factory);

        client.chat(new ChatRequest(List.of(com.fitflow.agent.port.ChatMessage.user("Hi")), List.of(), 0.2, 800));
        assertEquals(1, constructions.get());

        // Second turn sees new model name -> triggers rebuild
        client.chat(new ChatRequest(List.of(com.fitflow.agent.port.ChatMessage.user("Hi")), List.of(), 0.2, 800));
        assertEquals(2, constructions.get());
    }
}
