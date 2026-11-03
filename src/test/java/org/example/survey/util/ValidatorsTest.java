package org.example.survey.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidatorsTest {

    @Test
    void acceptsValidUsername() {
        assertThat(Validators.validateUsername("ivan_petrov")).isNull();
        assertThat(Validators.validateUsername("Иван.Петров")).isNull();
    }

    @Test
    void rejectsInvalidUsername() {
        assertThat(Validators.validateUsername("ab")).isNotNull();
        assertThat(Validators.validateUsername("bad name")).isNotNull();
        assertThat(Validators.validateUsername(null)).isNotNull();
    }

    @Test
    void validatesEmail() {
        assertThat(Validators.validateEmail("user@example.com")).isNull();
        assertThat(Validators.validateEmail("user@example")).isNotNull();
        assertThat(Validators.validateEmail("not-an-email")).isNotNull();
    }

    @Test
    void validatesPasswordLength() {
        assertThat(Validators.validatePassword("secret1")).isNull();
        assertThat(Validators.validatePassword("12345")).isNotNull();
        assertThat(Validators.validatePassword("a".repeat(100))).isNotNull();
    }

    @Test
    void cleanTrimsAndHandlesNull() {
        assertThat(Validators.clean("  text ")).isEqualTo("text");
        assertThat(Validators.clean(null)).isEmpty();
    }
}
