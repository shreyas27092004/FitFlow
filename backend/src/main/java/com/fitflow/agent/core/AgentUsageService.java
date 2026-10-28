package com.fitflow.agent.core;

import com.fitflow.agent.entity.AgentUsage;
import com.fitflow.agent.repository.AgentUsageRepository;
import com.fitflow.common.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentUsageService {

    private final AgentUsageRepository usageRepository;
    private final Clock clock;

    @Value("${fitflow.agent.daily-call-limit:200}")
    private int dailyCallLimit;

    @Value("${fitflow.agent.per-minute-limit:60}")
    private int perMinuteLimit;

    // Per-user sliding minute window timestamp queue
    private final Map<Long, Deque<Long>> userMinuteTimestamps = new ConcurrentHashMap<>();

    @Transactional("postgresTransactionManager")
    public void checkAndIncrementUsage(Long userId) {
        long nowMillis = clock.millis();

        // 1. Check per-minute rate limit
        Deque<Long> queue = userMinuteTimestamps.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (queue) {
            long windowStart = nowMillis - 60_000L;
            while (!queue.isEmpty() && queue.peekFirst() < windowStart) {
                queue.pollFirst();
            }

            if (queue.size() >= perMinuteLimit) {
                log.warn("Rate limit exceeded (per-minute) for user id={}: count={}", userId, queue.size());
                throw ApiException.rateLimited("Rate limit exceeded: maximum " + perMinuteLimit + " requests per minute");
            }
            queue.addLast(nowMillis);
        }

        // 2. Check daily budget in PostgreSQL
        LocalDate today = LocalDate.now(clock);
        AgentUsage usage = usageRepository.findByUserIdAndDay(userId, today).orElse(null);
        int currentCalls = usage != null ? usage.getCalls() : 0;

        if (currentCalls >= dailyCallLimit) {
            log.warn("Daily usage budget exceeded for user id={}: current={}, limit={}", userId, currentCalls, dailyCallLimit);
            throw ApiException.rateLimited("Daily AI usage limit reached (" + dailyCallLimit + " requests per day)");
        }

        usageRepository.incrementUsage(userId, today);
    }
}
