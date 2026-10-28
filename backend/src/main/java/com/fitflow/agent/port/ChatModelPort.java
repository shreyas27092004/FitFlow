package com.fitflow.agent.port;

public interface ChatModelPort {
    ChatResponse chat(ChatRequest request);
}
