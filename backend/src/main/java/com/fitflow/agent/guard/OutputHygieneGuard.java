package com.fitflow.agent.guard;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class OutputHygieneGuard {

    public static final int MAX_LENGTH = 1200;

    private static final Pattern HTML_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern TOOL_SYNTAX_PATTERN = Pattern.compile("<(?:tool_call|function_call|tool_code)[^>]*>.*?</(?:tool_call|function_call|tool_code)>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern SYSTEM_LEAK_PATTERN = Pattern.compile("(?m)^(?:System:|System Prompt:|Human:|Assistant:)\\s*");

    public String sanitize(String text) {
        if (text == null) {
            return "";
        }

        // 1. Remove tool-call syntax
        String cleaned = TOOL_SYNTAX_PATTERN.matcher(text).replaceAll("");

        // 2. Remove HTML tags
        cleaned = HTML_PATTERN.matcher(cleaned).replaceAll("");

        // 3. Remove system prompt leak headers
        cleaned = SYSTEM_LEAK_PATTERN.matcher(cleaned).replaceAll("");

        cleaned = cleaned.trim();

        // 4. Truncate to MAX_LENGTH
        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH).trim();
        }

        return cleaned;
    }
}
