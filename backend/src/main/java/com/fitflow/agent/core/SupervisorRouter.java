package com.fitflow.agent.core;

import com.fitflow.agent.agents.BookingAgent;
import com.fitflow.agent.agents.ClassMetadataAssistant;
import com.fitflow.agent.agents.DemandForecastAgent;
import com.fitflow.agent.agents.IntakeAgent;
import com.fitflow.agent.agents.MembershipAgent;
import com.fitflow.agent.agents.NutritionNumbersAgent;
import com.fitflow.agent.agents.PlanBuilderAgent;
import com.fitflow.agent.agents.RetentionAgent;
import com.fitflow.agent.agents.SafeClassMatcherAgent;
import com.fitflow.agent.agents.SpecialistAgent;
import com.fitflow.agent.agents.TrainerPrepAgent;
import com.fitflow.agent.agents.TrainerScheduleAgent;
import com.fitflow.agent.agents.WellnessCoachAgent;
import com.fitflow.agent.port.ChatModelPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupervisorRouter {

    public static final String CLARIFICATION_PROMPT =
            "I'm not quite sure which area you need assistance with. Would you like help with booking, membership, weekly planning, progress, or workouts?";

    public static final String NUTRITION_REFUSAL_MESSAGE =
            "FitFlow provides calorie expenditure estimates only. We do not provide meal plans, macros, supplements, or dietary advice. Please consult a qualified dietitian or doctor for personalized nutrition plans.";

    private static final Pattern NUTRITION_REFUSAL_PATTERN = Pattern.compile(
            "(?i)\\b(meal\\s*plans?|diet\\s*plans?|macros?|macronutrients?|supplements?|vitamins?|creatine|whey\\s*protein|lose\\s+\\d+\\s*(kg|lbs|pounds)|weight[\\s-]loss\\s+rate)\\b");

    private final BookingAgent bookingAgent;
    private final MembershipAgent membershipAgent;
    private final IntakeAgent intakeAgent;
    private final SafeClassMatcherAgent safeClassMatcherAgent;
    private final PlanBuilderAgent planBuilderAgent;
    private final WellnessCoachAgent wellnessCoachAgent;
    private final NutritionNumbersAgent nutritionNumbersAgent;
    private final TrainerPrepAgent trainerPrepAgent;
    private final TrainerScheduleAgent trainerScheduleAgent;
    private final RetentionAgent retentionAgent;
    private final ClassMetadataAssistant classMetadataAssistant;
    private final DemandForecastAgent demandForecastAgent;
    private final IntentClassifier intentClassifier;

    public record RouteResult(SpecialistAgent agent, String clarificationMessage) {
        public boolean hasAgent() {
            return agent != null;
        }

        public boolean isDirectRefusal() {
            return agent == null && NUTRITION_REFUSAL_MESSAGE.equals(clarificationMessage);
        }
    }

    public RouteResult route(Long userId, Set<String> roles, Set<String> permissions, String message, ChatModelPort chatModel) {
        // Deterministic nutrition boundary refusal
        if (message != null && NUTRITION_REFUSAL_PATTERN.matcher(message.trim()).find()) {
            return new RouteResult(null, NUTRITION_REFUSAL_MESSAGE);
        }

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
            case PLAN_BUILDER -> planBuilderAgent;
            case WELLNESS_COACH -> wellnessCoachAgent;
            case NUTRITION_NUMBERS -> nutritionNumbersAgent;
            case TRAINER_PREP -> trainerPrepAgent;
            case TRAINER_SCHEDULE -> trainerScheduleAgent;
            case RETENTION -> retentionAgent;
            case CLASS_METADATA_ASSISTANT -> classMetadataAssistant;
            case DEMAND_FORECAST -> demandForecastAgent;
        };

        return new RouteResult(agent, null);
    }

    public Set<IntentClassifier.Intent> getAllowedIntentsForRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptySet();
        }

        Set<IntentClassifier.Intent> set = new HashSet<>();
        if (roles.contains("MEMBER")) {
            set.add(IntentClassifier.Intent.BOOKING);
            set.add(IntentClassifier.Intent.MEMBERSHIP);
            set.add(IntentClassifier.Intent.INTAKE);
            set.add(IntentClassifier.Intent.SAFE_CLASS_MATCHER);
            set.add(IntentClassifier.Intent.PLAN_BUILDER);
            set.add(IntentClassifier.Intent.WELLNESS_COACH);
            set.add(IntentClassifier.Intent.NUTRITION_NUMBERS);
        }
        if (roles.contains("TRAINER")) {
            set.add(IntentClassifier.Intent.TRAINER_PREP);
            set.add(IntentClassifier.Intent.TRAINER_SCHEDULE);
        }
        if (roles.contains("ADMIN")) {
            set.add(IntentClassifier.Intent.RETENTION);
            set.add(IntentClassifier.Intent.CLASS_METADATA_ASSISTANT);
            set.add(IntentClassifier.Intent.DEMAND_FORECAST);
        }
        return set;
    }
}
