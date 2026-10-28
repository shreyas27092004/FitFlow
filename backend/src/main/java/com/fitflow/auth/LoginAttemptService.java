package com.fitflow.auth;

import com.fitflow.common.error.ApiException;
import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    private static final int RATE_LIMIT_CAPACITY = 10;
    private static final Duration RATE_LIMIT_PERIOD = Duration.ofMinutes(1);

    private final UserRepository userRepository;
    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public void checkRateLimit(String ip, String email) {
        String safeIp = (ip != null && !ip.isBlank()) ? ip.trim() : "unknown-ip";
        String safeEmail = (email != null && !email.isBlank()) ? email.trim().toLowerCase() : "unknown-email";
        String key = safeIp + ":" + safeEmail;

        Bucket bucket = buckets.computeIfAbsent(key, k -> createNewBucket());
        if (!bucket.tryConsume(1)) {
            log.warn("Login rate limit exceeded for key {}", key);
            throw ApiException.rateLimited("Too many login attempts. Please wait before trying again.");
        }
    }

    public void checkLockout(User user) {
        if (user == null || user.getLockedUntil() == null) {
            return;
        }

        Instant now = clock.instant();
        if (user.getLockedUntil().isAfter(now)) {
            long retryAfterSeconds = Math.max(1, Duration.between(now, user.getLockedUntil()).getSeconds());
            log.warn("Login attempted on locked user id {}; retry-after: {}s", user.getId(), retryAfterSeconds);
            throw ApiException.locked(
                    "Account is temporarily locked due to multiple failed login attempts.",
                    retryAfterSeconds
            );
        }

        // Lock expired: reset
        user.setLockedUntil(null);
        user.setFailedLoginCount(0);
        userRepository.save(user);
    }

    @Transactional
    public void onLoginFailed(User user) {
        if (user == null) {
            return;
        }

        int failedCount = (user.getFailedLoginCount() != null ? user.getFailedLoginCount() : 0) + 1;
        user.setFailedLoginCount(failedCount);

        if (failedCount >= MAX_FAILED_ATTEMPTS) {
            Instant lockedUntil = clock.instant().plus(LOCKOUT_DURATION);
            user.setLockedUntil(lockedUntil);
            log.warn("User {} locked until {} after {} failed login attempts", user.getId(), lockedUntil, failedCount);
        }

        userRepository.save(user);
    }

    @Transactional
    public void onLoginSuccess(User user) {
        if (user == null) {
            return;
        }
        if ((user.getFailedLoginCount() != null && user.getFailedLoginCount() > 0) || user.getLockedUntil() != null) {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }

    public void clearRateLimitBuckets() {
        buckets.clear();
    }

    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(RATE_LIMIT_CAPACITY)
                .refillGreedy(RATE_LIMIT_CAPACITY, RATE_LIMIT_PERIOD)
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
