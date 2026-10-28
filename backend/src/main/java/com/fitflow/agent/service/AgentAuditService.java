package com.fitflow.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.entity.AgentActionAudit;
import com.fitflow.agent.repository.AgentActionAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAuditService {

    private static final Set<String> ALLOWED_ARG_KEYS = Set.of(
            "sessionId", "bookingId", "classId", "targetPlanCode", "action",
            "from", "to", "category", "intensity", "onlyAvailable", "status"
    );

    private final AgentActionAuditRepository auditRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Transactional("postgresTransactionManager")
    public void recordAudit(
            Long conversationId,
            Long userId,
            String agentName,
            String toolName,
            Map<String, Object> rawArgs,
            String resultStatus,
            String error) {

        String redactedJson = redactArgs(rawArgs);

        AgentActionAudit audit = AgentActionAudit.builder()
                .conversationId(conversationId != null ? conversationId : 0L)
                .userId(userId != null ? userId : 0L)
                .agentName(agentName != null ? agentName : "UNKNOWN")
                .toolName(toolName != null ? toolName : "UNKNOWN")
                .argsRedacted(redactedJson)
                .resultStatus(resultStatus != null ? resultStatus : "OK")
                .error(error)
                .createdAt(clock.instant())
                .build();

        auditRepository.save(audit);
    }

    public String redactArgs(Map<String, Object> rawArgs) {
        if (rawArgs == null || rawArgs.isEmpty()) {
            return "{}";
        }

        Map<String, Object> safe = new HashMap<>();
        for (Map.Entry<String, Object> entry : rawArgs.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();

            // Health fields are strictly forbidden
            if (key.equalsIgnoreCase("profileDraft") || key.toLowerCase().contains("health")
                    || key.toLowerCase().contains("injury") || key.toLowerCase().contains("parq")) {
                safe.put(key, "[REDACTED_HEALTH_DATA]");
                continue;
            }

            if (ALLOWED_ARG_KEYS.contains(key)) {
                if (val instanceof String s && s.length() > 50) {
                    safe.put(key, redactFreeText(s));
                } else {
                    safe.put(key, val);
                }
            } else if (val instanceof Number || val instanceof Boolean) {
                safe.put(key, val);
            } else if (val instanceof String s) {
                safe.put(key, redactFreeText(s));
            } else {
                safe.put(key, "[REDACTED]");
            }
        }

        try {
            return objectMapper.writeValueAsString(safe);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String redactFreeText(String text) {
        if (text == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            String prefix = HexFormat.of().formatHex(hash).substring(0, 8);
            return "len=" + text.length() + ":sha=" + prefix;
        } catch (Exception e) {
            return "len=" + text.length();
        }
    }
}
