package org.example.survey.api;

import org.example.survey.api.ApiDtos.OptionDto;
import org.example.survey.api.ApiDtos.OptionStatDto;
import org.example.survey.api.ApiDtos.QuestionDto;
import org.example.survey.api.ApiDtos.QuestionResultDto;
import org.example.survey.api.ApiDtos.QuestionStatDto;
import org.example.survey.api.ApiDtos.ResultDto;
import org.example.survey.api.ApiDtos.ResultSummaryDto;
import org.example.survey.api.ApiDtos.StatsDto;
import org.example.survey.api.ApiDtos.SurveyDto;
import org.example.survey.api.ApiDtos.UserDto;
import org.example.survey.model.Question;
import org.example.survey.model.QuestionResult;
import org.example.survey.model.QuestionStats;
import org.example.survey.model.Survey;
import org.example.survey.model.SurveyOption;
import org.example.survey.model.SurveyResult;
import org.example.survey.model.SurveyStats;
import org.example.survey.model.User;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class ApiMapper {

    private static String iso(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    public UserDto user(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getEmail(), user.isAdmin(), user.isEmailVerified());
    }

    public QuestionDto question(Question question, boolean includeCorrect) {
        List<OptionDto> options = new ArrayList<>();
        for (SurveyOption option : question.getOptions()) {
            options.add(new OptionDto(option.getId(), option.getContent()));
        }
        return new QuestionDto(question.getId(), question.getContent(), question.getType(), question.isRequired(),
                options, includeCorrect ? question.getCorrectOptionId() : null);
    }

    public SurveyDto survey(Survey survey, List<Question> questions, boolean manager) {
        List<QuestionDto> questionDtos = null;
        if (questions != null) {
            questionDtos = new ArrayList<>();
            for (Question question : questions) {
                questionDtos.add(question(question, manager));
            }
        }
        boolean showToken = manager || survey.isAnonymous();
        return new SurveyDto(survey.getId(), survey.getTitle(), survey.getDescription(), survey.getCreatorName(),
                survey.isActive(), survey.isOpen(), survey.isAnonymous(), survey.isPublic(), iso(survey.getEndsAt()),
                survey.getQuestionsCount(), survey.getResponsesCount(), survey.isCompleted(),
                showToken && survey.isAnonymous() ? survey.getPublicToken() : null,
                survey.isAnonymous() && survey.getPublicToken() != null ? "/a/" + survey.getPublicToken() : null,
                questionDtos);
    }

    public ResultSummaryDto summary(SurveyResult result) {
        return new ResultSummaryDto(result.getSurveyId(), result.getSurveyTitle(), iso(result.getSubmittedAt()),
                result.getPercent(), result.getCorrectCount(), result.getGradedCount());
    }

    public ResultDto result(SurveyResult result, List<QuestionResult> questionResults) {
        List<QuestionResultDto> items = new ArrayList<>();
        for (QuestionResult item : questionResults) {
            items.add(new QuestionResultDto(item.getQuestion().getId(), item.getQuestion().getContent(),
                    item.getChosen(), item.getText(), item.getCorrect(), item.getCorrectText()));
        }
        return new ResultDto(result.getSurveyId(), result.getSurveyTitle(), iso(result.getSubmittedAt()),
                result.getPercent(), result.getCorrectCount(), result.getGradedCount(), items);
    }

    public StatsDto stats(SurveyStats stats) {
        List<QuestionStatDto> questions = new ArrayList<>();
        for (QuestionStats item : stats.getQuestions()) {
            Question question = item.getQuestion();
            List<OptionStatDto> options = new ArrayList<>();
            for (SurveyOption option : question.getOptions()) {
                boolean correct = question.getCorrectOptionId() != null && question.getCorrectOptionId() == option.getId();
                options.add(new OptionStatDto(option.getId(), option.getContent(), option.getAnswersCount(),
                        option.getPercent(), correct));
            }
            questions.add(new QuestionStatDto(question.getId(), question.getContent(), question.getType(),
                    item.getAnswered(), options, item.getTexts()));
        }
        return new StatsDto(stats.getSurvey().getId(), stats.getSurvey().getTitle(), stats.getResponsesCount(),
                stats.getAveragePercent(), questions);
    }
}
