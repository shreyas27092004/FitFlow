package com.fitflow.agent.repository;

import com.fitflow.agent.entity.PendingAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PendingActionRepository extends JpaRepository<PendingAction, Long> {
    Optional<PendingAction> findByIdAndUserId(Long id, Long userId);
    List<PendingAction> findByUserIdAndStatus(Long userId, String status);
    List<PendingAction> findByConversationId(Long conversationId);
}
