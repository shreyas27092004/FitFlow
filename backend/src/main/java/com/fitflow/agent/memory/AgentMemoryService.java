package com.fitflow.agent.memory;

import com.fitflow.agent.entity.AgentConversation;
import com.fitflow.agent.entity.AgentMessage;
import com.fitflow.agent.port.ChatMessage;
import com.fitflow.agent.repository.AgentConversationRepository;
import com.fitflow.agent.repository.AgentMessageRepository;
import com.fitflow.common.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentMemoryService {

    public static final int CONTEXT_MESSAGE_LIMIT = 12;

    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final Clock clock;

    @Transactional("postgresTransactionManager")
    public AgentConversation getOrCreateConversation(Long conversationId, Long userId, String role) {
        Instant now = clock.instant();

        if (conversationId != null && conversationId > 0) {
            AgentConversation conv = conversationRepository.findByIdAndUserId(conversationId, userId)
                    .orElseThrow(() -> ApiException.notFound("Conversation not found with id: " + conversationId));
            conv.setLastActiveAt(now);
            return conversationRepository.save(conv);
        }

        AgentConversation newConv = AgentConversation.builder()
                .userId(userId)
                .role(role != null ? role : "MEMBER")
                .createdAt(now)
                .lastActiveAt(now)
                .build();

        return conversationRepository.save(newConv);
    }

    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public List<ChatMessage> getRecentContext(Long conversationId) {
        List<AgentMessage> recentDesc = messageRepository.findTop12ByConversationIdOrderByCreatedAtDesc(conversationId);
        List<AgentMessage> chronological = new ArrayList<>(recentDesc);
        Collections.reverse(chronological);

        List<ChatMessage> result = new ArrayList<>();
        for (AgentMessage m : chronological) {
            if ("USER".equalsIgnoreCase(m.getSender())) {
                result.add(ChatMessage.user(m.getContent()));
            } else if ("ASSISTANT".equalsIgnoreCase(m.getSender())) {
                result.add(ChatMessage.assistant(m.getContent()));
            }
        }
        return result;
    }

    @Transactional("postgresTransactionManager")
    public void recordUserMessage(Long conversationId, String content) {
        AgentMessage msg = AgentMessage.builder()
                .conversationId(conversationId)
                .sender("USER")
                .content(content)
                .createdAt(clock.instant())
                .build();
        messageRepository.save(msg);
    }

    @Transactional("postgresTransactionManager")
    public void recordAssistantMessage(Long conversationId, String content) {
        AgentMessage msg = AgentMessage.builder()
                .conversationId(conversationId)
                .sender("ASSISTANT")
                .content(content)
                .createdAt(clock.instant())
                .build();
        messageRepository.save(msg);
    }

    @Transactional("postgresTransactionManager")
    public void deleteConversation(Long conversationId, Long userId) {
        AgentConversation conv = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> ApiException.notFound("Conversation not found with id: " + conversationId));
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.delete(conv);
    }
}
