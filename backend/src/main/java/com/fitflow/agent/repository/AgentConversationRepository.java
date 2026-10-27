package com.fitflow.agent.repository;

import com.fitflow.agent.entity.AgentConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentConversationRepository extends JpaRepository<AgentConversation, Long> {
}
