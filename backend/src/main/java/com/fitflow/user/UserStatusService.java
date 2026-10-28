package com.fitflow.user;

import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class UserStatusService {

    private record CachedStatus(User.Status status, Instant expiresAt) {}

    private final UserRepository userRepository;
    private final Clock clock;
    private final ConcurrentHashMap<Long, CachedStatus> statusCache = new ConcurrentHashMap<>();

    public boolean isActive(Long userId) {
        Instant now = clock.instant();
        CachedStatus cached = statusCache.get(userId);

        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.status() == User.Status.ACTIVE;
        }

        User user = userRepository.findById(userId).orElse(null);
        User.Status status = user != null ? user.getStatus() : null;

        // Cache for at most 30 seconds
        statusCache.put(userId, new CachedStatus(status, now.plusSeconds(30)));
        return status == User.Status.ACTIVE;
    }

    public void invalidate(Long userId) {
        statusCache.remove(userId);
    }

    public void clearCache() {
        statusCache.clear();
    }
}
