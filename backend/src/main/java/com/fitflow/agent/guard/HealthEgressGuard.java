package com.fitflow.agent.guard;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HealthEgressGuard {

    public boolean containsUnsafeHealthContent(String text, List<String> canaryStrings) {
        if (text == null || canaryStrings == null || canaryStrings.isEmpty()) {
            return false;
        }

        for (String canary : canaryStrings) {
            if (canary != null && !canary.isBlank() && text.contains(canary)) {
                return true;
            }
        }
        return false;
    }
}
