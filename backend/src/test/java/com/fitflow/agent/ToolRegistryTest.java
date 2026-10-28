package com.fitflow.agent;

import com.fitflow.agent.service.AgentAuditService;
import com.fitflow.agent.tools.SearchSessionsTool;
import com.fitflow.agent.tools.Tool;
import com.fitflow.agent.tools.ToolContext;
import com.fitflow.agent.tools.ToolRegistry;
import com.fitflow.agent.tools.ToolResult;
import com.fitflow.common.pagination.PageResponse;
import com.fitflow.session.ClassSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class ToolRegistryTest {

    private Tool mockTool;
    private AgentAuditService mockAuditService;
    private ToolRegistry registry;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        mockTool = Mockito.mock(Tool.class);
        Mockito.when(mockTool.name()).thenReturn("search_sessions");
        Mockito.when(mockTool.description()).thenReturn("Search scheduled sessions");
        Mockito.when(mockTool.requiredRoles()).thenReturn(Set.of("MEMBER"));
        Mockito.when(mockTool.requiredPermissions()).thenReturn(Set.of());
        Mockito.when(mockTool.isReadOnly()).thenReturn(true);

        mockAuditService = Mockito.mock(AgentAuditService.class);
        fixedClock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC);

        registry = new ToolRegistry(List.of(mockTool), mockAuditService);
    }

    @Test
    @DisplayName("Registry: Unknown tool name rejected and audited")
    void unknownToolRejectedAndAudited() {
        ToolContext ctx = new ToolContext(101L, Set.of("MEMBER"), Set.of(), 1L, fixedClock);
        ToolResult result = registry.execute("BookingAgent", Set.of("search_sessions"), "hack_database", ctx, Map.of());

        assertFalse(result.success());
        assertEquals("UNKNOWN_TOOL", result.errorCode());

        Mockito.verify(mockAuditService).recordAudit(
                eq(1L), eq(101L), eq("BookingAgent"), eq("hack_database"),
                eq(Map.of()), eq("ERROR"), eq("UNKNOWN_TOOL")
        );
    }

    @Test
    @DisplayName("Registry: Tool not in agent's allowed list is rejected and audited as FORBIDDEN_TOOL")
    void toolNotInAgentWhitelistRejected() {
        ToolContext ctx = new ToolContext(101L, Set.of("MEMBER"), Set.of(), 1L, fixedClock);
        // Agent allowed tools do not include search_sessions
        ToolResult result = registry.execute("IntakeAgent", Set.of("get_llm_safe_profile"), "search_sessions", ctx, Map.of());

        assertFalse(result.success());
        assertEquals("FORBIDDEN_TOOL", result.errorCode());

        Mockito.verify(mockAuditService).recordAudit(
                eq(1L), eq(101L), eq("IntakeAgent"), eq("search_sessions"),
                eq(Map.of()), eq("FORBIDDEN_TOOL"), eq("TOOL_NOT_ALLOWED_FOR_AGENT")
        );
    }

    @Test
    @DisplayName("Registry: Caller lacking required role is rejected as FORBIDDEN_TOOL")
    void callerLackingRequiredRoleRejected() {
        // User has GUEST role, not MEMBER
        ToolContext ctx = new ToolContext(101L, Set.of("GUEST"), Set.of(), 1L, fixedClock);
        ToolResult result = registry.execute("BookingAgent", Set.of("search_sessions"), "search_sessions", ctx, Map.of());

        assertFalse(result.success());
        assertEquals("FORBIDDEN_TOOL", result.errorCode());

        Mockito.verify(mockAuditService).recordAudit(
                eq(1L), eq(101L), eq("BookingAgent"), eq("search_sessions"),
                eq(Map.of()), eq("FORBIDDEN_TOOL"), eq("ROLE_INSUFFICIENT")
        );
    }

    @Test
    @DisplayName("Registry: Successful execution delegates to tool and audits OK")
    void successfulToolExecutionAudited() {
        ToolContext ctx = new ToolContext(101L, Set.of("MEMBER"), Set.of(), 1L, fixedClock);
        Mockito.when(mockTool.execute(eq(ctx), any())).thenReturn(ToolResult.ok(Map.of("found", 2), "Found 2 sessions"));

        ToolResult result = registry.execute("BookingAgent", Set.of("search_sessions"), "search_sessions", ctx, Map.of("category", "YOGA"));

        assertTrue(result.success());
        assertEquals("Found 2 sessions", result.summary());

        Mockito.verify(mockAuditService).recordAudit(
                eq(1L), eq(101L), eq("BookingAgent"), eq("search_sessions"),
                eq(Map.of("category", "YOGA")), eq("OK"), Mockito.isNull()
        );
    }

    @Test
    @DisplayName("Reflection Ban: No tool parameter DTO may declare memberId, userId, adminId, trainerId")
    void reflectionCheckCallerIdFieldsBanned() {
        List<Class<?>> paramClasses = List.of(
                com.fitflow.agent.tools.dto.SearchSessionsArgs.class,
                com.fitflow.agent.tools.dto.GetClassDetailsArgs.class,
                com.fitflow.agent.tools.dto.GetMyBookingsArgs.class,
                com.fitflow.agent.tools.dto.GetMembershipStatusArgs.class,
                com.fitflow.agent.tools.dto.ListPlansArgs.class,
                com.fitflow.agent.tools.dto.PreviewMembershipChangeArgs.class,
                com.fitflow.agent.tools.dto.GetSafeClassesArgs.class,
                com.fitflow.agent.tools.dto.GetLlmSafeProfileArgs.class,
                com.fitflow.agent.tools.dto.ProposeBookingArgs.class,
                com.fitflow.agent.tools.dto.ProposeCancelBookingArgs.class,
                com.fitflow.agent.tools.dto.ProposeMembershipChangeArgs.class,
                com.fitflow.agent.tools.dto.ProposeProfileSaveArgs.class
        );

        Set<String> bannedNames = Set.of("memberid", "userid", "adminid", "trainerid");

        for (Class<?> clazz : paramClasses) {
            for (Field field : clazz.getDeclaredFields()) {
                String nameLower = field.getName().toLowerCase();
                assertFalse(bannedNames.contains(nameLower),
                        String.format("Class %s has forbidden caller ID field: %s", clazz.getSimpleName(), field.getName()));
            }
        }
    }

    private String isNullOrEmpty() {
        return null;
    }
}
