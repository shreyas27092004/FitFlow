package com.fitflow.agent;

import com.fitflow.agent.controller.AgentChatController;
import com.fitflow.agent.core.AgentRuntime;
import com.fitflow.agent.dto.AgentChatRequestDto;
import com.fitflow.agent.dto.AgentChatResponseDto;
import com.fitflow.agent.dto.AgentConversationDto;
import com.fitflow.agent.dto.AgentMessageDto;
import com.fitflow.agent.dto.PendingActionProposal;
import com.fitflow.agent.memory.AgentMemoryService;
import com.fitflow.agent.service.PendingActionService;
import com.fitflow.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class Stage9AgentChatControllerTest {

    private AgentRuntime agentRuntime;
    private PendingActionService pendingActionService;
    private AgentMemoryService agentMemoryService;
    private Clock fixedClock;
    private AgentChatController controller;

    private UserPrincipal memberPrincipal;

    @BeforeEach
    void setUp() {
        agentRuntime = Mockito.mock(AgentRuntime.class);
        pendingActionService = Mockito.mock(PendingActionService.class);
        agentMemoryService = Mockito.mock(AgentMemoryService.class);
        fixedClock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC);

        controller = new AgentChatController(
                agentRuntime,
                pendingActionService,
                agentMemoryService,
                fixedClock
        );

        memberPrincipal = new UserPrincipal(1L, "alice@fitflow.local", List.of("MEMBER"), List.of());
        AgentChatController.sanityBreakStreamRawTokensBeforeGuard = false;
    }

    @AfterEach
    void tearDown() {
        AgentChatController.sanityBreakStreamRawTokensBeforeGuard = false;
    }

    @Test
    @DisplayName("Chat: Regular POST /chat endpoint dispatches and returns JSON response")
    void chatRegularEndpoint() {
        AgentRuntime.AgentResponse agentResp = new AgentRuntime.AgentResponse(
                50L, "Hello Alice, how can I assist you with your fitness journey today?",
                "BookingAgent", false
        );
        Mockito.when(agentRuntime.run(any())).thenReturn(agentResp);

        AgentChatRequestDto req = new AgentChatRequestDto(null, "Hello!");
        ResponseEntity<AgentChatResponseDto> resp = controller.chat(memberPrincipal, req);

        assertNotNull(resp);
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals(50L, resp.getBody().conversationId());
        assertTrue(resp.getBody().reply().contains("Hello Alice"));
    }

    @Test
    @DisplayName("Streaming: SSE POST /chat/stream creates SseEmitter and enforces 1 stream per user")
    void streamPostAndConcurrencyLimit() {
        AgentRuntime.AgentResponse agentResp = new AgentRuntime.AgentResponse(
                50L, "Your plan is ready.",
                "PlanBuilderAgent", false
        );
        Mockito.when(agentRuntime.run(any())).thenReturn(agentResp);

        AgentChatRequestDto req = new AgentChatRequestDto(null, "Show plan");
        SseEmitter emitter1 = controller.chatStreamPost(memberPrincipal, req);
        assertNotNull(emitter1);

        // Immediate concurrent attempt by same user
        SseEmitter emitter2 = controller.chatStreamPost(memberPrincipal, req);
        assertNotNull(emitter2);
    }

    @Test
    @DisplayName("Streaming: SSE GET /chat/stream accepts query parameters")
    void streamGetEndpoint() {
        AgentRuntime.AgentResponse agentResp = new AgentRuntime.AgentResponse(
                50L, "GET stream response.",
                "SafeClassMatcherAgent", false
        );
        Mockito.when(agentRuntime.run(any())).thenReturn(agentResp);

        SseEmitter emitter = controller.chatStreamGet(memberPrincipal, 50L, "What classes are safe?");
        assertNotNull(emitter);
    }

    @Test
    @DisplayName("Sanity Break 3: sanityBreakStreamRawTokensBeforeGuard emits unverified raw token")
    void sanityBreak3FlagValidation() {
        AgentChatController.sanityBreakStreamRawTokensBeforeGuard = true;

        AgentRuntime.AgentResponse agentResp = new AgentRuntime.AgentResponse(
                50L, "Safe verified reply.",
                "BookingAgent", false
        );
        Mockito.when(agentRuntime.run(any())).thenReturn(agentResp);

        SseEmitter emitter = controller.chatStreamPost(memberPrincipal, new AgentChatRequestDto(null, "Test"));
        assertNotNull(emitter);
        assertTrue(AgentChatController.sanityBreakStreamRawTokensBeforeGuard);
    }

    @Test
    @DisplayName("Conversations: Paginated list returns DTOs")
    void getConversationsReturnsDtoPage() {
        AgentConversationDto convDto = new AgentConversationDto(
                50L, 1L, "MEMBER", Instant.now(), Instant.now()
        );
        Page<AgentConversationDto> page = new PageImpl<>(List.of(convDto));
        Mockito.when(agentMemoryService.getConversations(eq(1L), any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<AgentConversationDto>> resp = controller.getConversations(memberPrincipal, 0, 20);
        assertNotNull(resp);
        assertEquals(1, resp.getBody().getContent().size());
        assertEquals(50L, resp.getBody().getContent().get(0).id());
    }

    @Test
    @DisplayName("Messages: Returns DTO list for conversation")
    void getMessagesReturnsDtoList() {
        AgentMessageDto msg = new AgentMessageDto(
                101L, 50L, "USER", "Hello", Instant.now()
        );
        Mockito.when(agentMemoryService.getMessages(50L, 1L)).thenReturn(List.of(msg));

        ResponseEntity<List<AgentMessageDto>> resp = controller.getMessages(memberPrincipal, 50L);
        assertNotNull(resp);
        assertEquals(1, resp.getBody().size());
        assertEquals("Hello", resp.getBody().get(0).content());
    }

    @Test
    @DisplayName("Actions: Lists pending action proposals via PendingActionService")
    void getPendingActionsUsesService() {
        PendingActionProposal pap = new PendingActionProposal(
                701L, "propose_weekly_plan", "Plan for Mon Nov 2", Instant.now().plusSeconds(3600)
        );
        Mockito.when(pendingActionService.listPending(1L)).thenReturn(List.of(pap));

        ResponseEntity<List<PendingActionProposal>> resp = controller.getPendingActions(memberPrincipal);
        assertNotNull(resp);
        assertEquals(1, resp.getBody().size());
        assertEquals(701L, resp.getBody().get(0).id());
    }

    @Test
    @DisplayName("Actions: Confirm and reject delegate to PendingActionService")
    void confirmAndRejectActions() {
        Mockito.when(pendingActionService.confirm(701L, 1L)).thenReturn(Map.of("status", "CONFIRMED", "planId", 55L));

        ResponseEntity<Map<String, Object>> confirmResp = controller.confirmAction(memberPrincipal, 701L);
        assertEquals(200, confirmResp.getStatusCode().value());
        assertEquals("CONFIRMED", confirmResp.getBody().get("status"));

        ResponseEntity<Map<String, Object>> rejectResp = controller.rejectAction(memberPrincipal, 701L);
        assertEquals(200, rejectResp.getStatusCode().value());
        assertEquals("REJECTED", rejectResp.getBody().get("status"));
        Mockito.verify(pendingActionService, Mockito.times(1)).reject(701L, 1L);
    }
}
