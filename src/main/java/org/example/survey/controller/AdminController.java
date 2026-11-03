package org.example.survey.controller;

import jakarta.servlet.http.HttpSession;
import org.example.survey.service.AppService;
import org.example.survey.service.ResultService;
import org.example.survey.service.SurveyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final int RESPONSES_LIMIT = 200;

    private final AppService appService;
    private final SurveyService surveyService;
    private final ResultService resultService;
    private final ControllerSupport support;

    public AdminController(AppService appService, SurveyService surveyService, ResultService resultService,
                           ControllerSupport support) {
        this.appService = appService;
        this.surveyService = surveyService;
        this.resultService = resultService;
        this.support = support;
    }

    @GetMapping
    public String panel(Model model) {
        model.addAttribute("users", appService.getAllUsers());
        model.addAttribute("surveys", surveyService.getAllSurveys());
        model.addAttribute("responses", resultService.getAllResponses(RESPONSES_LIMIT));
        model.addAttribute("usersCount", appService.getUsersCount());
        model.addAttribute("surveysCount", surveyService.getTotalSurveysCount());
        model.addAttribute("responsesCount", surveyService.getTotalResponsesCount());
        model.addAttribute("activeUsers", surveyService.getActiveUsersCount());
        return "admin";
    }

    @PostMapping("/users/save")
    public String saveUser(@RequestParam(required = false) Integer id,
                           @RequestParam(defaultValue = "") String username,
                           @RequestParam(defaultValue = "") String email,
                           @RequestParam(defaultValue = "") String password,
                           @RequestParam(defaultValue = "false") boolean isAdmin,
                           HttpSession session, RedirectAttributes ra) {
        String error = appService.saveUser(id, username, email, password, isAdmin, support.userId(session));
        if (error != null) {
            ra.addFlashAttribute("error", error);
        } else {
            ra.addFlashAttribute("success", "Пользователь сохранён");
        }
        return "redirect:/admin";
    }

    @PostMapping("/users/delete/{id}")
    public String deleteUser(@PathVariable int id, HttpSession session, RedirectAttributes ra) {
        String error = appService.deleteUser(id, support.userId(session));
        if (error != null) {
            ra.addFlashAttribute("error", error);
        } else {
            ra.addFlashAttribute("success", "Пользователь удалён");
        }
        return "redirect:/admin";
    }

    @PostMapping("/surveys/toggle/{id}")
    public String toggleSurvey(@PathVariable int id, @RequestParam boolean active, RedirectAttributes ra) {
        String error = surveyService.setActive(id, active);
        if (error != null) {
            ra.addFlashAttribute("error", error);
        } else {
            ra.addFlashAttribute("success", "Статус анкеты изменён");
        }
        return "redirect:/admin";
    }

    @PostMapping("/surveys/delete/{id}")
    public String deleteSurvey(@PathVariable int id, RedirectAttributes ra) {
        surveyService.deleteSurvey(id);
        ra.addFlashAttribute("success", "Анкета удалена");
        return "redirect:/admin";
    }

    @PostMapping("/responses/delete/{id}")
    public String deleteResponse(@PathVariable int id, RedirectAttributes ra) {
        resultService.deleteResponse(id);
        ra.addFlashAttribute("success", "Ответ удалён");
        return "redirect:/admin";
    }
}
