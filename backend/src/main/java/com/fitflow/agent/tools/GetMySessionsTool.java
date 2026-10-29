package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.tools.dto.GetMySessionsArgs;
import com.fitflow.session.ClassSessionService;
import com.fitflow.session.dto.TrainerSessionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetMySessionsTool implements Tool {

    private final ClassSessionService sessionService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "get_my_sessions";
    }

    @Override
    public String description() {
        return "Get trainer's scheduled teaching sessions within a specified date window.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "from", Map.of("type", "string", "description", "Start ISO timestamp (defaults to now)"),
                        "to", Map.of("type", "string", "description", "End ISO timestamp (defaults to now + 14 days)")
                )
        );
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public Set<String> requiredRoles() {
        return Set.of("TRAINER");
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of();
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> rawArgs) {
        GetMySessionsArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), GetMySessionsArgs.class);
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        Instant from = args.from() != null ? args.from() : context.clock().instant();
        Instant to = args.to() != null ? args.to() : from.plus(Duration.ofDays(14));

        try {
            List<TrainerSessionResponse> sessions = sessionService.getTrainerSessions(context.userId(), from, to);

            List<Map<String, Object>> sessionMaps = new ArrayList<>();
            for (TrainerSessionResponse s : sessions) {
                Map<String, Object> map = new HashMap<>();
                map.put("sessionId", s.id());
                map.put("className", s.className());
                map.put("startsAt", s.startsAt().toString());
                map.put("endsAt", s.endsAt().toString());
                map.put("room", s.room());
                map.put("capacity", s.capacity());
                map.put("bookedCount", s.bookedCount());
                map.put("status", s.status());
                sessionMaps.add(map);
            }

            Map<String, Object> data = Map.of(
                    "sessions", sessionMaps,
                    "count", sessionMaps.size()
            );

            String summary = String.format("Found %d scheduled session(s) between %s and %s.", sessionMaps.size(), from, to);
            return ToolResult.ok(data, summary);
        } catch (Exception e) {
            log.error("Failed to query trainer sessions: {}", e.getMessage(), e);
            return ToolResult.error("EXECUTION_FAILED", e.getMessage());
        }
    }
}
