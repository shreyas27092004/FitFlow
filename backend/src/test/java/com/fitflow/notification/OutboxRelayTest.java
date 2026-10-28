package com.fitflow.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.messaging.EventEnvelope;
import com.fitflow.messaging.EventPublisher;
import com.fitflow.notification.entity.OutboxEvent;
import com.fitflow.notification.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private OutboxEventRepository outboxEventRepository;
    private EventPublisher eventPublisher;
    private ObjectMapper objectMapper;
    private Clock clock;
    private SimpleMeterRegistry meterRegistry;
    private OutboxRelayService relayService;

    private final Instant baseTime = Instant.parse("2026-10-28T10:00:00Z");

    @BeforeEach
    void setUp() {
        outboxEventRepository = mock(OutboxEventRepository.class);
        eventPublisher = mock(EventPublisher.class);
        objectMapper = new ObjectMapper();
        clock = Clock.fixed(baseTime, ZoneOffset.UTC);
        meterRegistry = new SimpleMeterRegistry();

        relayService = new OutboxRelayService(
                outboxEventRepository,
                eventPublisher,
                objectMapper,
                clock,
                meterRegistry
        );
        relayService.initCounters();
    }

    @Test
    @DisplayName("Publishes PENDING rows in id order and marks SENT with payload_secret scrubbed")
    void testPublishesPendingInIdOrderAndScrubsSecret() {
        OutboxEvent event1 = OutboxEvent.builder()
                .id(101L)
                .aggregateType("USER")
                .aggregateId(1L)
                .eventType("user.created")
                .payload("{\"userId\":1}")
                .payloadSecret("encrypted-secret-token")
                .status(OutboxEvent.Status.PENDING)
                .attempts(0)
                .createdAt(baseTime)
                .nextAttemptAt(baseTime)
                .build();

        OutboxEvent event2 = OutboxEvent.builder()
                .id(102L)
                .aggregateType("BOOKING")
                .aggregateId(2L)
                .eventType("booking.created")
                .payload("{\"bookingId\":2}")
                .payloadSecret(null)
                .status(OutboxEvent.Status.PENDING)
                .attempts(0)
                .createdAt(baseTime)
                .nextAttemptAt(baseTime)
                .build();

        when(outboxEventRepository.findPendingForRelay(eq(baseTime), eq(50)))
                .thenReturn(new ArrayList<>(List.of(event1, event2)));

        int processed = relayService.pollAndRelayBatch(50);

        assertEquals(2, processed);
        assertEquals(OutboxEvent.Status.SENT, event1.getStatus());
        assertEquals(baseTime, event1.getSentAt());
        assertNull(event1.getPayloadSecret(), "payload_secret must be scrubbed (null) after SENT");
        assertNull(event1.getLastError());

        assertEquals(OutboxEvent.Status.SENT, event2.getStatus());
        assertNull(event2.getPayloadSecret());

        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(eventPublisher, times(2)).publish(captor.capture());

        List<EventEnvelope> published = captor.getAllValues();
        assertEquals(101L, published.get(0).eventId());
        assertEquals("user.created", published.get(0).eventType());
        assertEquals("encrypted-secret-token", published.get(0).enc());

        assertEquals(102L, published.get(1).eventId());
        assertEquals("booking.created", published.get(1).eventType());

        assertEquals(2.0, meterRegistry.counter("outbox.published").count());
    }

    @Test
    @DisplayName("Failing publisher leaves rows PENDING, updates attempts, and calculates exponential backoff")
    void testPublisherFailureCalculatesBackoff() {
        OutboxEvent event = OutboxEvent.builder()
                .id(201L)
                .aggregateType("USER")
                .aggregateId(1L)
                .eventType("user.created")
                .payload("{\"userId\":1}")
                .payloadSecret("secret-value")
                .status(OutboxEvent.Status.PENDING)
                .attempts(1)
                .createdAt(baseTime)
                .nextAttemptAt(baseTime)
                .build();

        when(outboxEventRepository.findPendingForRelay(eq(baseTime), eq(50)))
                .thenReturn(new ArrayList<>(List.of(event)));

        doThrow(new RuntimeException("RabbitMQ connection dropped"))
                .when(eventPublisher).publish(any());

        relayService.pollAndRelayBatch(50);

        assertEquals(OutboxEvent.Status.PENDING, event.getStatus());
        assertEquals(2, event.getAttempts());
        assertNotNull(event.getLastError());
        assertTrue(event.getLastError().contains("RabbitMQ connection dropped"));
        assertEquals("secret-value", event.getPayloadSecret(), "Secret should not be scrubbed if send failed");

        // Backoff for attempt 2: 2^2 = 4 seconds
        Instant expectedNext = baseTime.plusSeconds(4);
        assertEquals(expectedNext, event.getNextAttemptAt());
        assertEquals(1.0, meterRegistry.counter("outbox.failed").count());
    }

    @Test
    @DisplayName("Marked FAILED after 10 attempts")
    void testMarkedFailedAfterTenAttempts() {
        OutboxEvent event = OutboxEvent.builder()
                .id(301L)
                .aggregateType("USER")
                .aggregateId(1L)
                .eventType("user.created")
                .payload("{\"userId\":1}")
                .status(OutboxEvent.Status.PENDING)
                .attempts(9)
                .createdAt(baseTime)
                .nextAttemptAt(baseTime)
                .build();

        when(outboxEventRepository.findPendingForRelay(eq(baseTime), eq(50)))
                .thenReturn(new ArrayList<>(List.of(event)));

        doThrow(new RuntimeException("Persistent network failure"))
                .when(eventPublisher).publish(any());

        relayService.pollAndRelayBatch(50);

        assertEquals(OutboxEvent.Status.FAILED, event.getStatus());
        assertEquals(10, event.getAttempts());
        assertTrue(event.getLastError().contains("Persistent network failure"));
    }

    @Test
    @DisplayName("4 concurrent relays publish each of 500 rows exactly once")
    void testConcurrentRelaysPublishEachRowExactlyOnce() throws Exception {
        int totalRows = 500;
        List<OutboxEvent> allEvents = new ArrayList<>();
        for (long i = 1; i <= totalRows; i++) {
            allEvents.add(OutboxEvent.builder()
                    .id(i)
                    .aggregateType("SESSION")
                    .aggregateId(i)
                    .eventType("session.reminder")
                    .payload("{\"sessionId\":" + i + "}")
                    .status(OutboxEvent.Status.PENDING)
                    .attempts(0)
                    .createdAt(baseTime)
                    .nextAttemptAt(baseTime)
                    .build());
        }

        // Simulate database with FOR UPDATE SKIP LOCKED
        AtomicInteger cursor = new AtomicInteger(0);
        when(outboxEventRepository.findPendingForRelay(any(), any(int.class))).thenAnswer(invocation -> {
            int limit = invocation.getArgument(1);
            List<OutboxEvent> batch = new ArrayList<>();
            synchronized (allEvents) {
                int start = cursor.get();
                int end = Math.min(start + limit, allEvents.size());
                if (start < end) {
                    batch.addAll(allEvents.subList(start, end));
                    cursor.set(end);
                }
            }
            return batch;
        });

        Map<Long, AtomicInteger> publishCounts = new ConcurrentHashMap<>();
        doAnswer(invocation -> {
            EventEnvelope env = invocation.getArgument(0);
            publishCounts.computeIfAbsent(env.eventId(), k -> new AtomicInteger(0)).incrementAndGet();
            return null;
        }).when(eventPublisher).publish(any());

        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> {
                int totalHandled = 0;
                while (true) {
                    int handled = relayService.pollAndRelayBatch(50);
                    if (handled == 0) break;
                    totalHandled += handled;
                }
                return totalHandled;
            });
        }

        List<Future<Integer>> futures = pool.invokeAll(tasks);
        int grandTotal = 0;
        for (Future<Integer> f : futures) {
            grandTotal += f.get();
        }
        pool.shutdown();

        assertEquals(500, grandTotal);
        assertEquals(500, publishCounts.size());
        for (long i = 1; i <= totalRows; i++) {
            assertNotNull(publishCounts.get(i), "Row " + i + " must have been published");
            assertEquals(1, publishCounts.get(i).get(), "Row " + i + " must be published exactly once");
        }
    }
}
