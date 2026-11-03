package org.example.survey.api;

import org.example.survey.api.ApiDtos.QuestionDto;
import org.example.survey.api.ApiDtos.SurveyDto;
import org.example.survey.model.Question;
import org.example.survey.model.Survey;
import org.example.survey.model.SurveyOption;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiMapperTest {

    private final ApiMapper mapper = new ApiMapper();

    private Question question() {
        Question question = new Question();
        question.setId(7);
        question.setContent("Q");
        SurveyOption option = new SurveyOption();
        option.setId(70);
        option.setContent("A");
        question.getOptions().add(option);
        question.setCorrectOptionId(70);
        return question;
    }

    @Test
    void hidesCorrectAnswerFromParticipants() {
        QuestionDto hidden = mapper.question(question(), false);
        QuestionDto visible = mapper.question(question(), true);
        assertThat(hidden.correctOptionId()).isNull();
        assertThat(visible.correctOptionId()).isEqualTo(70);
        assertThat(hidden.options()).hasSize(1);
    }

    @Test
    void exposesTokenForAnonymousSurveyOnly() {
        Survey survey = new Survey();
        survey.setId(1);
        survey.setAnonymous(true);
        survey.setPublicToken("tok");
        SurveyDto dto = mapper.survey(survey, List.of(), false);
        assertThat(dto.publicToken()).isEqualTo("tok");
        assertThat(dto.publicPath()).isEqualTo("/a/tok");

        Survey regular = new Survey();
        regular.setPublicToken("tok");
        assertThat(mapper.survey(regular, null, true).publicToken()).isNull();
        assertThat(mapper.survey(regular, null, true).questions()).isNull();
    }
}
