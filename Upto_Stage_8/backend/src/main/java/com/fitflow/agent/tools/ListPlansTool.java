package com.fitflow.agent.tools;

import com.fitflow.membership.MembershipService;
import com.fitflow.membership.dto.PlanDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ListPlansTool implements Tool {

    private final MembershipService membershipService;

    @Override
    public String name() {
        return "list_plans";
    }

    @Override
    public String description() {
        return "List all active membership plans available for subscription.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of("type", "object", "properties", Map.of());
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
        List<PlanDto> plans = membershipService.getActivePlans();

        List<Map<String, Object>> list = plans.stream()
                .map(p -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("code", p.code());
                    map.put("name", p.name());
                    map.put("pricePaise", p.pricePaise());
                    map.put("priceRupees", p.pricePaise() != null ? p.pricePaise() / 100 : 0);
                    map.put("sessionQuota", p.sessionQuota());
                    map.put("durationDays", p.durationDays());
                    map.put("isFree", p.isFree());
                    return map;
                })
                .toList();

        String summary = String.format("Found %d active membership plans", list.size());
        return ToolResult.ok(Map.of("plans", list), summary);
    }
}
