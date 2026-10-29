package com.fitflow.agent.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.agents.SpecialistAgent;
import com.fitflow.agent.entity.AgentConversation;
import com.fitflow.agent.guard.InjectionGuard;
import com.fitflow.agent.guard.NoSecretsGuard;
import com.fitflow.agent.guard.NumberGuard;
import com.fitflow.agent.guard.OutputHygieneGuard;
import com.fitflow.agent.guard.RoleGuard;
import com.fitflow.agent.guard.ScopeGuard;
import com.fitflow.agent.memory.AgentMemoryService;
import com.fitflow.agent.port.ChatMessage;
import com.fitflow.agent.port.ChatModelPort;
import com.fitflow.agent.port.ChatRequest;
import com.fitflow.agent.port.ChatResponse;
import com.fitflow.agent.port.ToolCall;
import com.fitflow.agent.port.ToolDefinition;
import com.fitflow.agent.tools.ToolContext;
import com.fitflow.agent.tools.ToolRegistry;
import com.fitflow.agent.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRuntime {

    public static final int MAX_MODEL_CALLS = 6;
    public static final int MAX_TOOL_CALLS = 8;
    public static final long CALL_TIMEOUT_SECONDS = 30;
    public static final long TURN_TIMEOUT_SECONDS = 60;

    private final ChatModelPort chatModel;
    private final SupervisorRouter router;
    private final ToolRegistry toolRegistry;
    private final AgentMemoryService memoryService;
    private final AgentUsageService usageService;
    private final AgentCircuitBreaker circuitBreaker;
    private final NumberGuard numberGuard;
    private final InjectionGuard injectionGuard;
    private final ScopeGuard scopeGuard;
    private final RoleGuard roleGuard;
    private final OutputHygieneGuard outputHygieneGuard;
    private final NoSecretsGuard noSecretsGuard;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public record AgentRequest(
            Long userId,
            Set<String> roles,
            Set<String> permissions,
            Long conversationId,
            String message
    ) {}

    public record ToolCallStatusDto(String name, String status) {}
    public record PendingActionProposalDto(Long id, String summary, String expiresAt) {}
    public record GuardStatusDto(boolean replacedByFallback) {}

    public record AgentResponse(
            Long conversationId,
            String reply,
            String agentName,
            boolean circuitBreakerTripped,
            List<ToolCallStatusDto> toolCalls,
            List<PendingActionProposalDto> pendingActions,
            GuardStatusDto guard
    ) {
        public AgentResponse(Long conversationId, String reply, String agentName, boolean circuitBreakerTripped) {
            this(conversationId, reply, agentName, circuitBreakerTripped, List.of(), List.of(), new GuardStatusDto(false));
        }
    }

    public AgentResponse run(AgentRequest request) {
        long turnStartMillis = clock.millis();

        // 1. Usage budget and rate limit check
        usageService.checkAndIncrementUsage(request.userId());

        // 2. Circuit breaker check
        if (circuitBreaker.isOpen()) {
            return new AgentResponse(request.conversationId(), AgentCircuitBreaker.FALLBACK_MESSAGE, "NONE", true);
        }

        // 3. Scope guard check
        if (scopeGuard.isOutOfScope(request.message())) {
            return new AgentResponse(request.conversationId(), scopeGuard.getRefusal(), "NONE", false);
        }

        // 4. Role guard check
        if (roleGuard.isForbiddenAdminRequest(request.message())) {
            return new AgentResponse(request.conversationId(), roleGuard.getRefusal(), "NONE", false);
        }

        // 5. Supervisor router
        SupervisorRouter.RouteResult route = router.route(
                request.userId(),
                request.roles(),
                request.permissions(),
                request.message(),
                chatModel
        );

        if (!route.hasAgent()) {
            return new AgentResponse(request.conversationId(), route.clarificationMessage(), "SUPERVISOR", false);
        }

        SpecialistAgent agent = route.agent();

        // 6. Setup or get conversation
        AgentConversation conversation = memoryService.getOrCreateConversation(
                request.conversationId(),
                request.userId(),
                request.roles().contains("MEMBER") ? "MEMBER" : "USER"
        );
        Long convId = conversation.getId();

        // Tool context
        ToolContext toolContext = new ToolContext(
                request.userId(),
                request.roles(),
                request.permissions(),
                convId,
                clock
        );

        // 7. Load context messages (up to 12) + system prompt + new user message
        List<ChatMessage> turnMessages = new ArrayList<>();
        turnMessages.add(ChatMessage.system(agent.systemPrompt()));
        turnMessages.addAll(memoryService.getRecentContext(convId));
        turnMessages.add(ChatMessage.user(request.message()));

        List<ToolDefinition> toolDefs = toolRegistry.getDefinitionsForTools(agent.allowedTools());

        int modelCalls = 0;
        int toolCallsCount = 0;
        List<String> turnToolOutputs = new ArrayList<>();
        List<String> turnToolSummaries = new ArrayList<>();
        Set<String> avoidedSessionIds = new HashSet<>();
        List<ToolCallStatusDto> turnToolCalls = new ArrayList<>();
        List<PendingActionProposalDto> turnPendingActions = new ArrayList<>();
        boolean guardReplaced = false;

        String finalReply = null;

        // Tool-calling loop
        while (modelCalls < MAX_MODEL_CALLS) {
            // Check turn timeout
            if (clock.millis() - turnStartMillis > TURN_TIMEOUT_SECONDS * 1000L) {
                log.warn("Turn timeout ({}s) exceeded for user id={}", TURN_TIMEOUT_SECONDS, request.userId());
                finalReply = "The request took too long to complete. Please try again.";
                break;
            }

            modelCalls++;
            ChatRequest chatReq = new ChatRequest(
                    turnMessages,
                    toolDefs,
                    agent.temperature(),
                    agent.maxOutputTokens()
            );

            ChatResponse modelResp;
            try {
                modelResp = executeModelCallWithTimeout(chatReq, CALL_TIMEOUT_SECONDS);
                circuitBreaker.recordSuccess();
            } catch (Exception e) {
                log.error("Model call failed in turn (attempt {}): {}", modelCalls, e.getMessage());
                circuitBreaker.recordFailure();
                if (circuitBreaker.isOpen()) {
                    return new AgentResponse(convId, AgentCircuitBreaker.FALLBACK_MESSAGE, agent.name(), true);
                }
                finalReply = "I encountered an error contacting the assistant service. Please try again.";
                break;
            }

            if (modelResp.hasToolCalls()) {
                // Add assistant tool calls message to context
                turnMessages.add(ChatMessage.assistant(modelResp.content(), modelResp.toolCalls()));

                for (ToolCall tc : modelResp.toolCalls()) {
                    if (toolCallsCount >= MAX_TOOL_CALLS) {
                        log.warn("Maximum tool calls limit ({}) reached in turn", MAX_TOOL_CALLS);
                        break;
                    }

                    toolCallsCount++;
                    Map<String, Object> rawArgs = parseArgs(tc.argumentsJson());

                    ToolResult result = toolRegistry.execute(
                            agent.name(),
                            agent.allowedTools(),
                            tc.name(),
                            toolContext,
                            rawArgs
                    );

                    String resultString = formatToolResult(result);
                    turnToolOutputs.add(resultString);
                    if (result.summary() != null) {
                        turnToolSummaries.add(result.summary());
                    }

                    turnToolCalls.add(new ToolCallStatusDto(tc.name(), result.success() ? "OK" : "ERROR"));
                    if (result.success() && result.data() != null && result.data().containsKey("actionId")) {
                        try {
                            Long actId = ((Number) result.data().get("actionId")).longValue();
                            String sum = (String) result.data().get("summary");
                            String exp = result.data().get("expiresAt") != null ? result.data().get("expiresAt").toString() : "";
                            turnPendingActions.add(new PendingActionProposalDto(actId, sum, exp));
                        } catch (Exception ignored) {}
                    }

                    // Track avoided sessions for SafeClassMatcherAgent post-check
                    if ("get_safe_classes".equals(tc.name()) && result.data() != null) {
                        extractAvoidedSessionIds(result.data(), avoidedSessionIds);
                    }

                    // Wrap untrusted text via G2
                    String wrappedContent = injectionGuard.wrapUntrusted(resultString);
                    turnMessages.add(ChatMessage.tool(tc.id(), wrappedContent));
                }
            } else {
                // Final answer produced
                String candidate = modelResp.content() != null ? modelResp.content() : "";

                // G1 NumberGuard check
                NumberGuard.GuardResult numResult = numberGuard.verifyReply(
                        candidate, request.message(), turnToolOutputs, turnToolSummaries);

                if (!numResult.verified()) {
                    log.warn("NumberGuard failed for unverified number: {}. Retrying once.", numResult.unverifiedNumber());
                    turnMessages.add(ChatMessage.assistant(candidate));
                    turnMessages.add(ChatMessage.system("Only use numbers present in tool results."));

                    try {
                        ChatResponse retryResp = executeModelCallWithTimeout(
                                new ChatRequest(turnMessages, toolDefs, agent.temperature(), agent.maxOutputTokens()),
                                CALL_TIMEOUT_SECONDS
                        );
                        String retryCandidate = retryResp.content() != null ? retryResp.content() : "";
                        NumberGuard.GuardResult retryCheck = numberGuard.verifyReply(
                                retryCandidate, request.message(), turnToolOutputs, turnToolSummaries);

                        if (!retryCheck.verified()) {
                            candidate = retryCheck.fallbackText();
                            guardReplaced = true;
                        } else {
                            candidate = retryCandidate;
                        }
                    } catch (Exception ex) {
                        candidate = numResult.fallbackText();
                        guardReplaced = true;
                    }
                }

                // SafeClassMatcherAgent avoid list post-check
                if ("SafeClassMatcherAgent".equals(agent.name()) && !avoidedSessionIds.isEmpty()) {
                    for (String avoidedId : avoidedSessionIds) {
                        java.util.regex.Pattern recPattern = java.util.regex.Pattern.compile(
                                "(?i)\\b(recommend|try|attend|join|take|book)\\b[^.!?]*\\b" + avoidedId + "\\b"
                        );
                        if (recPattern.matcher(candidate).find()) {
                            log.warn("SafeClassMatcherAgent recommended avoided session id {}. Replacing with safe fallback.", avoidedId);
                            candidate = "Here are the safe sessions identified for you:\n" + String.join("\n", turnToolSummaries);
                            break;
                        }
                    }
                }

                finalReply = candidate;
                break;
            }
        }

        if (finalReply == null) {
            finalReply = "I have reviewed your request, but could not produce a response within the step limit.";
        }

        // G6 Output hygiene
        String sanitizedReply = outputHygieneGuard.sanitize(finalReply);

        // Store messages in memory
        memoryService.recordUserMessage(convId, request.message());
        memoryService.recordAssistantMessage(convId, sanitizedReply);

        return new AgentResponse(convId, sanitizedReply, agent.name(), false, turnToolCalls, turnPendingActions, new GuardStatusDto(guardReplaced));
    }

    private ChatResponse executeModelCallWithTimeout(ChatRequest req, long timeoutSeconds) throws Exception {
        CompletableFuture<ChatResponse> future = CompletableFuture.supplyAsync(() -> chatModel.chat(req));
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            future.cancel(true);
            throw new RuntimeException("Model call timed out after " + timeoutSeconds + "s");
        }
    }

    private Map<String, Object> parseArgs(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String formatToolResult(ToolResult result) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "success", result.success(),
                    "summary", result.summary() != null ? result.summary() : "",
                    "data", result.data() != null ? result.data() : Map.of(),
                    "errorCode", result.errorCode() != null ? result.errorCode() : ""
            ));
        } catch (Exception e) {
            return "{\"success\":" + result.success() + ",\"summary\":\"" + result.summary() + "\"}";
        }
    }

    @SuppressWarnings("unchecked")
    private void extractAvoidedSessionIds(Map<String, Object> data, Set<String> avoidedIds) {
        Object avoidObj = data.get("avoid");
        if (avoidObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Object sId = m.get("sessionId");
                    if (sId != null) {
                        avoidedIds.add(sId.toString());
                    }
                }
            }
        }
    }
}
