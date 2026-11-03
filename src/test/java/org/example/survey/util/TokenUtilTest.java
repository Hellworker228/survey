package org.example.survey.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenUtilTest {

    @Test
    void generatesDifferentUrlSafeTokens() {
        String first = TokenUtil.generate(32);
        String second = TokenUtil.generate(32);
        assertThat(first).isNotEqualTo(second);
        assertThat(first).matches("[A-Za-z0-9_-]+");
        assertThat(first.length()).isGreaterThanOrEqualTo(43);
    }

    @Test
    void hashIsStableAndHex() {
        assertThat(TokenUtil.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(TokenUtil.sha256("abc")).isEqualTo(TokenUtil.sha256("abc"));
        assertThat(TokenUtil.sha256("abc")).isNotEqualTo(TokenUtil.sha256("abd"));
    }
}
