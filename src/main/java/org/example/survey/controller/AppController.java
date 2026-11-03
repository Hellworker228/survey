package org.example.survey.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.example.survey.model.SurveyResult;
import org.example.survey.model.User;
import org.example.survey.security.LoginAttempts;
import org.example.survey.security.RateLimiter;
import org.example.survey.security.SessionKeys;
import org.example.survey.service.AppService;
import org.example.survey.service.EmailVerificationService;
import org.example.survey.service.ResultService;
import org.example.survey.service.SurveyService;
import org.example.survey.util.Validators;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.util.List;

@Controller
public class AppController {

    private final AppService appService;
    private final SurveyService surveyService;
    private final ResultService resultService;
    private final LoginAttempts loginAttempts;
    private final ControllerSupport support;
    private final EmailVerificationService verificationService;
    private final RateLimiter rateLimiter;

    public AppController(AppService appService, SurveyService surveyService, ResultService resultService,
                         LoginAttempts loginAttempts, ControllerSupport support,
                         EmailVerificationService verificationService, RateLimiter rateLimiter) {
        this.appService = appService;
        this.surveyService = surveyService;
        this.resultService = resultService;
        this.loginAttempts = loginAttempts;
        this.support = support;
        this.verificationService = verificationService;
        this.rateLimiter = rateLimiter;
    }

    private static boolean isLoggedIn(HttpSession session) {
        return session.getAttribute(SessionKeys.USER_ID) != null;
    }

    @GetMapping("/")
    public String toIndex() {
        return "redirect:/index";
    }

    @GetMapping("/index")
    public String homePage(Model model, HttpSession session) {
        int userId = isLoggedIn(session) ? support.userId(session) : 0;
        model.addAttribute("surveys", surveyService.listAvailable(userId, "", 1, 6).getItems());
        return "index";
    }

    @GetMapping("/login")
    public String loginPage(Model model, HttpSession session) {
        if (isLoggedIn(session)) {
            return "redirect:/surveys";
        }
        model.addAttribute("user", new User());
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam(defaultValue = "") String username,
                        @RequestParam(defaultValue = "") String password,
                        HttpServletRequest request, Model model) {
        String key = username.trim().toLowerCase() + "|" + request.getRemoteAddr();
        User form = new User();
        form.setUsername(username);
        model.addAttribute("user", form);

        if (loginAttempts.isLocked(key)) {
            model.addAttribute("error", "Слишком много неудачных попыток. Попробуйте через 5 минут");
            return "login";
        }
        User user = appService.authenticate(username, password);
        if (user == null) {
            loginAttempts.failed(key);
            model.addAttribute("error", "Неверное имя пользователя или пароль");
            return "login";
        }
        if (!user.isEmailVerified()) {
            model.addAttribute("unverified", true);
            model.addAttribute("error", "Email не подтверждён. Перейдите по ссылке из письма или запросите письмо повторно");
            return "login";
        }
        loginAttempts.succeeded(key);

        HttpSession session = request.getSession(true);
        Object next = session.getAttribute(SessionKeys.NEXT_URL);
        request.changeSessionId();
        session.removeAttribute(SessionKeys.NEXT_URL);
        session.setAttribute(SessionKeys.USER_ID, user.getId());
        session.setAttribute(SessionKeys.USERNAME, user.getUsername());
        session.setAttribute(SessionKeys.IS_ADMIN, user.isAdmin());
        if (next instanceof String url && isSafeRedirect(url)) {
            return "redirect:" + url;
        }
        return "redirect:/surveys";
    }

    private static boolean isSafeRedirect(String url) {
        return url.startsWith("/") && !url.startsWith("//") && !url.contains("\\")
                && !url.contains("\r") && !url.contains("\n");
    }

    @GetMapping("/registration")
    public String registrationPage(Model model, HttpSession session) {
        if (isLoggedIn(session)) {
            return "redirect:/surveys";
        }
        model.addAttribute("user", new User());
        return "registration";
    }

    @PostMapping("/registration")
    public String registration(@RequestParam(defaultValue = "") String username,
                               @RequestParam(defaultValue = "") String email,
                               @RequestParam(defaultValue = "") String password,
                               HttpServletRequest request, Model model, RedirectAttributes redirectAttributes) {
        User form = new User();
        form.setUsername(username);
        form.setEmail(email);
        model.addAttribute("user", form);
        if (!rateLimiter.tryAcquire("register|" + request.getRemoteAddr(), 10, Duration.ofHours(1))) {
            model.addAttribute("error", "Слишком много попыток регистрации. Попробуйте позже");
            return "registration";
        }
        AppService.Registered registered = appService.register(username, email, password);
        if (!registered.ok()) {
            model.addAttribute("error", registered.error());
            return "registration";
        }
        if (verificationService.isRequired()) {
            boolean sent = verificationService.sendVerification(registered.user());
            if (sent) {
                redirectAttributes.addFlashAttribute("success",
                        "Регистрация прошла успешно. Мы отправили письмо на " + registered.user().getEmail()
                                + ", перейдите по ссылке в нём, чтобы подтвердить адрес");
            } else {
                redirectAttributes.addFlashAttribute("error",
                        "Аккаунт создан, но письмо отправить не удалось. Попробуйте запросить его повторно при входе");
            }
        } else {
            redirectAttributes.addFlashAttribute("success", "Регистрация прошла успешно. Войдите в систему");
        }
        return "redirect:/login";
    }

    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam(defaultValue = "") String token, RedirectAttributes redirectAttributes) {
        if (verificationService.verify(token)) {
            redirectAttributes.addFlashAttribute("success", "Email подтверждён. Теперь можно войти в систему");
        } else {
            redirectAttributes.addFlashAttribute("error", "Ссылка недействительна или срок её действия истёк");
        }
        return "redirect:/login";
    }

    @PostMapping("/resend-verification")
    public String resendVerification(@RequestParam(defaultValue = "") String login,
                                     HttpServletRequest request, RedirectAttributes redirectAttributes) {
        String message = "Если аккаунт существует и email не подтверждён, мы отправили новое письмо";
        if (!rateLimiter.tryAcquire("resend|" + request.getRemoteAddr(), 10, Duration.ofHours(1))) {
            redirectAttributes.addFlashAttribute("error", "Слишком много запросов. Попробуйте позже");
            return "redirect:/login";
        }
        User user = appService.findByLogin(login);
        if (user != null && !user.isEmailVerified() && verificationService.canResend(user.getId())) {
            if (!verificationService.sendVerification(user)) {
                redirectAttributes.addFlashAttribute("error", "Не удалось отправить письмо. Попробуйте позже");
                return "redirect:/login";
            }
        }
        redirectAttributes.addFlashAttribute("success", message);
        return "redirect:/login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/index";
    }

    @GetMapping("/profile")
    public String profile(Model model, HttpSession session) {
        User user = appService.getUserById(support.userId(session));
        if (user == null) {
            session.invalidate();
            return "redirect:/login";
        }
        List<SurveyResult> results = resultService.getUserResults(user.getId());
        model.addAttribute("user", user);
        model.addAttribute("completedSurveys", results.size());
        model.addAttribute("totalScore", resultService.getAveragePercent(results));
        model.addAttribute("createdSurveys", surveyService.getSurveysByCreator(user.getId()).size());
        return "profile";
    }

    @PostMapping("/update-profile")
    public String updateProfile(@RequestParam(defaultValue = "") String username,
                                @RequestParam(defaultValue = "") String email,
                                @RequestParam(defaultValue = "") String password,
                                HttpSession session, RedirectAttributes redirectAttributes) {
        int userId = support.userId(session);
        AppService.ProfileResult result = appService.updateProfile(userId, username, email, password);
        if (!result.ok()) {
            redirectAttributes.addFlashAttribute("error", result.error());
            return "redirect:/profile";
        }
        if (result.emailChanged()) {
            boolean sent = verificationService.sendVerification(result.user());
            session.invalidate();
            if (sent) {
                redirectAttributes.addFlashAttribute("success",
                        "Email изменён. Подтвердите новый адрес по ссылке из письма и войдите снова");
            } else {
                redirectAttributes.addFlashAttribute("error",
                        "Email изменён, но письмо отправить не удалось. Запросите его повторно при входе");
            }
            return "redirect:/login";
        }
        session.setAttribute(SessionKeys.USERNAME, Validators.clean(username));
        redirectAttributes.addFlashAttribute("success", "Профиль обновлён");
        return "redirect:/profile";
    }
}
