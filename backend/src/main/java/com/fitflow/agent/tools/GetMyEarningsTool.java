package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.tools.dto.GetMyEarningsArgs;
import com.fitflow.payout.PayoutService;
import com.fitflow.payout.dto.TrainerEarningsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetMyEarningsTool implements Tool {

    private final PayoutService payoutService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "get_my_earnings";
    }

    @Override
    public String description() {
        return "Get trainer earnings, including total earnings, pending payouts, paid payouts, and itemized class session earnings.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "from", Map.of("type", "string", "description", "Start ISO timestamp (defaults to 30 days ago)"),
                        "to", Map.of("type", "string", "description", "End ISO timestamp (defaults to now)")
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
        GetMyEarningsArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), GetMyEarningsArgs.class);
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        Instant to = args.to() != null ? args.to() : context.clock().instant();
        Instant from = args.from() != null ? args.from() : to.minus(Duration.ofDays(30));

        try {
            TrainerEarningsResponse res = payoutService.getTrainerEarnings(context.userId(), from, to);

            Map<String, Object> data = new HashMap<>();
            data.put("totalPaise", res.totalPaise());
            data.put("pendingPaise", res.pendingPaise());
            data.put("paidPaise", res.paidPaise());
            data.put("sessionCount", res.items() != null ? res.items().size() : 0);
            data.put("items", res.items());

            String summary = String.format("Trainer earnings (%s to %s): Total %d paise, Pending %d paise, Paid %d paise across %d session(s).",
                    from, to, res.totalPaise(), res.pendingPaise(), res.paidPaise(), res.items() != null ? res.items().size() : 0);

            return ToolResult.ok(data, summary);
        } catch (Exception e) {
            log.error("Failed to get trainer earnings: {}", e.getMessage(), e);
            return ToolResult.error("EXECUTION_FAILED", e.getMessage());
        }
    }
}
