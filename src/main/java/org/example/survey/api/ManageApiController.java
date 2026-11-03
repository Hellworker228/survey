package org.example.survey.api;

import jakarta.servlet.http.HttpServletRequest;
import org.example.survey.api.ApiDtos.CorrectRequest;
import org.example.survey.api.ApiDtos.OptionRequest;
import org.example.survey.api.ApiDtos.QuestionDto;
import org.example.survey.api.ApiDtos.QuestionRequest;
import org.example.survey.api.ApiDtos.SurveyDto;
import org.example.survey.api.ApiDtos.SurveyRequest;
import org.example.survey.model.Question;
import org.example.survey.model.Survey;
import org.example.survey.model.SurveyOption;
import org.example.survey.service.QuestionService;
import org.example.survey.service.SurveyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/surveys")
public class ManageApiController {

    private final SurveyService surveyService;
    private final QuestionService questionService;
    private final ApiMapper mapper;
    private final ApiSupport support;

    public ManageApiController(SurveyService surveyService, QuestionService questionService,
                               ApiMapper mapper, ApiSupport support) {
        this.surveyService = surveyService;
        this.questionService = questionService;
        this.mapper = mapper;
        this.support = support;
    }

    private static boolean flag(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private static void require(Object body) {
        if (body == null) {
            throw new ApiException(400, "Пустое тело запроса");
        }
    }

    private Question reload(int surveyId, int questionId) {
        for (Question question : questionService.getQuestions(surveyId)) {
            if (question.getId() == questionId) {
                return question;
            }
        }
        throw new ApiException(404, "Вопрос не найден");
    }

    @PostMapping
    public ResponseEntity<SurveyDto> create(@RequestBody SurveyRequest body, HttpServletRequest request) {
        require(body);
        SurveyService.Saved saved = surveyService.createSurvey(support.userId(request), body.title(),
                body.description(), flag(body.publicAccess(), true), body.accessList(),
                support.parseDateTime(body.endsAt()), flag(body.anonymous(), false));
        if (!saved.ok()) {
            throw new ApiException(400, saved.error());
        }
        Survey survey = support.requireSurvey(saved.id());
        return ResponseEntity.status(201).body(mapper.survey(survey, List.of(), true));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SurveyDto> update(@PathVariable int id, @RequestBody SurveyRequest body,
                                            HttpServletRequest request) {
        require(body);
        support.requireManaged(id, request);
        String error = surveyService.updateSurvey(id, body.title(), body.description(),
                flag(body.publicAccess(), true), body.accessList(), support.parseDateTime(body.endsAt()),
                flag(body.anonymous(), false));
        if (error != null) {
            throw new ApiException(400, error);
        }
        return ResponseEntity.ok(mapper.survey(support.requireSurvey(id), questionService.getQuestions(id), true));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id, HttpServletRequest request) {
        support.requireManaged(id, request);
        surveyService.deleteSurvey(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<SurveyDto> publish(@PathVariable int id, HttpServletRequest request) {
        return setActive(id, true, request);
    }

    @PostMapping("/{id}/unpublish")
    public ResponseEntity<SurveyDto> unpublish(@PathVariable int id, HttpServletRequest request) {
        return setActive(id, false, request);
    }

    private ResponseEntity<SurveyDto> setActive(int id, boolean active, HttpServletRequest request) {
        support.requireManaged(id, request);
        String error = surveyService.setActive(id, active);
        if (error != null) {
            throw new ApiException(400, error);
        }
        return ResponseEntity.ok(mapper.survey(support.requireSurvey(id), null, true));
    }

    @PostMapping("/{id}/questions")
    public ResponseEntity<QuestionDto> addQuestion(@PathVariable int id, @RequestBody QuestionRequest body,
                                                   HttpServletRequest request) {
        require(body);
        support.requireManaged(id, request);
        QuestionService.SavedQuestion saved = questionService.createQuestion(
                id, body.content(), body.type() == null ? "SINGLE" : body.type(), flag(body.required(), true));
        if (!saved.ok()) {
            throw new ApiException(400, saved.error());
        }
        if (body.options() != null) {
            for (String option : body.options()) {
                String error = questionService.addOption(saved.id(), option);
                if (error != null) {
                    questionService.deleteQuestion(saved.id());
                    throw new ApiException(400, error);
                }
            }
        }
        if (body.correctIndex() != null) {
            Question created = reload(id, saved.id());
            int index = body.correctIndex();
            if (index < 0 || index >= created.getOptions().size()) {
                questionService.deleteQuestion(saved.id());
                throw new ApiException(400, "Некорректный correctIndex");
            }
            SurveyOption correct = created.getOptions().get(index);
            String error = questionService.setCorrectOption(saved.id(), correct.getId());
            if (error != null) {
                questionService.deleteQuestion(saved.id());
                throw new ApiException(400, error);
            }
        }
        return ResponseEntity.status(201).body(mapper.question(reload(id, saved.id()), true));
    }

    @PutMapping("/{id}/questions/{questionId}")
    public ResponseEntity<QuestionDto> updateQuestion(@PathVariable int id, @PathVariable int questionId,
                                                      @RequestBody QuestionRequest body, HttpServletRequest request) {
        require(body);
        support.requireManaged(id, request);
        support.requireQuestion(id, questionId);
        String error = questionService.saveQuestion(questionId, id, body.content(),
                body.type() == null ? "SINGLE" : body.type(), flag(body.required(), true));
        if (error != null) {
            throw new ApiException(400, error);
        }
        return ResponseEntity.ok(mapper.question(reload(id, questionId), true));
    }

    @DeleteMapping("/{id}/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable int id, @PathVariable int questionId,
                                               HttpServletRequest request) {
        support.requireManaged(id, request);
        support.requireQuestion(id, questionId);
        questionService.deleteQuestion(questionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/questions/{questionId}/options")
    public ResponseEntity<QuestionDto> addOption(@PathVariable int id, @PathVariable int questionId,
                                                 @RequestBody OptionRequest body, HttpServletRequest request) {
        require(body);
        support.requireManaged(id, request);
        support.requireQuestion(id, questionId);
        String error = questionService.addOption(questionId, body.content());
        if (error != null) {
            throw new ApiException(400, error);
        }
        return ResponseEntity.status(201).body(mapper.question(reload(id, questionId), true));
    }

    @DeleteMapping("/{id}/options/{optionId}")
    public ResponseEntity<Void> deleteOption(@PathVariable int id, @PathVariable int optionId,
                                             HttpServletRequest request) {
        support.requireManaged(id, request);
        SurveyOption option = questionService.getOption(optionId);
        if (option == null) {
            throw new ApiException(404, "Вариант не найден");
        }
        support.requireQuestion(id, option.getQuestionId());
        questionService.deleteOption(optionId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/questions/{questionId}/correct")
    public ResponseEntity<QuestionDto> setCorrect(@PathVariable int id, @PathVariable int questionId,
                                                  @RequestBody CorrectRequest body, HttpServletRequest request) {
        require(body);
        support.requireManaged(id, request);
        support.requireQuestion(id, questionId);
        String error = questionService.setCorrectOption(questionId, body.optionId());
        if (error != null) {
            throw new ApiException(400, error);
        }
        return ResponseEntity.ok(mapper.question(reload(id, questionId), true));
    }
}
