package com.fitflow.agent.guard;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class RoleGuard {

    public static final String REFUSAL_MESSAGE =
            "I cannot perform administrative or trainer operations. Those actions require elevated permissions.";

    private static final List<String> ADMIN_KEYWORDS = List.of(
            "delete user", "refund payment", "create promo code", "view all revenue",
            "override safety", "grant admin", "ban member", "drop table", "admin settings",
            "system settings", "change user role", "list all users", "trainer payout"
    );

    public boolean isForbiddenAdminRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        String lower = message.toLowerCase(Locale.ROOT);
        for (String kw : ADMIN_KEYWORDS) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    public String getRefusal() {
        return REFUSAL_MESSAGE;
    }
}
