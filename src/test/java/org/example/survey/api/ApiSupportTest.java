package org.example.survey.api;

import org.example.survey.api.ApiDtos.AnswerRequest;
import org.example.survey.api.ApiDtos.SubmitRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiSupportTest {

    private final ApiSupport support = new ApiSupport(null, null, null);

    @Test
    void convertsAnswersToFormParameters() {
        SubmitRequest request = new SubmitRequest(List.of(
                new AnswerRequest(1, List.of(10), null),
                new AnswerRequest(2, List.of(20, 21), null),
                new AnswerRequest(3, null, "free text")));
        Map<String, String[]> params = support.submitParams(request);
        assertThat(params.get("q_1")).containsExactly("10");
        assertThat(params.get("q_2")).containsExactly("20", "21");
        assertThat(params.get("q_3")).containsExactly("free text");
    }

    @Test
    void rejectsMissingAnswers() {
        assertThatThrownBy(() -> support.submitParams(null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> support.submitParams(new SubmitRequest(null))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> support.submitParams(new SubmitRequest(List.of(new AnswerRequest(null, null, "x")))))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void parsesDates() {
        assertThat(support.parseDateTime(null)).isNull();
        assertThat(support.parseDateTime(" ")).isNull();
        assertThat(support.parseDateTime("2030-12-31T23:59")).isEqualTo(LocalDateTime.of(2030, 12, 31, 23, 59));
        assertThatThrownBy(() -> support.parseDateTime("tomorrow")).isInstanceOf(ApiException.class);
    }
}
