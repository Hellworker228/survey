package org.example.survey.api;

import jakarta.servlet.http.HttpServletRequest;
import org.example.survey.api.ApiDtos.SubmitRequest;
import org.example.survey.api.ApiDtos.SubmitResponse;
import org.example.survey.api.ApiDtos.SurveyDto;
import org.example.survey.model.Question;
import org.example.survey.model.Survey;
import org.example.survey.security.RateLimiter;
import org.example.survey.service.QuestionService;
import org.example.survey.service.ResultService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/v1/public/surveys")
public class PublicApiController {

    private final QuestionService questionService;
    private final ResultService resultService;
    private final RateLimiter rateLimiter;
    private final ApiMapper mapper;
    private final ApiSupport support;

    public PublicApiController(QuestionService questionService, ResultService resultService,
                               RateLimiter rateLimiter, ApiMapper mapper, ApiSupport support) {
        this.questionService = questionService;
        this.resultService = resultService;
        this.rateLimiter = rateLimiter;
        this.mapper = mapper;
        this.support = support;
    }

    @GetMapping("/{token}")
    public ResponseEntity<SurveyDto> get(@PathVariable String token) {
        Survey survey = support.requireByToken(token);
        if (!survey.isOpen()) {
            throw new ApiException(403, "Анкета сейчас недоступна");
        }
        return ResponseEntity.ok(mapper.survey(survey, questionService.getQuestions(survey.getId()), false));
    }

    @PostMapping("/{token}/responses")
    public ResponseEntity<SubmitResponse> submit(@PathVariable String token, @RequestBody SubmitRequest body,
                                                 HttpServletRequest request) {
        Survey survey = support.requireByToken(token);
        if (!rateLimiter.tryAcquire("anon|" + request.getRemoteAddr(), 30, Duration.ofHours(1))) {
            throw new ApiException(429, "Слишком много отправок с вашего адреса. Попробуйте позже");
        }
        List<Question> questions = questionService.getQuestions(survey.getId());
        ResultService.SubmitOutcome outcome = resultService.submit(survey, questions, null, support.submitParams(body));
        if (!outcome.ok()) {
            throw new ApiException(400, outcome.message());
        }
        return ResponseEntity.status(201).body(new SubmitResponse(
                true, outcome.percent(), outcome.correctCount(), outcome.gradedCount()));
    }
}
