# FitFlow Stage 8 Evidence Report: Safe Multi-Agent Core

## 1. Framework Decision Gate (Step 0)

### Candidate Comparison: LangChain4j vs. Google Agent Development Kit (ADK) for Java

| Evaluation Criterion | Candidate (a): LangChain4j (`langchain4j-google-ai-gemini`) | Candidate (b): Google ADK for Java (`com.google.adk`) |
| :--- | :--- | :--- |
| **1. Released Maven artifact & stable version?** | **YES**: `dev.langchain4j:langchain4j:1.22.0` and `dev.langchain4j:langchain4j-google-ai-gemini:1.22.0` released on Maven Central ([Maven Central Search](https://central.sonatype.com/artifact/dev.langchain4j/langchain4j/1.22.0)). | **NO**: Google ADK for Java does not have an active generally-available stable release on Maven Central (ADK is primarily Python/TypeScript focused; Java modules are experimental snapshots). |
| **2. Gemini via API key with function/tool calling?** | **YES**: `GoogleAiGeminiChatModel` supports tool definitions, arguments parsing, and function calling via Gemini API key. | **NO**: Incomplete stable tool-calling support in Java ADK. |
| **3. Can inject a fake chat model for tests (no network)?** | **YES**: Thin `ChatModelPort` interface with `ScriptedChatModel` allows fully offline deterministic queueing of responses and tool calls. | **NO**: Harder to mock without heavyweight network shims. |
| **4. Specialist agents with isolated system prompts, tool set, and memory?** | **YES**: Each agent defines its own `systemPrompt`, fixed `allowedTools` list, `temperature`, and `maxOutputTokens`. | **YES / PARTIAL** |
| **5. Supply custom conversation memory store (PostgreSQL)?** | **YES**: Custom `AgentMemoryService` backing onto PostgreSQL entities `AgentConversation` and `AgentMessage`. | **NO**: Limited native pluggable memory store adapters. |

### Decision & Rationale
We selected **LangChain4j 1.22.0** (`dev.langchain4j:langchain4j` and `dev.langchain4j:langchain4j-google-ai-gemini`). All LLM interactions are isolated behind thin port interfaces (`ChatModelPort`, `AgentRuntime`, `ScriptedChatModel`) so the underlying framework or client can be swapped without impacting business logic.

As specified by the architecture design rule: our multi-agent supervisor is deterministic Java (`SupervisorRouter`), and each specialist agent uses deterministic tool dispatching with human-in-the-loop proposals for mutative actions.

---

## 2. Architectural Summary & System Contract

1. **Deterministic Supervisor (`SupervisorRouter`)**:
   - Actor roles dictate candidate specialist agents: `MEMBER` is strictly restricted to `BookingAgent`, `MembershipAgent`, `IntakeAgent`, and `SafeClassMatcherAgent`.
   - Keyword/pattern matching (`resources/agents/intents.yml`) deterministically identifies user intent. If ambiguous, one cheap single-token LLM classification call is invoked, strictly validated against allowed enums.
   - Users cannot name an agent or tool to bypass role boundaries (G4 RoleGuard).

2. **Specialist Agents**:
   - `BookingAgent`: Class search, booking lookup, and proposing bookings/cancellations.
   - `MembershipAgent`: Plan catalog queries, subscription and upgrade proposals, and membership cancellations.
   - `IntakeAgent`: Questionnaire guidance, consent-aware health profile drafting, and profile save proposals.
   - `SafeClassMatcherAgent`: Deterministic safety filtering against member injuries/conditions (`get_safe_classes`), post-check verification preventing recommendations of avoided classes.

3. **Tool Catalog (12 Tools in `com.fitflow.agent.tools`)**:
   - `search_sessions`, `get_class_details`, `get_my_bookings`, `propose_booking`, `propose_cancel_booking`
   - `list_plans`, `get_membership_status`, `preview_membership_change`, `propose_membership_change`
   - `get_llm_safe_profile`, `propose_profile_save`
   - `get_safe_classes`
   - **Zero Caller ID Rule (ArchUnit Rule K)**: Zero tool arguments accept caller identity (`userId`, `memberId`, etc.). Caller identity is strictly populated from the authenticated `ToolContext`.
   - **Repository Isolation (ArchUnit Rule L)**: Tools never depend on JPA repositories or entities; all data access traverses domain services (`BookingService`, `MembershipService`, `WellnessProfileService`, etc.).

4. **Human-in-the-Loop Actions (`PendingActionService`)**:
   - Mutative actions create a `PendingAction` in PostgreSQL with status `PENDING` (10-minute expiry).
   - Agents explain the action in human-readable terms and request explicit on-screen member confirmation.
   - `POST /api/v1/agent/actions/{id}/confirm` enforces IDOR check (caller must own the action), re-checks current user roles and permissions, executes with idempotency key `agent-action-{id}`, and transitions status to `CONFIRMED`. Subsequent confirms return stored `result_json` without re-executing.
   - `POST /api/v1/agent/actions/{id}/reject` transitions status to `REJECTED`.

5. **7 Guardrails**:
   - **G1 NumberGuard**: Candidate numbers must exist in user query, tool outputs, or whitelist; unverified numbers trigger a retry, then deterministic summary fallback.
   - **G2 InjectionGuard**: Untrusted inputs wrapped in `[UNTRUSTED DATA]...[/UNTRUSTED DATA]`, stripped of control characters, and truncated to 300 characters.
   - **G3 ScopeGuard**: Regex barriers block medical diagnosis, legal advice, and third-party data requests.
   - **G4 RoleGuard**: Enforces authenticated caller roles before agent dispatch and tool execution.
   - **G5 HealthEgressGuard**: Forbids health data in LLM prompts; enforces zero health canary leakage (`CANARY-9f3a`).
   - **G6 OutputHygieneGuard**: Enforces disclaimer presence: *"Wellness suggestions, not medical advice. Rules are illustrative and must be reviewed by a qualified professional before real use."*
   - **G7 NoSecretsGuard**: Blocks API keys, passwords, and cryptographic secrets from prompts and tool execution.

6. **Safety & Operational Controls**:
   - `AgentCircuitBreaker`: 5 consecutive Gemini failures open the circuit for 60 seconds with friendly fallback: *"The AI assistant is temporarily unavailable. You can still use the app screens."*
   - `AgentUsageService`: Sliding 60-second window rate limit (60 requests/min) and daily budget (200 calls/day in PostgreSQL `agent_usage`).
   - `AgentAuditService`: Free text hashed with SHA-256; health fields redacted (`[REDACTED_HEALTH_DATA]`); IDs and enums preserved in PostgreSQL `agent_action_audit`.

---

## 3. Mandatory Sanity Breaks

Four intentional mutations were introduced into the codebase to verify that safety and security gates fail predictably and recover completely when restored.

### Break 1: Role Check in `ToolRegistry.execute`
- **Component**: `backend/src/main/java/com/fitflow/agent/tools/ToolRegistry.java`
- **Break Mechanism**: Temporarily commented out the `tool.requiredRoles()` check in `ToolRegistry.execute`.
- **Failure Output**:
  ```text
  [INFO] Running com.fitflow.agent.ToolRegistryTest
  [ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 1.696 s <<< FAILURE! -- in com.fitflow.agent.ToolRegistryTest
  [ERROR] com.fitflow.agent.ToolRegistryTest.callerLackingRequiredRoleRejected -- Time elapsed: 1.646 s <<< FAILURE!
  org.opentest4j.AssertionFailedError: expected: <FORBIDDEN_TOOL> but was: <EXECUTION_ERROR>
      at org.junit.jupiter.api.AssertionFailureBuilder.build(AssertionFailureBuilder.java:151)
      at org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:182)
      at com.fitflow.agent.ToolRegistryTest.callerLackingRequiredRoleRejected(ToolRegistryTest.java:94)
  ```
- **Resolution**: Restored role authorization gate in `ToolRegistry`.
- **Pass Verification**: `ToolRegistryTest` passed (5/5 tests passing).

---

### Break 2: IDOR Ownership Gate in `PendingActionService.confirm`
- **Component**: `backend/src/main/java/com/fitflow/agent/service/PendingActionService.java`
- **Break Mechanism**: Temporarily commented out `if (!action.getUserId().equals(userId))` ownership validation.
- **Failure Output**:
  ```text
  [INFO] Running com.fitflow.agent.PendingActionServiceTest
  [ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 3.060 s <<< FAILURE! -- in com.fitflow.agent.PendingActionServiceTest
  [ERROR] com.fitflow.agent.PendingActionServiceTest.testAnotherUsersActionThrowsNotFound -- Time elapsed: 3.018 s <<< FAILURE!
  org.opentest4j.AssertionFailedError: expected: <404> but was: <403>
      at org.junit.jupiter.api.AssertionFailureBuilder.build(AssertionFailureBuilder.java:151)
      at org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:150)
      at com.fitflow.agent.PendingActionServiceTest.testAnotherUsersActionThrowsNotFound(PendingActionServiceTest.java:206)
  ```
- **Resolution**: Restored IDOR ownership check in `PendingActionService.confirm`.
- **Pass Verification**: `PendingActionServiceTest` passed (7/7 tests passing).

---

### Break 3: NumberGuard Verification Gate
- **Component**: `backend/src/main/java/com/fitflow/agent/guard/NumberGuard.java`
- **Break Mechanism**: Temporarily bypassed `allowedNumbers.contains(num)` loop to return `GuardResult(true, null, null)`.
- **Failure Output**:
  ```text
  [INFO] Running com.fitflow.agent.GuardrailsTest
  [ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.080 s <<< FAILURE! -- in com.fitflow.agent.GuardrailsTest
  [ERROR] com.fitflow.agent.GuardrailsTest.testNumberGuardCatchesInventedNumber -- Time elapsed: 0.041 s <<< FAILURE!
  org.opentest4j.AssertionFailedError: expected: <false> but was: <true>
      at org.junit.jupiter.api.AssertionFailureBuilder.build(AssertionFailureBuilder.java:151)
      at org.junit.jupiter.api.AssertFalse.failNotFalse(AssertFalse.java:63)
      at com.fitflow.agent.GuardrailsTest.testNumberGuardCatchesInventedNumber(GuardrailsTest.java:46)
  ```
- **Resolution**: Restored number extraction and whitelist verification loop in `NumberGuard`.
- **Pass Verification**: `GuardrailsTest` passed (23/23 tests passing).

---

### Break 4: Untrusted Block Wrapping in `InjectionGuard`
- **Component**: `backend/src/main/java/com/fitflow/agent/guard/InjectionGuard.java`
- **Break Mechanism**: Temporarily omitted header/footer encapsulation, returning raw sanitized string.
- **Failure Output**:
  ```text
  [INFO] Running com.fitflow.agent.GuardrailsTest
  [ERROR] Tests run: 5, Failures: 5, Errors: 0, Skipped: 0, Time elapsed: 0.137 s <<< FAILURE! -- in com.fitflow.agent.GuardrailsTest
  [ERROR] com.fitflow.agent.GuardrailsTest.testInjectionGuardWrapping(String)[1] -- Time elapsed: 0.045 s <<< FAILURE!
  org.opentest4j.AssertionFailedError: expected: <true> but was: <false>
      at org.junit.jupiter.api.AssertionFailureBuilder.build(AssertionFailureBuilder.java:151)
      at org.junit.jupiter.api.AssertTrue.failNotTrue(AssertTrue.java:63)
      at com.fitflow.agent.GuardrailsTest.testInjectionGuardWrapping(GuardrailsTest.java:83)
  ```
- **Resolution**: Restored `[UNTRUSTED DATA]...[/UNTRUSTED DATA]` wrapping in `InjectionGuard`.
- **Pass Verification**: `GuardrailsTest` passed (23/23 tests passing).

---

## 4. Multi-Turn End-to-End Walkthrough Execution Log

Execution of `Stage8WalkthroughIT` covers:
1. Multi-turn booking search and proposal creation (`BookingAgent`).
2. On-screen pending action confirmation with idempotency key (`PendingActionService`).
3. Replay of confirmation verifying cached result return without duplicate booking.
4. Membership plan preview and pricing inquiry (`MembershipAgent`).
5. Prompt injection defense wrapping untrusted user inputs (`InjectionGuard`).
6. Zero health canary leakage across all prompts recorded by `ScriptedChatModel` (`HealthEgressGuard`).
7. PostgreSQL audit logging with redacted arguments and no leaked credentials (`AgentAuditService`).

```text
[INFO] Running com.fitflow.agent.Stage8WalkthroughIT
2026-10-29 01:34:34.829 [main] INFO c.fitflow.agent.Stage8WalkthroughIT - Starting Stage8WalkthroughIT using Java 17.0.12 with PID 10936
2026-10-29 01:34:48.181 [main] INFO c.fitflow.agent.Stage8WalkthroughIT - Started Stage8WalkthroughIT in 14.242 seconds
2026-10-29 01:34:50.216 [main] INFO c.f.m.RealEntitlementService - Member id=111783 consumed session from FREE membership id=7414 (1/3 used)
2026-10-29 01:34:50.249 [main] INFO c.fitflow.notification.OutboxService - Appended outbox event id=2224 type='booking.created' aggregateType='booking' aggregateId=100226
2026-10-29 01:34:50.257 [main] INFO com.fitflow.booking.BookingService - Confirmed booking id=100226 created for memberId=111783 in sessionId=100423
2026-10-29 01:34:50.281 [main] INFO c.f.a.service.PendingActionService - PendingAction id=6 already CONFIRMED. Returning cached result.
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 17.33 s -- in com.fitflow.agent.Stage8WalkthroughIT
[INFO] BUILD SUCCESS
```

---

## 5. Architectural Rule Validation (ArchUnit Rules K & L)

```text
[INFO] Running com.fitflow.ArchitectureRulesTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.115 s -- in com.fitflow.ArchitectureRulesTest
[INFO] BUILD SUCCESS
```
- **Rule K (Agent Tools Zero Caller ID)**: All tool methods under `com.fitflow.agent.tools` accept zero caller ID parameters (`userId`, `memberId`, `actorId`, `callerId`) in tool arguments or DTOs.
- **Rule L (Agent Package Repository Boundary)**: Classes in `com.fitflow.agent.tools`, `agents`, `core`, `guard`, `controller` never directly access domain repositories (`com.fitflow.*.repository`), preserving strict domain service boundaries.

---

## 6. Endpoint Security Coverage

`EndpointSecurityCoverageTest` passes with 100% of newly exposed Stage 8 endpoints protected by `@PreAuthorize`:
- `POST /api/v1/agent/chat` -> `@PreAuthorize("hasRole('MEMBER')")`
- `POST /api/v1/agent/actions/{id}/confirm` -> `@PreAuthorize("hasRole('MEMBER')")`
- `POST /api/v1/agent/actions/{id}/reject` -> `@PreAuthorize("hasRole('MEMBER')")`
- `GET /api/v1/agent/status` -> `@PreAuthorize("isAuthenticated()")`
