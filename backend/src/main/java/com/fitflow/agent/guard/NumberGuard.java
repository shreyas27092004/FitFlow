package com.fitflow.agent.guard;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NumberGuard {

    private static final Pattern NUMBER_PATTERN = Pattern.compile(
            "(?:₹|Rs\\.?|INR\\s*)?\\s*(\\d+(?:,\\d{3})*(?:\\.\\d+)?)\\s*(?:%|kcal|calories|paise|rupees|sessions|days|mins|hours)?",
            Pattern.CASE_INSENSITIVE
    );

    // Whitelisted common numbers that don't need tool output presence (e.g. 1 session, 7 days, 18+ years, 1-7 PAR-Q)
    private static final Set<String> WHITELIST = Set.of(
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "12", "14", "18", "24", "60"
    );

    public record GuardResult(boolean verified, String unverifiedNumber, String fallbackText) {}

    public GuardResult verifyReply(String reply, String userMessage, List<String> toolOutputs, List<String> toolSummaries) {
        if (reply == null || reply.isBlank()) {
            return new GuardResult(true, null, null);
        }

        Set<String> allowedNumbers = new HashSet<>(WHITELIST);

        // Harvest numbers from user message
        if (userMessage != null) {
            allowedNumbers.addAll(extractAllNumbers(userMessage));
        }

        // Harvest numbers from tool outputs and summaries
        if (toolOutputs != null) {
            for (String output : toolOutputs) {
                if (output != null) {
                    allowedNumbers.addAll(extractAllNumbers(output));
                }
            }
        }
        if (toolSummaries != null) {
            for (String sum : toolSummaries) {
                if (sum != null) {
                    allowedNumbers.addAll(extractAllNumbers(sum));
                }
            }
        }

        // Extract numbers from candidate reply
        List<String> replyNumbers = extractAllNumbers(reply);
        for (String num : replyNumbers) {
            if (!allowedNumbers.contains(num)) {
                // Construct deterministic fallback
                StringBuilder fallback = new StringBuilder("Here is what I found:\n");
                if (toolSummaries != null && !toolSummaries.isEmpty()) {
                    for (String s : toolSummaries) {
                        fallback.append("- ").append(s).append("\n");
                    }
                } else {
                    fallback.append("No numerical data was returned by the system.");
                }
                return new GuardResult(false, num, fallback.toString().trim());
            }
        }

        return new GuardResult(true, null, null);
    }

    public List<String> extractAllNumbers(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) return result;

        Matcher matcher = NUMBER_PATTERN.matcher(text);
        while (matcher.find()) {
            String num = matcher.group(1);
            if (num != null) {
                String clean = num.replace(",", "").trim();
                if (!clean.isEmpty()) {
                    result.add(clean);
                    // Also add integer part if decimal
                    if (clean.contains(".")) {
                        String integerPart = clean.substring(0, clean.indexOf('.'));
                        if (!integerPart.isEmpty()) {
                            result.add(integerPart);
                        }
                    } else {
                        result.add(clean + ".00");
                        result.add(clean + ".0");
                    }
                }
            }
        }
        return result;
    }
}
