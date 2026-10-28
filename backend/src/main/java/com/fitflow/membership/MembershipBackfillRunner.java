package com.fitflow.membership;

import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class MembershipBackfillRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final MembershipService membershipService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Running MembershipBackfillRunner: Checking all active users for missing FREE tier membership...");
        try {
            List<User> users = userRepository.findAll();
            int backfilled = 0;
            for (User user : users) {
                if (user.getStatus() == User.Status.ACTIVE) {
                    try {
                        membershipService.assignFreeMembershipIfMissing(user.getId());
                        backfilled++;
                    } catch (Exception ex) {
                        log.warn("Could not assign free tier membership to user id {}: {}", user.getId(), ex.getMessage());
                    }
                }
            }
            log.info("MembershipBackfillRunner complete. Checked {} users.", backfilled);
        } catch (Exception e) {
            log.warn("MembershipBackfillRunner skipped or failed (e.g. database not initialized): {}", e.getMessage());
        }
    }
}
