package com.fitflow.membership;

import com.fitflow.auth.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class MembershipRegistrationListener {

    private final MembershipService membershipService;

    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent for userId={}. Assigning free tier membership if missing.", event.userId());
        try {
            membershipService.assignFreeMembershipIfMissing(event.userId());
        } catch (Exception e) {
            log.error("Failed to assign default free tier membership to userId={}: {}", event.userId(), e.getMessage(), e);
        }
    }
}
