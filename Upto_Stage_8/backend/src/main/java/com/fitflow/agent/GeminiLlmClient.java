package com.fitflow.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.port.ChatMessage;
import com.fitflow.agent.port.ChatModelFactory;
import com.fitflow.agent.port.ChatModelPort;
import com.fitflow.agent.port.ChatRequest;
import com.fitflow.agent.port.ChatResponse;
import com.fitflow.agent.port.ToolCall;
import com.fitflow.agent.port.ToolDefinition;
import com.fitflow.settings.AiConnectionTester;
import com.fitflow.settings.entity.AppSetting;
import com.fitflow.settings.repository.AppSettingRepository;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@Profile("!test")
public class GeminiLlmClient implements LlmClient, ChatModelPort, AiConnectionTester {

    public static final String MOCK_MESSAGE = "AI is not configured by the administrator.";
    public static final String SETTING_AI_MODEL = "ai_model";

    private final LlmKeyProvider llmKeyProvider;
    private final AppSettingRepository appSettingRepository;
    private final ObjectMapper objectMapper;
    private final String defaultModel;
    private final ChatModelFactory chatModelFactory;

    private volatile String cachedClientCacheKey = null;
    private volatile ChatModel cachedChatModel = null;
    private volatile String lastTestMessage = "Not tested yet";

    @org.springframework.beans.factory.annotation.Autowired
    public GeminiLlmClient(
            LlmKeyProvider llmKeyProvider,
            AppSettingRepository appSettingRepository,
            ObjectMapper objectMapper,
            @Value("${fitflow.ai.default-model:gemini-1.5-flash}") String defaultModel) {
        this(llmKeyProvider, appSettingRepository, objectMapper, defaultModel,
                (key, model) -> GoogleAiGeminiChatModel.builder()
                        .apiKey(key)
                        .modelName(model)
                        .temperature(0.2)
                        .maxOutputTokens(800)
                        .build());
    }

    public GeminiLlmClient(
            LlmKeyProvider llmKeyProvider,
            AppSettingRepository appSettingRepository,
            ObjectMapper objectMapper,
            String defaultModel,
            ChatModelFactory chatModelFactory) {
        this.llmKeyProvider = llmKeyProvider;
        this.appSettingRepository = appSettingRepository;
        this.objectMapper = objectMapper;
        this.defaultModel = defaultModel;
        this.chatModelFactory = chatModelFactory;
    }

    public String getCurrentModelName() {
        return appSettingRepository.findById(SETTING_AI_MODEL)
                .map(AppSetting::getValue)
                .filter(v -> !v.isBlank())
                .orElse(defaultModel);
    }

    public boolean isConfigured() {
        return llmKeyProvider.currentKey().isPresent();
    }

    public String getMode() {
        return isConfigured() ? "live" : "mock";
    }

    @Override
    public String generateResponse(String prompt) {
        if (!isConfigured()) {
            return MOCK_MESSAGE;
        }
        ChatResponse resp = chat(new ChatRequest(List.of(ChatMessage.user(prompt)), List.of(), 0.2, 800));
        return resp.content();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        Optional<String> keyOpt = llmKeyProvider.currentKey();
        if (keyOpt.isEmpty()) {
            log.debug("GeminiLlmClient executing in mock mode (no key configured)");
            return new ChatResponse(MOCK_MESSAGE, List.of(), "STOP");
        }

        String apiKey = keyOpt.get();
        String modelName = getCurrentModelName();
        ChatModel model = getOrBuildChatModel(apiKey, modelName);

        // Convert messages
        List<dev.langchain4j.data.message.ChatMessage> lcMessages = new ArrayList<>();
        for (ChatMessage m : request.messages()) {
            if (m.role() == null) continue;
            switch (m.role()) {
                case SYSTEM -> lcMessages.add(SystemMessage.from(m.content() != null ? m.content() : ""));
                case USER -> lcMessages.add(UserMessage.from(m.content() != null ? m.content() : ""));
                case ASSISTANT -> {
                    if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                        List<ToolExecutionRequest> reqs = m.toolCalls().stream()
                                .map(tc -> ToolExecutionRequest.builder()
                                        .id(tc.id())
                                        .name(tc.name())
                                        .arguments(tc.argumentsJson())
                                        .build())
                                .toList();
                        lcMessages.add(AiMessage.from(m.content() != null ? m.content() : "", reqs));
                    } else {
                        lcMessages.add(AiMessage.from(m.content() != null ? m.content() : ""));
                    }
                }
                case TOOL -> lcMessages.add(ToolExecutionResultMessage.from(m.toolCallId(), "", m.content() != null ? m.content() : ""));
            }
        }

        // Convert tools
        List<ToolSpecification> lcTools = new ArrayList<>();
        if (request.tools() != null) {
            for (ToolDefinition def : request.tools()) {
                try {
                    if (def.parametersJsonSchema() != null && !def.parametersJsonSchema().isEmpty()) {
                        Map<String, Object> specMap = Map.of(
                                "name", def.name(),
                                "description", def.description() != null ? def.description() : "",
                                "parameters", def.parametersJsonSchema()
                        );
                        lcTools.add(ToolSpecification.fromJson(objectMapper.writeValueAsString(specMap)));
                    } else {
                        lcTools.add(ToolSpecification.builder()
                                .name(def.name())
                                .description(def.description() != null ? def.description() : "")
                                .build());
                    }
                } catch (Exception e) {
                    log.error("Failed to serialize tool specification for {}: {}", def.name(), e.getMessage());
                }
            }
        }

        dev.langchain4j.model.chat.request.ChatRequest.Builder reqBuilder = dev.langchain4j.model.chat.request.ChatRequest.builder()
                .messages(lcMessages);

        if (!lcTools.isEmpty()) {
            reqBuilder.toolSpecifications(lcTools);
        }
        if (request.temperature() != null) {
            reqBuilder.temperature(request.temperature());
        }
        if (request.maxTokens() != null) {
            reqBuilder.maxOutputTokens(request.maxTokens());
        }

        if (log.isDebugEnabled()) {
            log.debug("Dispatching request to Gemini model '{}' with {} messages and {} tools",
                    modelName, lcMessages.size(), lcTools.size());
        }

        dev.langchain4j.model.chat.response.ChatResponse lcResponse = model.chat(reqBuilder.build());

        AiMessage ai = lcResponse.aiMessage();
        String text = ai.text() != null ? ai.text() : "";
        List<ToolCall> toolCalls = new ArrayList<>();
        if (ai.hasToolExecutionRequests()) {
            for (ToolExecutionRequest ter : ai.toolExecutionRequests()) {
                toolCalls.add(new ToolCall(ter.id(), ter.name(), ter.arguments()));
            }
        }
        String finishReason = lcResponse.finishReason() != null ? lcResponse.finishReason().name() : "STOP";

        return new ChatResponse(text, toolCalls, finishReason);
    }

    private synchronized ChatModel getOrBuildChatModel(String apiKey, String modelName) {
        String keyHash = hashKey(apiKey);
        String cacheKey = keyHash + ":" + modelName;

        if (cachedChatModel != null && cacheKey.equals(cachedClientCacheKey)) {
            return cachedChatModel;
        }

        log.debug("Rebuilding Gemini chat model client for model '{}' (key hash: {})", modelName, keyHash.substring(0, Math.min(8, keyHash.length())));
        cachedChatModel = chatModelFactory.create(apiKey, modelName);
        cachedClientCacheKey = cacheKey;
        return cachedChatModel;
    }

    private String hashKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(key.hashCode());
        }
    }

    @Override
    public boolean testConnection(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            lastTestMessage = "API key is missing or blank";
            return false;
        }

        try {
            String model = getCurrentModelName();
            ChatModel testModel = chatModelFactory.create(apiKey, model);
            String reply = testModel.chat("ping");
            if (reply != null && !reply.isBlank()) {
                lastTestMessage = "Connection to Gemini succeeded: model replied successfully";
                return true;
            } else {
                lastTestMessage = "Gemini returned empty response";
                return false;
            }
        } catch (Exception e) {
            log.warn("Gemini connection test failed: {}", e.getMessage());
            lastTestMessage = "Connection failed: " + e.getMessage();
            return false;
        }
    }

    @Override
    public String getTestMessage() {
        return lastTestMessage;
    }
}
