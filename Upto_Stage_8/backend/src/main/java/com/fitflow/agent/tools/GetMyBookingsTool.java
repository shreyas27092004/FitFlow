package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.tools.dto.GetMyBookingsArgs;
import com.fitflow.booking.BookingService;
import com.fitflow.booking.dto.MemberBookingResponse;
import com.fitflow.booking.model.BookingStatus;
import com.fitflow.common.pagination.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetMyBookingsTool implements Tool {

    private final BookingService bookingService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "get_my_bookings";
    }

    @Override
    public String description() {
        return "Get the member's existing class bookings.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "status", Map.of("type", "string", "description", "Optional status filter (e.g. CONFIRMED, CANCELLED)")
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
        GetMyBookingsArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), GetMyBookingsArgs.class);
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        BookingStatus bStatus = null;
        if (args.status() != null && !args.status().isBlank()) {
            try {
                bStatus = BookingStatus.valueOf(args.status().trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        PageResponse<MemberBookingResponse> bookingsPage = bookingService.getMemberBookings(
                context.userId(), bStatus, args.from(), args.to(), 0, 50);

        List<Map<String, Object>> list = bookingsPage.items().stream()
                .map(b -> Map.<String, Object>of(
                        "bookingId", b.bookingId(),
                        "sessionId", b.sessionId(),
                        "className", b.className(),
                        "startsAt", b.startsAt().toString(),
                        "status", b.status()
                ))
                .toList();

        String summary = String.format("Found %d bookings for current member", list.size());
        return ToolResult.ok(Map.of("bookings", list, "count", list.size()), summary);
    }
}
