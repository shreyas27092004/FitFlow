package com.fitflow.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.admin.dto.DlqReplayResponse;
import com.fitflow.admin.dto.OutboxEventSummaryResponse;
import com.fitflow.admin.dto.QueueDepthResponse;
import com.fitflow.common.error.ApiException;
import com.fitflow.common.pagination.PageResponse;
import com.fitflow.messaging.InMemoryEventDispatcher;
import com.fitflow.messaging.RabbitMqTopologyConfig;
import com.fitflow.notification.entity.OutboxEvent;
import com.fitflow.notification.repository.OutboxEventRepository;
import com.fitflow.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Service
public class AdminMessagingService {

    private final OutboxEventRepository outboxEventRepository;
    private final AdminActionLogger adminActionLogger;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String messagingMode;

    private final RabbitAdmin rabbitAdmin;
    private final RabbitTemplate rabbitTemplate;
    private final InMemoryEventDispatcher inMemoryDispatcher;

    public AdminMessagingService(
            OutboxEventRepository outboxEventRepository,
            AdminActionLogger adminActionLogger,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${fitflow.messaging.mode:inmemory}") String messagingMode,
            @Autowired(required = false) RabbitAdmin rabbitAdmin,
            @Autowired(required = false) RabbitTemplate rabbitTemplate,
            @Autowired(required = false) InMemoryEventDispatcher inMemoryDispatcher) {
        this.outboxEventRepository = outboxEventRepository;
        this.adminActionLogger = adminActionLogger;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.messagingMode = messagingMode;
        this.rabbitAdmin = rabbitAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.inMemoryDispatcher = inMemoryDispatcher;
    }

    @Transactional(readOnly = true)
    public PageResponse<OutboxEventSummaryResponse> getOutboxEvents(String statusStr, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"));
        Page<OutboxEvent> outboxPage;

        if (statusStr != null && !statusStr.isBlank()) {
            try {
                OutboxEvent.Status status = OutboxEvent.Status.valueOf(statusStr.trim().toUpperCase());
                outboxPage = outboxEventRepository.findByStatus(status, pageRequest);
            } catch (IllegalArgumentException e) {
                throw ApiException.validationFailed("Invalid outbox status: " + statusStr);
            }
        } else {
            outboxPage = outboxEventRepository.findAll(pageRequest);
        }

        return PageResponse.of(outboxPage, OutboxEventSummaryResponse::from);
    }

    @Transactional
    public OutboxEventSummaryResponse retryOutboxEvent(Long id) {
        OutboxEvent event = outboxEventRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Outbox event not found with id: " + id));

        if (event.getStatus() != OutboxEvent.Status.FAILED) {
            throw ApiException.conflict("Only FAILED outbox events can be retried (current status: " + event.getStatus() + ")");
        }

        event.setStatus(OutboxEvent.Status.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(clock.instant());
        event.setLastError(null);

        OutboxEvent saved = outboxEventRepository.save(event);
        log.info("Outbox event id={} reset to PENDING for retry", id);
        return OutboxEventSummaryResponse.from(saved);
    }

    public QueueDepthResponse getQueues() {
        if ("inmemory".equalsIgnoreCase(messagingMode)) {
            int deadLetterCount = inMemoryDispatcher != null ? inMemoryDispatcher.getDeadLetters().size() : 0;
            return new QueueDepthResponse(
                    "inmemory",
                    List.of(
                            new QueueDepthResponse.QueueInfo(RabbitMqTopologyConfig.QUEUE_NOTIFICATIONS_EMAIL, 0, 1),
                            new QueueDepthResponse.QueueInfo(RabbitMqTopologyConfig.QUEUE_NOTIFICATIONS_EMAIL_DLQ, deadLetterCount, 0),
                            new QueueDepthResponse.QueueInfo(RabbitMqTopologyConfig.QUEUE_AGENT_EVENTS, 0, 0),
                            new QueueDepthResponse.QueueInfo(RabbitMqTopologyConfig.QUEUE_AGENT_EVENTS_DLQ, 0, 0)
                    )
            );
        }

        List<String> queueNames = List.of(
                RabbitMqTopologyConfig.QUEUE_NOTIFICATIONS_EMAIL,
                RabbitMqTopologyConfig.QUEUE_NOTIFICATIONS_EMAIL_DLQ,
                RabbitMqTopologyConfig.QUEUE_AGENT_EVENTS,
                RabbitMqTopologyConfig.QUEUE_AGENT_EVENTS_DLQ
        );

        RabbitAdmin admin = this.rabbitAdmin;
        if (admin == null && rabbitTemplate != null) {
            admin = new RabbitAdmin(rabbitTemplate.getConnectionFactory());
        }

        List<QueueDepthResponse.QueueInfo> queueInfos = new ArrayList<>();
        for (String qName : queueNames) {
            long msgCount = 0;
            long consumerCount = 0;
            if (admin != null) {
                try {
                    Properties props = admin.getQueueProperties(qName);
                    if (props != null) {
                        Number msgs = (Number) props.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
                        Number consumers = (Number) props.get(RabbitAdmin.QUEUE_CONSUMER_COUNT);
                        if (msgs != null) msgCount = msgs.longValue();
                        if (consumers != null) consumerCount = consumers.longValue();
                    }
                } catch (Exception e) {
                    log.warn("Could not inspect queue properties for {}: {}", qName, e.getMessage());
                }
            }
            queueInfos.add(new QueueDepthResponse.QueueInfo(qName, msgCount, consumerCount));
        }

        return new QueueDepthResponse("rabbit", queueInfos);
    }

    public DlqReplayResponse replayDlq(UserPrincipal actor, String queueName, int requestedLimit) {
        int limit = Math.min(Math.max(requestedLimit, 1), 100);
        int replayedCount = 0;

        if ("inmemory".equalsIgnoreCase(messagingMode)) {
            if (inMemoryDispatcher != null) {
                var deadLetters = new ArrayList<>(inMemoryDispatcher.getDeadLetters());
                for (var env : deadLetters) {
                    if (replayedCount >= limit) break;
                    try {
                        inMemoryDispatcher.publish(env);
                        replayedCount++;
                    } catch (Exception e) {
                        log.warn("Replay failed for event {}: {}", env.eventId(), e.getMessage());
                    }
                }
            }
        } else if (rabbitTemplate != null) {
            String targetQueue = queueName.endsWith(".dlq")
                    ? queueName.substring(0, queueName.length() - 4)
                    : queueName;

            for (int i = 0; i < limit; i++) {
                Message message = rabbitTemplate.receive(queueName, 1000);
                if (message == null) {
                    break;
                }
                rabbitTemplate.send("", targetQueue, message);
                replayedCount++;
            }
        }

        // Audit action
        adminActionLogger.logAction(
                actor.getId(),
                "DLQ_REPLAY",
                "MESSAGING_QUEUE",
                null,
                null,
                toJson(Map.of("queue", queueName, "replayedCount", replayedCount, "limit", limit)),
                null
        );

        log.info("Admin id={} replayed {} messages from DLQ {}", actor.getId(), replayedCount, queueName);
        return new DlqReplayResponse(queueName, replayedCount);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
