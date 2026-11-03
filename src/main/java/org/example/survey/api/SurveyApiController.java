package org.example.survey.api;

import jakarta.servlet.http.HttpServletRequest;
import org.example.survey.api.ApiDtos.PageDto;
import org.example.survey.api.ApiDtos.ResultDto;
import org.example.survey.api.ApiDtos.ResultSummaryDto;
import org.example.survey.api.ApiDtos.StatsDto;
import org.example.survey.api.ApiDtos.SubmitRequest;
import org.example.survey.api.ApiDtos.SubmitResponse;
import org.example.survey.api.ApiDtos.SurveyDto;
import org.example.survey.model.Page;
import org.example.survey.model.Question;
import org.example.survey.model.Survey;
import org.example.survey.model.SurveyResult;
import org.example.survey.service.ExportService;
import org.example.survey.service.QuestionService;
import org.example.survey.service.ResultService;
import org.example.survey.service.SurveyService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class SurveyApiController {

    private static final int MAX_PAGE_SIZE = 50;

    private final SurveyService surveyService;
    private final QuestionService questionService;
    private final ResultService resultService;
    private final ExportService exportService;
    private final ApiMapper mapper;
    private final ApiSupport support;

    public SurveyApiController(SurveyService surveyService, QuestionService questionService,
                               ResultService resultService, ExportService exportService,
                               ApiMapper mapper, ApiSupport support) {
        this.surveyService = surveyService;
        this.questionService = questionService;
        this.resultService = resultService;
        this.exportService = exportService;
        this.mapper = mapper;
        this.support = support;
    }

    @GetMapping("/surveys")
    public ResponseEntity<PageDto<SurveyDto>> list(@RequestParam(name = "q", defaultValue = "") String query,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size,
                                                   HttpServletRequest request) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page result = surveyService.listAvailable(support.userId(request), query, page, pageSize);
        List<SurveyDto> items = new ArrayList<>();
        for (Survey survey : result.getItems()) {
            items.add(mapper.survey(survey, null, false));
        }
        return ResponseEntity.ok(new PageDto<>(items, result.getPage(), result.getTotalPages(), result.getTotal()));
    }

    @GetMapping("/surveys/{id}")
    public ResponseEntity<SurveyDto> get(@PathVariable int id, HttpServletRequest request) {
        Survey survey = support.requireAccessible(id, request);
        int userId = support.userId(request);
        boolean manager = surveyService.canManage(survey, userId, support.isAdmin(request));
        if (!survey.isOpen() && !manager) {
            throw new ApiException(403, "Анкета сейчас недоступна");
        }
        survey.setCompleted(resultService.hasCompleted(userId, id));
        return ResponseEntity.ok(mapper.survey(survey, questionService.getQuestions(id), manager));
    }

    @PostMapping("/surveys/{id}/responses")
    public ResponseEntity<SubmitResponse> submit(@PathVariable int id, @RequestBody SubmitRequest body,
                                                 HttpServletRequest request) {
        Survey survey = support.requireAccessible(id, request);
        int userId = support.userId(request);
        if (resultService.hasCompleted(userId, id)) {
            throw new ApiException(409, "Вы уже проходили эту анкету");
        }
        List<Question> questions = questionService.getQuestions(id);
        ResultService.SubmitOutcome outcome = resultService.submit(survey, questions, userId, support.submitParams(body));
        if (!outcome.ok()) {
            throw new ApiException(400, outcome.message());
        }
        return ResponseEntity.status(201).body(new SubmitResponse(
                survey.isAnonymous(), outcome.percent(), outcome.correctCount(), outcome.gradedCount()));
    }

    @GetMapping("/surveys/{id}/result")
    public ResponseEntity<ResultDto> result(@PathVariable int id, HttpServletRequest request) {
        Survey survey = support.requireSurvey(id);
        if (survey.isAnonymous()) {
            throw new ApiException(404, "Ответы анонимной анкеты недоступны для просмотра");
        }
        int userId = support.userId(request);
        SurveyResult result = resultService.getResult(userId, id);
        if (result == null) {
            throw new ApiException(404, "Вы ещё не проходили эту анкету");
        }
        List<Question> questions = questionService.getQuestions(id);
        return ResponseEntity.ok(mapper.result(result, resultService.getQuestionResults(userId, id, questions)));
    }

    @GetMapping("/completed")
    public ResponseEntity<List<ResultSummaryDto>> completed(HttpServletRequest request) {
        List<ResultSummaryDto> items = new ArrayList<>();
        for (SurveyResult result : resultService.getUserResults(support.userId(request))) {
            items.add(mapper.summary(result));
        }
        return ResponseEntity.ok(items);
    }

    @GetMapping("/my/surveys")
    public ResponseEntity<List<SurveyDto>> mine(HttpServletRequest request) {
        List<SurveyDto> items = new ArrayList<>();
        for (Survey survey : surveyService.getSurveysByCreator(support.userId(request))) {
            items.add(mapper.survey(survey, null, true));
        }
        return ResponseEntity.ok(items);
    }

    @GetMapping("/surveys/{id}/statistics")
    public ResponseEntity<StatsDto> statistics(@PathVariable int id, HttpServletRequest request) {
        Survey survey = support.requireManaged(id, request);
        return ResponseEntity.ok(mapper.stats(resultService.getStats(survey, questionService.getQuestions(id))));
    }

    @GetMapping("/surveys/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable int id, HttpServletRequest request) throws IOException {
        Survey survey = support.requireManaged(id, request);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("survey-" + id + ".xlsx").build());
        return ResponseEntity.ok().headers(headers).body(exportService.exportSurvey(survey));
    }
}
