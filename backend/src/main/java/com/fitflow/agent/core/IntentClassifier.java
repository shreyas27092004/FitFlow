package com.fitflow.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fitflow.agent.port.ChatMessage;
import com.fitflow.agent.port.ChatModelPort;
import com.fitflow.agent.port.ChatRequest;
import com.fitflow.agent.port.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Component
public class IntentClassifier {

    public enum Intent {
        BOOKING,
        MEMBERSHIP,
        INTAKE,
        SAFE_CLASS_MATCHER,
        PLAN_BUILDER,
        WELLNESS_COACH,
        NUTRITION_NUMBERS,
        TRAINER_PREP,
        TRAINER_SCHEDULE,
        RETENTION,
        CLASS_METADATA_ASSISTANT,
        DEMAND_FORECAST
    }

    private final Map<Intent, List<String>> intentKeywords = new HashMap<>();
    private final Map<Intent, List<Pattern>> intentPatterns = new HashMap<>();

    public IntentClassifier() {
        loadIntentsYaml();
    }

    @SuppressWarnings("unchecked")
    private void loadIntentsYaml() {
        try {
            ClassPathResource res = new ClassPathResource("agents/intents.yml");
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            try (InputStream is = res.getInputStream()) {
                Map<String, Object> root = yamlMapper.readValue(is, Map.class);
                Map<String, Object> intentsMap = (Map<String, Object>) root.get("intents");
                if (intentsMap != null) {
                    for (Map.Entry<String, Object> entry : intentsMap.entrySet()) {
                        try {
                            Intent intent = Intent.valueOf(entry.getKey().toUpperCase(Locale.ROOT));
                            Map<String, Object> config = (Map<String, Object>) entry.getValue();

                            List<String> kws = (List<String>) config.get("keywords");
                            if (kws != null) {
                                intentKeywords.put(intent, kws.stream().map(String::toLowerCase).toList());
                            }

                            List<String> pats = (List<String>) config.get("patterns");
                            if (pats != null) {
                                List<Pattern> compiled = pats.stream().map(Pattern::compile).toList();
                                intentPatterns.put(intent, compiled);
                            }
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load agents/intents.yml: {}. Using hardcoded fallback.", e.getMessage());
            setupHardcodedFallbacks();
        }
    }

    private void setupHardcodedFallbacks() {
        intentKeywords.put(Intent.BOOKING, List.of("book", "booking", "reserve", "cancel booking", "my bookings"));
        intentKeywords.put(Intent.MEMBERSHIP, List.of("membership", "plan", "plans", "upgrade", "downgrade", "pricing"));
        intentKeywords.put(Intent.INTAKE, List.of("intake", "questionnaire", "par-q", "parq", "onboard"));
        intentKeywords.put(Intent.SAFE_CLASS_MATCHER, List.of("safe", "suitable", "knee friendly", "back friendly"));
        intentKeywords.put(Intent.PLAN_BUILDER, List.of("weekly plan", "build plan", "plan builder", "routine", "workout schedule"));
        intentKeywords.put(Intent.WELLNESS_COACH, List.of("coach", "progress", "weekly progress", "streak", "missed session"));
        intentKeywords.put(Intent.NUTRITION_NUMBERS, List.of("calories", "calorie", "burn", "energy expenditure", "bmr"));
        intentKeywords.put(Intent.TRAINER_PREP, List.of("prep", "prep summary", "attendees", "modifications"));
        intentKeywords.put(Intent.TRAINER_SCHEDULE, List.of("my schedule", "my sessions", "earnings", "payout"));
        intentKeywords.put(Intent.RETENTION, List.of("retention", "at risk", "at-risk", "churn", "nudge"));
        intentKeywords.put(Intent.CLASS_METADATA_ASSISTANT, List.of("metadata", "draft class", "create class"));
        intentKeywords.put(Intent.DEMAND_FORECAST, List.of("demand", "forecast", "insights", "fill rate"));
    }

    /**
     * Classifies intent using deterministic keywords/patterns first.
     * If ambiguous, invokes a single cheap LLM classification call.
     */
    public Intent classify(String message, Set<Intent> allowedIntents, ChatModelPort chatModel) {
        if (message == null || message.isBlank()) {
            return null;
        }

        String lower = message.toLowerCase(Locale.ROOT).trim();
        List<Intent> matchedIntents = new ArrayList<>();

        // 1. Check patterns first
        for (Intent intent : allowedIntents) {
            List<Pattern> patterns = intentPatterns.get(intent);
            if (patterns != null) {
                for (Pattern p : patterns) {
                    if (p.matcher(message).find()) {
                        matchedIntents.add(intent);
                        break;
                    }
                }
            }
        }

        // 2. If no pattern matched, check keywords
        if (matchedIntents.isEmpty()) {
            for (Intent intent : allowedIntents) {
                List<String> kws = intentKeywords.get(intent);
                if (kws != null) {
                    for (String kw : kws) {
                        if (lower.contains(kw)) {
                            matchedIntents.add(intent);
                            break;
                        }
                    }
                }
            }
        }

        // Single unique match
        if (matchedIntents.size() == 1) {
            return matchedIntents.get(0);
        }

        // If 0 matches or multiple matches -> Ambiguous intent.
        // Fall back to one cheap LLM classification call.
        if (chatModel != null) {
            return classifyWithLlm(message, allowedIntents, chatModel);
        }

        return null;
    }

    private Intent classifyWithLlm(String message, Set<Intent> allowedIntents, ChatModelPort chatModel) {
        String allowedOptions = String.join(", ", allowedIntents.stream().map(Enum::name).toList());
        String systemPrompt = "You are a classifier. Classify the user query into exactly one of: [" + allowedOptions + "]. Return ONLY the single enum name in all uppercase with no extra words or punctuation.";

        ChatRequest req = new ChatRequest(
                List.of(ChatMessage.system(systemPrompt), ChatMessage.user(message)),
                List.of(),
                0.0,
                20
        );

        try {
            ChatResponse resp = chatModel.chat(req);
            String raw = resp.content() != null ? resp.content().trim().toUpperCase(Locale.ROOT) : "";
            // Strip any quotes or whitespace
            raw = raw.replaceAll("[^A-Z_]", "");
            for (Intent allowed : allowedIntents) {
                if (allowed.name().equalsIgnoreCase(raw)) {
                    return allowed;
                }
            }
        } catch (Exception e) {
            log.warn("LLM intent classification failed: {}", e.getMessage());
        }

        return null;
    }
}
