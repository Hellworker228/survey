package org.example.survey.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    @Test
    void blocksAfterLimit() {
        RateLimiter limiter = new RateLimiter();
        assertThat(limiter.tryAcquire("k", 2, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.tryAcquire("k", 2, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.tryAcquire("k", 2, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void keysAreIndependent() {
        RateLimiter limiter = new RateLimiter();
        assertThat(limiter.tryAcquire("a", 1, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.tryAcquire("b", 1, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.tryAcquire("a", 1, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void windowExpires() throws InterruptedException {
        RateLimiter limiter = new RateLimiter();
        assertThat(limiter.tryAcquire("k", 1, Duration.ofMillis(50))).isTrue();
        assertThat(limiter.tryAcquire("k", 1, Duration.ofMillis(50))).isFalse();
        Thread.sleep(80);
        assertThat(limiter.tryAcquire("k", 1, Duration.ofMillis(50))).isTrue();
    }
}
