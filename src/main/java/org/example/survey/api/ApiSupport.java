package org.example.survey.api;

import jakarta.servlet.http.HttpServletRequest;
import org.example.survey.model.Question;
import org.example.survey.model.Survey;
import org.example.survey.service.AppService;
import org.example.survey.service.QuestionService;
import org.example.survey.service.SurveyService;
import org.springframework.stereotype.Component;

import org.example.survey.api.ApiDtos.AnswerRequest;
import org.example.survey.api.ApiDtos.SubmitRequest;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ApiSupport {

    private final AppService appService;
    private final SurveyService surveyService;
    private final QuestionService questionService;

    public ApiSupport(AppService appService, SurveyService surveyService, QuestionService questionService) {
        this.appService = appService;
        this.surveyService = surveyService;
        this.questionService = questionService;
    }

    public int userId(HttpServletRequest request) {
        Object value = request.getAttribute(ApiAuthInterceptor.USER_ID_ATTR);
        if (value instanceof Integer id) {
            return id;
        }
        throw new ApiException(401, "Требуется авторизация");
    }

    public boolean isAdmin(HttpServletRequest request) {
        return appService.isAdmin(userId(request));
    }

    public Survey requireSurvey(int id) {
        Survey survey = surveyService.getSurveyById(id);
        if (survey == null) {
            throw new ApiException(404, "Анкета не найдена");
        }
        return survey;
    }

    public Survey requireAccessible(int id, HttpServletRequest request) {
        Survey survey = requireSurvey(id);
        if (!surveyService.canAccess(survey, userId(request), isAdmin(request))) {
            throw new ApiException(403, "Нет доступа к анкете");
        }
        return survey;
    }

    public Survey requireManaged(int id, HttpServletRequest request) {
        Survey survey = requireSurvey(id);
        if (!surveyService.canManage(survey, userId(request), isAdmin(request))) {
            throw new ApiException(403, "Нет прав на управление анкетой");
        }
        return survey;
    }

    public Survey requireByToken(String token) {
        Survey survey = surveyService.getSurveyByToken(token);
        if (survey == null) {
            throw new ApiException(404, "Анкета не найдена");
        }
        return survey;
    }

    public Question requireQuestion(int surveyId, int questionId) {
        Question question = questionService.getQuestion(questionId);
        if (question == null || question.getSurveyId() != surveyId) {
            throw new ApiException(404, "Вопрос не найден");
        }
        return question;
    }

    public LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(400, "Некорректная дата, ожидается формат 2030-12-31T23:59");
        }
    }

    public Map<String, String[]> submitParams(SubmitRequest request) {
        if (request == null || request.answers() == null) {
            throw new ApiException(400, "Не переданы ответы");
        }
        Map<String, String[]> params = new HashMap<>();
        for (AnswerRequest answer : request.answers()) {
            if (answer == null || answer.questionId() == null) {
                throw new ApiException(400, "Для каждого ответа укажите questionId");
            }
            List<String> values = new ArrayList<>();
            if (answer.optionIds() != null) {
                for (Integer optionId : answer.optionIds()) {
                    if (optionId != null) {
                        values.add(String.valueOf(optionId));
                    }
                }
            }
            if (answer.text() != null) {
                values.add(answer.text());
            }
            params.put("q_" + answer.questionId(), values.toArray(new String[0]));
        }
        return params;
    }
}
