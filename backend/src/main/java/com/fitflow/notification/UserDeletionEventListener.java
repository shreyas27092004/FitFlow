package com.fitflow.notification;

import com.fitflow.common.event.UserDeletionRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeletionEventListener {

    private final OutboxService outboxService;

    @EventListener
    public void onUserDeletionRequested(UserDeletionRequestedEvent event) {
        log.info("Received UserDeletionRequestedEvent for user id {}", event.userId());
        outboxService.append("USER", event.userId(), "user.deletion_requested", Map.of("userId", event.userId()));
    }
}
