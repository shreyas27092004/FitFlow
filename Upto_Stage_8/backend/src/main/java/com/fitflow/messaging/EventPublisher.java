package com.fitflow.messaging;

public interface EventPublisher {

    void publish(EventEnvelope envelope);
}
