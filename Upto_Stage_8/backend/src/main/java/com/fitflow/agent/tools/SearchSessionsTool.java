package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.tools.dto.SearchSessionsArgs;
import com.fitflow.common.pagination.PageResponse;
import com.fitflow.session.ClassSessionService;
import com.fitflow.session.dto.SessionDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchSessionsTool implements Tool {

    private final ClassSessionService classSessionService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "search_sessions";
    }

    @Override
    public String description() {
        return "Search available class sessions within an optional date range, category, or intensity.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "from", Map.of("type", "string", "description", "Start ISO timestamp (default now)"),
                        "to", Map.of("type", "string", "description", "End ISO timestamp (default now + 7 days)"),
                        "category", Map.of("type", "string", "description", "Class category: YOGA, HIIT, STRENGTH, PILATES, CARDIO, RECOVERY"),
                        "intensity", Map.of("type", "string", "description", "Intensity: LOW, MEDIUM, HIGH"),
                        "onlyAvailable", Map.of("type", "boolean", "description", "Only sessions with free spots")
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
        SearchSessionsArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), SearchSessionsArgs.class);
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        Instant from = args.from() != null ? args.from() : context.clock().instant();
        Instant to = args.to() != null ? args.to() : from.plusSeconds(7 * 24 * 3600);

        PageResponse<SessionDetailResponse> page = classSessionService.getSessions(
                from, to, args.category(), args.intensity(), null, args.onlyAvailable(), 0, 50, context.userId());

        List<Map<String, Object>> items = page.items().stream()
                .map(s -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("sessionId", s.id());
                    m.put("className", s.className());
                    m.put("category", s.category());
                    m.put("intensity", s.intensity());
                    m.put("startsAt", s.startsAt().toString());
                    m.put("endsAt", s.endsAt().toString());
                    m.put("trainerName", s.trainerName());
                    m.put("room", s.room());
                    m.put("availableSpots", s.seatsLeft());
                    return m;
                })
                .toList();

        String summary = String.format("Found %d scheduled sessions between %s and %s", items.size(), from, to);
        return ToolResult.ok(Map.of("sessions", items, "total", items.size()), summary);
    }
}
