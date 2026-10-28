package com.fitflow.notification.repository;

import com.fitflow.notification.entity.OutboxEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByEventType(String eventType);

    List<OutboxEvent> findByAggregateIdAndEventType(Long aggregateId, String eventType);

    @Query(value = "SELECT * FROM outbox_events WHERE status = 'PENDING' AND next_attempt_at <= :now ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> findPendingForRelay(@Param("now") Instant now, @Param("limit") int limit);

    default List<OutboxEvent> findPendingForRelay(Instant now) {
        return findPendingForRelay(now, 50);
    }

    Page<OutboxEvent> findByStatus(OutboxEvent.Status status, Pageable pageable);

    Optional<OutboxEvent> findTopByStatusOrderByCreatedAtAsc(OutboxEvent.Status status);

    long countByStatus(OutboxEvent.Status status);
}
