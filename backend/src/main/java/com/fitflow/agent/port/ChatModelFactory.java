package com.fitflow.agent.port;

import dev.langchain4j.model.chat.ChatModel;

@FunctionalInterface
public interface ChatModelFactory {
    ChatModel create(String apiKey, String modelName);
}
