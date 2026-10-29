package com.fitflow.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class InMemoryEventDispatcher implements EventPublisher {

    private final List<EventHandler> handlers;
    private final List<EventEnvelope> dispatchedEvents = new CopyOnWriteArrayList<>();
    private final List<EventEnvelope> deadLetters = new CopyOnWriteArrayList<>();
    private volatile boolean simulateBrokerFailure = false;

    public InMemoryEventDispatcher(@Autowired(required = false) List<EventHandler> handlers) {
        this.handlers = handlers != null ? handlers : Collections.emptyList();
    }

    @Override
    public void publish(EventEnvelope envelope) {
        if (simulateBrokerFailure) {
            log.warn("Simulated broker failure for event {}", envelope.eventId());
            throw new RuntimeException("Simulated broker publishing failure");
        }

        dispatchedEvents.add(envelope);
        log.info("InMemoryEventDispatcher dispatched event id={} type={}", envelope.eventId(), envelope.eventType());

        boolean handled = false;
        for (EventHandler handler : handlers) {
            if (handler.supports(envelope.eventType())) {
                handled = true;
                try {
                    handler.handle(envelope);
                } catch (Exception e) {
                    log.error("Error in handler for event {}: {}", envelope.eventId(), e.getMessage(), e);
                    deadLetters.add(envelope);
                    throw e;
                }
            }
        }

        if (!handled) {
            log.debug("No handler found for event type: {}", envelope.eventType());
        }
    }

    public List<EventEnvelope> getDispatchedEvents() {
        return Collections.unmodifiableList(new ArrayList<>(dispatchedEvents));
    }

    public List<EventEnvelope> getDeadLetters() {
        return Collections.unmodifiableList(new ArrayList<>(deadLetters));
    }

    public void clearDispatchedEvents() {
        dispatchedEvents.clear();
        deadLetters.clear();
    }

    public void setSimulateBrokerFailure(boolean simulateBrokerFailure) {
        this.simulateBrokerFailure = simulateBrokerFailure;
    }
}
