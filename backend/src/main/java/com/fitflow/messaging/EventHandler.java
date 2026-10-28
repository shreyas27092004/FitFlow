package com.fitflow.messaging;

public interface EventHandler {

    boolean supports(String eventType);

    void handle(EventEnvelope envelope);
}
