package org.example.survey.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.example.survey.model.Question;
import org.example.survey.model.Survey;
import org.example.survey.model.SurveyResult;
import org.example.survey.service.QuestionService;
import org.example.survey.service.ResultService;
import org.example.survey.service.SurveyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
public class SurveyController {

    private final SurveyService surveyService;
    private final QuestionService questionService;
    private final ResultService resultService;
    private final ControllerSupport support;

    public SurveyController(SurveyService surveyService, QuestionService questionService,
                            ResultService resultService, ControllerSupport support) {
        this.surveyService = surveyService;
        this.questionService = questionService;
        this.resultService = resultService;
        this.support = support;
    }

    @GetMapping("/surveys")
    public String surveys(@RequestParam(name = "q", defaultValue = "") String query,
                          @RequestParam(defaultValue = "1") int page,
                          Model model, HttpSession session) {
        model.addAttribute("pageData", surveyService.listAvailable(
                support.userId(session), query, page, SurveyService.PAGE_SIZE));
        return "surveys";
    }

    @GetMapping("/survey/{id}")
    public String takeSurvey(@PathVariable int id, Model model, HttpSession session, RedirectAttributes ra) {
        int userId = support.userId(session);
        Survey survey = support.requireAccessible(id, session);
        boolean manager = surveyService.canManage(survey, userId, support.isAdmin(session));

        if (resultService.hasCompleted(userId, id)) {
            if (survey.isAnonymous()) {
                ra.addFlashAttribute("error", "Вы уже проходили эту анонимную анкету. Ответы не связаны с вашей учётной записью и недоступны для просмотра");
                return "redirect:/surveys";
            }
            return "redirect:/survey/" + id + "/results";
        }
        if (!survey.isOpen() && !manager) {
            ra.addFlashAttribute("error", "Анкета сейчас недоступна для прохождения");
            return "redirect:/surveys";
        }
        List<Question> questions = questionService.getQuestions(id);
        if (questions.isEmpty() && !manager) {
            ra.addFlashAttribute("error", "В этой анкете пока нет вопросов");
            return "redirect:/surveys";
        }
        model.addAttribute("survey", survey);
        model.addAttribute("questions", questions);
        model.addAttribute("preview", !survey.isOpen());
        model.addAttribute("manager", manager);
        model.addAttribute("submitUrl", "/survey/" + id + "/submit");
        return "survey-detail";
    }

    @PostMapping("/survey/{id}/submit")
    public String submit(@PathVariable int id, HttpServletRequest request, HttpSession session,
                         RedirectAttributes ra) {
        int userId = support.userId(session);
        Survey survey = support.requireAccessible(id, session);
        List<Question> questions = questionService.getQuestions(id);
        ResultService.SubmitOutcome outcome = resultService.submit(survey, questions, userId, request.getParameterMap());
        if (!outcome.ok()) {
            ra.addFlashAttribute("error", outcome.message());
            if (resultService.hasCompleted(userId, id)) {
                return survey.isAnonymous() ? "redirect:/surveys" : "redirect:/survey/" + id + "/results";
            }
            return "redirect:/survey/" + id;
        }
        if (survey.isAnonymous()) {
            session.setAttribute("anonDone_" + id, outcome);
            return "redirect:/a/" + survey.getPublicToken() + "/thanks";
        }
        return "redirect:/survey/" + id + "/results";
    }

    @GetMapping("/survey/{id}/results")
    public String results(@PathVariable int id, Model model, HttpSession session) {
        int userId = support.userId(session);
        Survey survey = support.requireSurvey(id);
        SurveyResult result = resultService.getResult(userId, id);
        if (result == null) {
            return "redirect:/survey/" + id;
        }
        List<Question> questions = questionService.getQuestions(id);
        model.addAttribute("survey", survey);
        model.addAttribute("result", result);
        model.addAttribute("questionResults", resultService.getQuestionResults(userId, id, questions));
        return "survey-results";
    }

    @GetMapping("/completed-surveys")
    public String completed(Model model, HttpSession session) {
        List<SurveyResult> results = resultService.getUserResults(support.userId(session));
        model.addAttribute("results", results);
        model.addAttribute("totalScore", resultService.getAveragePercent(results));
        String last = results.isEmpty() || results.get(0).getSubmittedAt() == null
                ? "—" : results.get(0).getSubmittedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        model.addAttribute("lastActivity", last);
        return "completed-surveys";
    }
}
