package com.fitflow.agent.core;

import com.fitflow.agent.agents.BookingAgent;
import com.fitflow.agent.agents.IntakeAgent;
import com.fitflow.agent.agents.MembershipAgent;
import com.fitflow.agent.agents.SafeClassMatcherAgent;
import com.fitflow.agent.agents.SpecialistAgent;
import com.fitflow.agent.port.ChatModelPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupervisorRouter {

    public static final String CLARIFICATION_PROMPT =
            "I'm not quite sure which area you need assistance with. Would you like help with booking a class, checking your membership, completing fitness intake, or finding safe classes?";

    private final BookingAgent bookingAgent;
    private final MembershipAgent membershipAgent;
    private final IntakeAgent intakeAgent;
    private final SafeClassMatcherAgent safeClassMatcherAgent;
    private final IntentClassifier intentClassifier;

    public record RouteResult(SpecialistAgent agent, String clarificationMessage) {
        public boolean hasAgent() {
            return agent != null;
        }
    }

    public RouteResult route(Long userId, Set<String> roles, Set<String> permissions, String message, ChatModelPort chatModel) {
        // Deterministic role gate
        Set<IntentClassifier.Intent> allowedIntents = getAllowedIntentsForRoles(roles);
        if (allowedIntents.isEmpty()) {
            return new RouteResult(null, "No agent available for caller's role.");
        }

        IntentClassifier.Intent intent = intentClassifier.classify(message, allowedIntents, chatModel);
        if (intent == null) {
            return new RouteResult(null, CLARIFICATION_PROMPT);
        }

        SpecialistAgent agent = switch (intent) {
            case BOOKING -> bookingAgent;
            case MEMBERSHIP -> membershipAgent;
            case INTAKE -> intakeAgent;
            case SAFE_CLASS_MATCHER -> safeClassMatcherAgent;
        };

        return new RouteResult(agent, null);
    }

    public Set<IntentClassifier.Intent> getAllowedIntentsForRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptySet();
        }

        Set<IntentClassifier.Intent> set = new HashSet<>();
        // In Stage 8, only MEMBER role is mapped to agents
        if (roles.contains("MEMBER")) {
            set.add(IntentClassifier.Intent.BOOKING);
            set.add(IntentClassifier.Intent.MEMBERSHIP);
            set.add(IntentClassifier.Intent.INTAKE);
            set.add(IntentClassifier.Intent.SAFE_CLASS_MATCHER);
        }
        return set;
    }
}
