package com.fitflow.agent.core;

import com.fitflow.agent.port.ScriptedChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class AgentConfig {

    @Bean
    @Profile("test")
    public ScriptedChatModel scriptedChatModel() {
        return new ScriptedChatModel();
    }
}
