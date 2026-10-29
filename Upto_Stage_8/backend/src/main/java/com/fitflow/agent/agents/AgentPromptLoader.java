package com.fitflow.agent.agents;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AgentPromptLoader {

    private final Map<String, String> cachedPrompts = new ConcurrentHashMap<>();

    public String loadAgentPrompt(String agentId) {
        return cachedPrompts.computeIfAbsent(agentId, id -> {
            String shared = readResourceFile("agents/_shared.md");
            String specific = readResourceFile("agents/" + id + ".md");
            return shared + "\n\n" + specific;
        });
    }

    private String readResourceFile(String path) {
        try {
            ClassPathResource res = new ClassPathResource(path);
            try (InputStream is = res.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("Failed to load prompt resource at {}: {}", path, e.getMessage());
            return "# Agent instructions for " + path;
        }
    }
}
