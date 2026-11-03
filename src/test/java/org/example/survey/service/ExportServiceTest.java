package org.example.survey.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExportServiceTest {

    @Test
    void escapesFormulaPrefixes() {
        assertThat(ExportService.safe("=SUM(A1:A2)")).isEqualTo("'=SUM(A1:A2)");
        assertThat(ExportService.safe("+1")).isEqualTo("'+1");
        assertThat(ExportService.safe("@cmd")).isEqualTo("'@cmd");
    }

    @Test
    void keepsRegularText() {
        assertThat(ExportService.safe("Обычный ответ")).isEqualTo("Обычный ответ");
        assertThat(ExportService.safe(null)).isEmpty();
    }
}
