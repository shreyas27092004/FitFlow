package com.fitflow.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.entity.AgentActionAudit;
import com.fitflow.agent.repository.AgentActionAuditRepository;
import com.fitflow.agent.service.AgentAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentAuditTest {

    private AgentActionAuditRepository auditRepository;
    private Clock fixedClock;
    private ObjectMapper objectMapper;
    private AgentAuditService auditService;

    private final Instant baseTime = Instant.parse("2026-11-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        auditRepository = mock(AgentActionAuditRepository.class);
        fixedClock = Clock.fixed(baseTime, ZoneOffset.UTC);
        objectMapper = new ObjectMapper();
        auditService = new AgentAuditService(auditRepository, fixedClock, objectMapper);
    }

    @Test
    @DisplayName("Audit Redaction: IDs, enums and numbers preserved; health data redacted; free text hashed")
    void testAuditRedaction() throws Exception {
        Map<String, Object> rawArgs = Map.of(
                "sessionId", 501L,
                "targetPlanCode", "PRO",
                "action", "UPGRADE",
                "profileDraft", Map.of("pastExperienceNote", "sensitive medical history"),
                "injuryNote", "torn ACL",
                "customUserNote", "Please make sure trainer Aisha is available for this session tomorrow"
        );

        auditService.recordAudit(1L, 42L, "BookingAgent", "propose_booking", rawArgs, "OK", null);

        ArgumentCaptor<AgentActionAudit> captor = ArgumentCaptor.forClass(AgentActionAudit.class);
        verify(auditRepository).save(captor.capture());

        AgentActionAudit audit = captor.getValue();
        assertEquals(1L, audit.getConversationId());
        assertEquals(42L, audit.getUserId());
        assertEquals("BookingAgent", audit.getAgentName());
        assertEquals("propose_booking", audit.getToolName());
        assertEquals("OK", audit.getResultStatus());

        Map<String, Object> safeMap = objectMapper.readValue(audit.getArgsRedacted(), new TypeReference<>() {});

        // Preserved fields
        assertEquals(501, safeMap.get("sessionId"));
        assertEquals("PRO", safeMap.get("targetPlanCode"));
        assertEquals("UPGRADE", safeMap.get("action"));

        // Redacted health fields
        assertEquals("[REDACTED_HEALTH_DATA]", safeMap.get("profileDraft"));
        assertEquals("[REDACTED_HEALTH_DATA]", safeMap.get("injuryNote"));

        // Free text note hashed with length and sha
        String redactedText = (String) safeMap.get("customUserNote");
        assertNotNull(redactedText);
        assertTrue(redactedText.startsWith("len="));
        assertTrue(redactedText.contains(":sha="));
        assertFalse(redactedText.contains("sensitive"));
        assertFalse(redactedText.contains("Aisha"));
    }
}
