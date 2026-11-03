package org.example.survey.controller;

import jakarta.servlet.http.HttpSession;
import org.example.survey.model.Question;
import org.example.survey.config.AppSettings;
import org.example.survey.model.Survey;
import org.example.survey.model.SurveyOption;
import org.example.survey.service.ExportService;
import org.example.survey.service.QuestionService;
import org.example.survey.service.ResultService;
import org.example.survey.service.SurveyService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Controller
public class BuilderController {

    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final SurveyService surveyService;
    private final QuestionService questionService;
    private final ResultService resultService;
    private final ExportService exportService;
    private final ControllerSupport support;
    private final AppSettings settings;

    public BuilderController(SurveyService surveyService, QuestionService questionService,
                             ResultService resultService, ExportService exportService, ControllerSupport support,
                             AppSettings settings) {
        this.surveyService = surveyService;
        this.questionService = questionService;
        this.resultService = resultService;
        this.exportService = exportService;
        this.support = support;
        this.settings = settings;
    }

    private static LocalDateTime parseEndsAt(String value) throws DateTimeParseException {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value.trim(), INPUT_FORMAT);
    }

    private String back(int surveyId) {
        return "redirect:/survey/" + surveyId + "/edit";
    }

    private Question requireQuestion(int surveyId, int questionId) {
        Question question = questionService.getQuestion(questionId);
        if (question == null || question.getSurveyId() != surveyId) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return question;
    }

    @GetMapping("/my-surveys")
    public String mySurveys(Model model, HttpSession session) {
        model.addAttribute("surveys", surveyService.getSurveysByCreator(support.userId(session)));
        return "my-surveys";
    }

    @GetMapping("/create-survey")
    public String createPage(Model model) {
        model.addAttribute("survey", new Survey());
        model.addAttribute("endsAtValue", "");
        return "create-survey";
    }

    @PostMapping("/create-survey")
    public String create(@RequestParam(defaultValue = "") String title,
                         @RequestParam(defaultValue = "") String description,
                         @RequestParam(defaultValue = "public") String access,
                         @RequestParam(defaultValue = "") String accessList,
                         @RequestParam(defaultValue = "") String endsAt,
                         @RequestParam(defaultValue = "false") boolean anonymous,
                         Model model, HttpSession session, RedirectAttributes ra) {
        Survey form = new Survey();
        form.setTitle(title);
        form.setDescription(description);
        form.setAccessUsers("public".equals(access) ? "*" : "LIST");
        form.setAccessList(accessList);
        form.setAnonymous(anonymous);
        LocalDateTime deadline;
        try {
            deadline = parseEndsAt(endsAt);
        } catch (DateTimeParseException e) {
            model.addAttribute("survey", form);
            model.addAttribute("endsAtValue", "");
            model.addAttribute("error", "Некорректная дата окончания");
            return "create-survey";
        }
        SurveyService.Saved saved = surveyService.createSurvey(
                support.userId(session), title, description, "public".equals(access), accessList, deadline, anonymous);
        if (!saved.ok()) {
            model.addAttribute("survey", form);
            model.addAttribute("endsAtValue", endsAt);
            model.addAttribute("error", saved.error());
            return "create-survey";
        }
        ra.addFlashAttribute("success", "Анкета создана. Добавьте вопросы и опубликуйте её");
        return back(saved.id());
    }

    @GetMapping("/survey/{id}/edit")
    public String edit(@PathVariable int id, Model model, HttpSession session) {
        Survey survey = support.requireManaged(id, session);
        model.addAttribute("survey", survey);
        model.addAttribute("questions", questionService.getQuestions(id));
        model.addAttribute("endsAtValue", survey.getEndsAt() == null ? "" : survey.getEndsAt().format(INPUT_FORMAT));
        if (survey.isAnonymous() && survey.getPublicToken() != null) {
            model.addAttribute("shareUrl", settings.getBaseUrl() + "/a/" + survey.getPublicToken());
        }
        return "edit-survey";
    }

    @PostMapping("/survey/{id}/edit")
    public String update(@PathVariable int id,
                         @RequestParam(defaultValue = "") String title,
                         @RequestParam(defaultValue = "") String description,
                         @RequestParam(defaultValue = "public") String access,
                         @RequestParam(defaultValue = "") String accessList,
                         @RequestParam(defaultValue = "") String endsAt,
                         @RequestParam(defaultValue = "false") boolean anonymous,
                         HttpSession session, RedirectAttributes ra) {
        support.requireManaged(id, session);
        LocalDateTime deadline;
        try {
            deadline = parseEndsAt(endsAt);
        } catch (DateTimeParseException e) {
            ra.addFlashAttribute("error", "Некорректная дата окончания");
            return back(id);
        }
        String error = surveyService.updateSurvey(id, title, description, "public".equals(access), accessList, deadline, anonymous);
        if (error != null) {
            ra.addFlashAttribute("error", error);
        } else {
            ra.addFlashAttribute("success", "Настройки сохранены");
        }
        return back(id);
    }

    @PostMapping("/survey/{id}/publish")
    public String publish(@PathVariable int id, @RequestParam boolean active,
                          HttpSession session, RedirectAttributes ra) {
        support.requireManaged(id, session);
        String error = surveyService.setActive(id, active);
        if (error != null) {
            ra.addFlashAttribute("error", error);
        } else {
            ra.addFlashAttribute("success", active ? "Анкета опубликована" : "Анкета снята с публикации");
        }
        return back(id);
    }

    @PostMapping("/survey/{id}/delete")
    public String delete(@PathVariable int id, HttpSession session, RedirectAttributes ra) {
        support.requireManaged(id, session);
        surveyService.deleteSurvey(id);
        ra.addFlashAttribute("success", "Анкета удалена");
        return "redirect:/my-surveys";
    }

    @PostMapping("/survey/{id}/questions/save")
    public String saveQuestion(@PathVariable int id,
                               @RequestParam(required = false) Integer questionId,
                               @RequestParam(defaultValue = "") String content,
                               @RequestParam(defaultValue = "SINGLE") String type,
                               @RequestParam(defaultValue = "false") boolean required,
                               HttpSession session, RedirectAttributes ra) {
        support.requireManaged(id, session);
        if (questionId != null) {
            requireQuestion(id, questionId);
        }
        String error = questionService.saveQuestion(questionId, id, content, type, required);
        if (error != null) {
            ra.addFlashAttribute("error", error);
        } else {
            ra.addFlashAttribute("success", questionId == null ? "Вопрос добавлен" : "Вопрос обновлён");
        }
        return back(id);
    }

    @PostMapping("/survey/{id}/questions/{questionId}/delete")
    public String deleteQuestion(@PathVariable int id, @PathVariable int questionId,
                                 HttpSession session, RedirectAttributes ra) {
        support.requireManaged(id, session);
        requireQuestion(id, questionId);
        questionService.deleteQuestion(questionId);
        ra.addFlashAttribute("success", "Вопрос удалён");
        return back(id);
    }

    @PostMapping("/survey/{id}/questions/{questionId}/options")
    public String addOption(@PathVariable int id, @PathVariable int questionId,
                            @RequestParam(defaultValue = "") String content,
                            HttpSession session, RedirectAttributes ra) {
        support.requireManaged(id, session);
        requireQuestion(id, questionId);
        String error = questionService.addOption(questionId, content);
        if (error != null) {
            ra.addFlashAttribute("error", error);
        }
        return back(id);
    }

    @PostMapping("/survey/{id}/options/{optionId}/delete")
    public String deleteOption(@PathVariable int id, @PathVariable int optionId,
                               HttpSession session, RedirectAttributes ra) {
        support.requireManaged(id, session);
        SurveyOption option = questionService.getOption(optionId);
        if (option == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        requireQuestion(id, option.getQuestionId());
        questionService.deleteOption(optionId);
        return back(id);
    }

    @PostMapping("/survey/{id}/questions/{questionId}/correct")
    public String setCorrect(@PathVariable int id, @PathVariable int questionId,
                             @RequestParam(required = false) Integer optionId,
                             HttpSession session, RedirectAttributes ra) {
        support.requireManaged(id, session);
        requireQuestion(id, questionId);
        String error = questionService.setCorrectOption(questionId, optionId);
        if (error != null) {
            ra.addFlashAttribute("error", error);
        }
        return back(id);
    }

    @GetMapping("/survey/{id}/statistics")
    public String statistics(@PathVariable int id, Model model, HttpSession session) {
        Survey survey = support.requireManaged(id, session);
        model.addAttribute("stats", resultService.getStats(survey, questionService.getQuestions(id)));
        model.addAttribute("survey", survey);
        return "survey-statistics";
    }

    @GetMapping("/survey/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable int id, HttpSession session) throws IOException {
        Survey survey = support.requireManaged(id, session);
        byte[] data = exportService.exportSurvey(survey);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("survey-" + id + ".xlsx").build());
        return ResponseEntity.ok().headers(headers).body(data);
    }
}
