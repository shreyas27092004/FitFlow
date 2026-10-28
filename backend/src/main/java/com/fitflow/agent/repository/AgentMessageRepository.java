package com.fitflow.agent.repository;

import com.fitflow.agent.entity.AgentMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentMessageRepository extends JpaRepository<AgentMessage, Long> {
    List<AgentMessage> findTop12ByConversationIdOrderByCreatedAtDesc(Long conversationId);
    List<AgentMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
    void deleteByConversationId(Long conversationId);
}
