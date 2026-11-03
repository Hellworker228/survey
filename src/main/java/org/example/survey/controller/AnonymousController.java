package org.example.survey.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.survey.model.Question;
import org.example.survey.model.Survey;
import org.example.survey.security.RateLimiter;
import org.example.survey.security.SessionKeys;
import org.example.survey.service.QuestionService;
import org.example.survey.service.ResultService;
import org.example.survey.service.SurveyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.util.List;

@Controller
@RequestMapping("/a/{token}")
public class AnonymousController {

    private final SurveyService surveyService;
    private final QuestionService questionService;
    private final ResultService resultService;
    private final RateLimiter rateLimiter;

    public AnonymousController(SurveyService surveyService, QuestionService questionService,
                               ResultService resultService, RateLimiter rateLimiter) {
        this.surveyService = surveyService;
        this.questionService = questionService;
        this.resultService = resultService;
        this.rateLimiter = rateLimiter;
    }

    private Survey requireAnonymous(String token) {
        Survey survey = surveyService.getSurveyByToken(token);
        if (survey == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return survey;
    }

    private static Integer currentUserId(HttpSession session) {
        Object value = session.getAttribute(SessionKeys.USER_ID);
        return value instanceof Integer id ? id : null;
    }

    private boolean alreadyDone(Survey survey, HttpServletRequest request, HttpSession session) {
        if (session.getAttribute("anonDone_" + survey.getId()) != null) {
            return true;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            String name = "anon_" + survey.getId();
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return true;
                }
            }
        }
        Integer userId = currentUserId(session);
        return userId != null && resultService.hasCompleted(userId, survey.getId());
    }

    private String message(Model model, Survey survey, String title, String text) {
        model.addAttribute("survey", survey);
        model.addAttribute("title", title);
        model.addAttribute("text", text);
        return "anonymous-message";
    }

    @GetMapping
    public String show(@PathVariable String token, Model model, HttpServletRequest request, HttpSession session) {
        Survey survey = requireAnonymous(token);
        if (!survey.isOpen()) {
            return message(model, survey, "Анкета недоступна", "Приём ответов в этой анкете закрыт");
        }
        if (alreadyDone(survey, request, session)) {
            return message(model, survey, "Вы уже участвовали", "Повторное прохождение этой анкеты недоступно");
        }
        List<Question> questions = questionService.getQuestions(survey.getId());
        if (questions.isEmpty()) {
            return message(model, survey, "Анкета недоступна", "В анкете пока нет вопросов");
        }
        model.addAttribute("survey", survey);
        model.addAttribute("questions", questions);
        model.addAttribute("preview", false);
        model.addAttribute("manager", false);
        model.addAttribute("submitUrl", "/a/" + token + "/submit");
        return "survey-detail";
    }

    @PostMapping("/submit")
    public String submit(@PathVariable String token, HttpServletRequest request, HttpServletResponse response,
                         HttpSession session, RedirectAttributes ra) {
        Survey survey = requireAnonymous(token);
        if (!rateLimiter.tryAcquire("anon|" + request.getRemoteAddr(), 30, Duration.ofHours(1))) {
            ra.addFlashAttribute("error", "Слишком много отправок с вашего адреса. Попробуйте позже");
            return "redirect:/a/" + token;
        }
        if (alreadyDone(survey, request, session)) {
            return "redirect:/a/" + token;
        }
        List<Question> questions = questionService.getQuestions(survey.getId());
        ResultService.SubmitOutcome outcome = resultService.submit(
                survey, questions, currentUserId(session), request.getParameterMap());
        if (!outcome.ok()) {
            ra.addFlashAttribute("error", outcome.message());
            return "redirect:/a/" + token;
        }
        session.setAttribute("anonDone_" + survey.getId(), outcome);
        Cookie cookie = new Cookie("anon_" + survey.getId(), "1");
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 24 * 365);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        return "redirect:/a/" + token + "/thanks";
    }

    @GetMapping("/thanks")
    public String thanks(@PathVariable String token, Model model, HttpSession session) {
        Survey survey = requireAnonymous(token);
        model.addAttribute("survey", survey);
        model.addAttribute("title", "Спасибо за участие!");
        model.addAttribute("text", "Ваши ответы сохранены анонимно");
        Object stored = session.getAttribute("anonDone_" + survey.getId());
        if (stored instanceof ResultService.SubmitOutcome outcome && outcome.percent() != null) {
            model.addAttribute("percent", outcome.percent());
            model.addAttribute("correct", outcome.correctCount());
            model.addAttribute("graded", outcome.gradedCount());
        }
        return "anonymous-message";
    }
}
