package com.fitflow.agent.guard;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class ScopeGuard {

    public static final String REFUSAL_MESSAGE =
            "I cannot provide medical diagnoses, legal advice, or information about other members. Wellness suggestions, not medical advice.";

    private static final List<String> MEDICAL_DIAGNOSIS_KEYWORDS = List.of(
            "diagnose", "diagnosis", "what illness", "prescribe", "cure my",
            "am i sick", "cancer", "symptoms of heart attack", "medical advice"
    );

    private static final List<String> LEGAL_ADVICE_KEYWORDS = List.of(
            "legal advice", "sue the gym", "lawsuit", "liability waiver legal", "hire a lawyer"
    );

    private static final List<String> OTHER_MEMBERS_KEYWORDS = List.of(
            "other member", "other user", "another user's", "another member's",
            "all members' profiles", "someone else's booking", "user 2's password"
    );

    public boolean isOutOfScope(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        String lower = message.toLowerCase(Locale.ROOT);

        for (String kw : MEDICAL_DIAGNOSIS_KEYWORDS) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        for (String kw : LEGAL_ADVICE_KEYWORDS) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        for (String kw : OTHER_MEMBERS_KEYWORDS) {
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
