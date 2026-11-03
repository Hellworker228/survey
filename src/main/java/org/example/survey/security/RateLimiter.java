package org.example.survey.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int maxHits, Duration window) {
        if (hits.size() > 10000) {
            Instant cutoff = Instant.now().minus(Duration.ofDays(1));
            hits.values().removeIf(d -> {
                synchronized (d) {
                    return d.isEmpty() || d.peekLast().isBefore(cutoff);
                }
            });
        }
        Deque<Instant> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            Instant now = Instant.now();
            Instant threshold = now.minus(window);
            while (!deque.isEmpty() && deque.peekFirst().isBefore(threshold)) {
                deque.pollFirst();
            }
            if (deque.size() >= maxHits) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }
}
