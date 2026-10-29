package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.tools.dto.GetMissedSessionsCatchupArgs;
import com.fitflow.session.dto.SessionDetailResponse;
import com.fitflow.wellness.WellnessProfileService;
import com.fitflow.wellness.dto.SafeClassesResponse;
import com.fitflow.wellness.dto.SafeSessionItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetMissedSessionsCatchupTool implements Tool {

    private final WellnessProfileService wellnessProfileService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "get_missed_sessions_catchup";
    }

    @Override
    public String description() {
        return "Get up to 2 upcoming safe sessions to catch up on missed weekly workouts.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "weekStart", Map.of("type", "string", "description", "Week start date (YYYY-MM-DD)")
                )
        );
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public Set<String> requiredRoles() {
        return Set.of("MEMBER");
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of();
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> rawArgs) {
        GetMissedSessionsCatchupArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), GetMissedSessionsCatchupArgs.class);
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        Instant now = context.clock().instant();
        Instant windowEnd = now.plus(Duration.ofDays(7));

        try {
            SafeClassesResponse safeResponse = wellnessProfileService.getSafeClasses(context.userId(), now, windowEnd, 0, 50);

            List<SessionDetailResponse> catchupCandidates = safeResponse.eligible().stream()
                    .map(SafeSessionItem::session)
                    .filter(s -> s.startsAt().isAfter(now))
                    .sorted(Comparator.comparing(SessionDetailResponse::startsAt).thenComparing(SessionDetailResponse::id))
                    .limit(2)
                    .toList();

            List<Map<String, Object>> sessionMaps = new ArrayList<>();
            for (SessionDetailResponse s : catchupCandidates) {
                Map<String, Object> map = new HashMap<>();
                map.put("sessionId", s.id());
                map.put("className", s.className());
                map.put("category", s.category());
                map.put("startsAt", s.startsAt().toString());
                map.put("seatsLeft", s.seatsLeft());
                map.put("room", s.room());
                sessionMaps.add(map);
            }

            Map<String, Object> data = Map.of(
                    "catchupSessions", sessionMaps,
                    "count", sessionMaps.size()
            );

            String summary = String.format("Found %d recommended catch-up session(s) in the next 7 days.", sessionMaps.size());
            return ToolResult.ok(data, summary);
        } catch (Exception e) {
            log.error("Failed to query catch-up sessions: {}", e.getMessage(), e);
            return ToolResult.error("EXECUTION_FAILED", e.getMessage());
        }
    }
}
