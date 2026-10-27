package com.fitflow.notification.repository;

import com.fitflow.notification.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByEventType(String eventType);

    List<OutboxEvent> findByAggregateIdAndEventType(Long aggregateId, String eventType);
}
