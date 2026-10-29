package com.fitflow.agent.repository;

import com.fitflow.agent.entity.AgentConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentConversationRepository extends JpaRepository<AgentConversation, Long> {
    Optional<AgentConversation> findByIdAndUserId(Long id, Long userId);
    List<AgentConversation> findByUserIdOrderByLastActiveAtDesc(Long userId);
    Page<AgentConversation> findByUserIdOrderByLastActiveAtDesc(Long userId, Pageable pageable);
}
