package com.fitflow.agent.port;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public class ScriptedChatModel implements ChatModelPort {

    private final Queue<ChatResponse> responseQueue = new ConcurrentLinkedQueue<>();
    private final List<ChatRequest> recordedRequests = new CopyOnWriteArrayList<>();

    public ScriptedChatModel queueResponse(String text) {
        responseQueue.add(new ChatResponse(text, List.of(), "STOP"));
        return this;
    }

    public ScriptedChatModel queueToolCall(String id, String toolName, String argumentsJson) {
        responseQueue.add(new ChatResponse("", List.of(new ToolCall(id, toolName, argumentsJson)), "TOOL_CALLS"));
        return this;
    }

    public ScriptedChatModel queueResponse(ChatResponse response) {
        responseQueue.add(response);
        return this;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        recordedRequests.add(request);
        ChatResponse queued = responseQueue.poll();
        if (queued != null) {
            return queued;
        }
        String lastMsg = request.messages().isEmpty() ? "" : request.messages().get(request.messages().size() - 1).content();
        return new ChatResponse("Default scripted reply to: " + lastMsg, List.of(), "STOP");
    }

    public List<ChatRequest> getRecordedRequests() {
        return new ArrayList<>(recordedRequests);
    }

    public List<String> getAllRecordedTexts() {
        List<String> texts = new ArrayList<>();
        for (ChatRequest req : recordedRequests) {
            for (ChatMessage msg : req.messages()) {
                if (msg.content() != null) {
                    texts.add(msg.content());
                }
            }
        }
        return texts;
    }

    public boolean anyPromptContains(String substring) {
        for (String text : getAllRecordedTexts()) {
            if (text != null && text.contains(substring)) {
                return true;
            }
        }
        return false;
    }

    public void reset() {
        responseQueue.clear();
        recordedRequests.clear();
    }
}
