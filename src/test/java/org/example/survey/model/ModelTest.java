package org.example.survey.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTest {

    @Test
    void questionTypes() {
        Question question = new Question();
        assertThat(question.isSingle()).isTrue();
        assertThat(question.isChoice()).isTrue();
        assertThat(question.isGraded()).isFalse();

        question.setCorrectOptionId(5);
        assertThat(question.isGraded()).isTrue();

        question.setType("TEXT");
        assertThat(question.isText()).isTrue();
        assertThat(question.isChoice()).isFalse();
        assertThat(question.isGraded()).isFalse();
    }

    @Test
    void surveyOpenState() {
        Survey survey = new Survey();
        survey.setActive(true);
        assertThat(survey.isOpen()).isTrue();
        assertThat(survey.isPublic()).isTrue();

        survey.setEndsAt(LocalDateTime.now().minusDays(1));
        assertThat(survey.isExpired()).isTrue();
        assertThat(survey.isOpen()).isFalse();

        survey.setEndsAt(LocalDateTime.now().plusDays(1));
        survey.setActive(false);
        assertThat(survey.isOpen()).isFalse();

        survey.setAccessUsers("LIST");
        assertThat(survey.isPublic()).isFalse();
    }

    @Test
    void pageNavigation() {
        Page page = new Page();
        page.setPage(1);
        page.setTotalPages(3);
        assertThat(page.isHasPrevious()).isFalse();
        assertThat(page.isHasNext()).isTrue();
        page.setPage(3);
        assertThat(page.isHasNext()).isFalse();
    }
}
