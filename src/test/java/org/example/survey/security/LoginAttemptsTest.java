package org.example.survey.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptsTest {

    @Test
    void locksAfterFiveFailures() {
        LoginAttempts attempts = new LoginAttempts();
        for (int i = 0; i < 4; i++) {
            attempts.failed("user|127.0.0.1");
        }
        assertThat(attempts.isLocked("user|127.0.0.1")).isFalse();
        attempts.failed("user|127.0.0.1");
        assertThat(attempts.isLocked("user|127.0.0.1")).isTrue();
    }

    @Test
    void successResetsCounter() {
        LoginAttempts attempts = new LoginAttempts();
        for (int i = 0; i < 5; i++) {
            attempts.failed("user|127.0.0.1");
        }
        attempts.succeeded("user|127.0.0.1");
        assertThat(attempts.isLocked("user|127.0.0.1")).isFalse();
    }

    @Test
    void keysAreIndependent() {
        LoginAttempts attempts = new LoginAttempts();
        for (int i = 0; i < 5; i++) {
            attempts.failed("a|1");
        }
        assertThat(attempts.isLocked("b|1")).isFalse();
    }
}
