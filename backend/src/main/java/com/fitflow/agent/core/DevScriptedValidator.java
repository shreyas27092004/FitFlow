package com.fitflow.agent.core;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class DevScriptedValidator {

    private final Environment environment;

    @Value("${fitflow.agent.dev-scripted:false}")
    private boolean devScripted;

    @PostConstruct
    public void validate() {
        if (devScripted) {
            boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
            if (isProd) {
                throw new IllegalStateException("Security violation: fitflow.agent.dev-scripted must NOT be enabled in the prod profile");
            }
            log.info("Agent running with dev-scripted=true (mock/scripted LLM enabled for development/testing).");
        }
    }

    public boolean isDevScripted() {
        return devScripted;
    }
}
