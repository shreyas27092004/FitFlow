package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.dto.PendingActionProposal;
import com.fitflow.agent.service.PendingActionService;
import com.fitflow.agent.tools.dto.ProposeCancelBookingArgs;
import com.fitflow.booking.BookingService;
import com.fitflow.booking.dto.MemberBookingResponse;
import com.fitflow.common.pagination.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProposeCancelBookingTool implements Tool {

    private final BookingService bookingService;
    private final PendingActionService pendingActionService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "propose_cancel_booking";
    }

    @Override
    public String description() {
        return "Propose cancelling a booking. Creates a pending action requiring human confirmation.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "bookingId", Map.of("type", "integer", "description", "ID of the booking to cancel")
                ),
                "required", java.util.List.of("bookingId")
        );
    }

    @Override
    public boolean isReadOnly() {
        return false;
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
        ProposeCancelBookingArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), ProposeCancelBookingArgs.class);
            if (args.bookingId() == null) {
                return ToolResult.error("INVALID_ARGUMENTS", "bookingId is required");
            }
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        try {
            // Dry run: verify booking exists and belongs to member
            PageResponse<MemberBookingResponse> page = bookingService.getMemberBookings(context.userId(), null, null, null, 0, 100);
            Optional<MemberBookingResponse> match = page.items().stream()
                    .filter(b -> b.bookingId().equals(args.bookingId()))
                    .findFirst();

            if (match.isEmpty()) {
                return ToolResult.error("BOOKING_NOT_FOUND", "Booking not found for current member with id: " + args.bookingId());
            }

            MemberBookingResponse booking = match.get();
            if (!"CONFIRMED".equalsIgnoreCase(booking.status())) {
                return ToolResult.error("INVALID_STATUS", "Booking is not in CONFIRMED status (status: " + booking.status() + ")");
            }

            String summary = String.format("Cancel booking #%d for '%s' on %s. Session entitlement will be restored per cancellation policy.",
                    booking.bookingId(),
                    booking.className(),
                    booking.startsAt().toString());

            PendingActionProposal action = pendingActionService.propose(
                    context.conversationId(),
                    context.userId(),
                    name(),
                    args,
                    summary
            );

            Map<String, Object> data = Map.of(
                    "pendingActionId", action.id(),
                    "bookingId", args.bookingId(),
                    "summary", summary,
                    "status", "PENDING"
            );

            return ToolResult.ok(data, summary);
        } catch (Exception e) {
            return ToolResult.error("VALIDATION_ERROR", "Dry run failed: " + e.getMessage());
        }
    }
}
