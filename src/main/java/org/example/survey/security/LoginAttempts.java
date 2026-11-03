package org.example.survey.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginAttempts {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK = Duration.ofMinutes(5);

    private record State(int failures, Instant lockedUntil) {
    }

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    public boolean isLocked(String key) {
        State state = states.get(key);
        if (state == null || state.lockedUntil() == null) {
            return false;
        }
        if (state.lockedUntil().isBefore(Instant.now())) {
            states.remove(key);
            return false;
        }
        return true;
    }

    public void failed(String key) {
        states.merge(key, new State(1, null), (old, ignored) -> {
            int failures = old.failures() + 1;
            return failures >= MAX_FAILURES ? new State(failures, Instant.now().plus(LOCK)) : new State(failures, null);
        });
    }

    public void succeeded(String key) {
        states.remove(key);
    }
}
