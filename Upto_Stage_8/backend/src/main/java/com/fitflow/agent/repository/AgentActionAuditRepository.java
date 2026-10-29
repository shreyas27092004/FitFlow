package com.fitflow.agent.repository;

import com.fitflow.agent.entity.AgentActionAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentActionAuditRepository extends JpaRepository<AgentActionAudit, Long> {
    List<AgentActionAudit> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<AgentActionAudit> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
}
