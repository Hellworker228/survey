package org.example.survey.api;

import jakarta.servlet.http.HttpServletRequest;
import org.example.survey.api.ApiDtos.LoginRequest;
import org.example.survey.api.ApiDtos.MessageResponse;
import org.example.survey.api.ApiDtos.RegisterRequest;
import org.example.survey.api.ApiDtos.RegisterResponse;
import org.example.survey.api.ApiDtos.ResendRequest;
import org.example.survey.api.ApiDtos.TokenResponse;
import org.example.survey.api.ApiDtos.UserDto;
import org.example.survey.model.User;
import org.example.survey.security.LoginAttempts;
import org.example.survey.security.RateLimiter;
import org.example.survey.service.ApiTokenService;
import org.example.survey.service.AppService;
import org.example.survey.service.EmailVerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1")
public class AuthApiController {

    private final AppService appService;
    private final ApiTokenService tokenService;
    private final EmailVerificationService verificationService;
    private final LoginAttempts loginAttempts;
    private final RateLimiter rateLimiter;
    private final ApiMapper mapper;
    private final ApiSupport support;

    public AuthApiController(AppService appService, ApiTokenService tokenService,
                             EmailVerificationService verificationService, LoginAttempts loginAttempts,
                             RateLimiter rateLimiter, ApiMapper mapper, ApiSupport support) {
        this.appService = appService;
        this.tokenService = tokenService;
        this.verificationService = verificationService;
        this.loginAttempts = loginAttempts;
        this.rateLimiter = rateLimiter;
        this.mapper = mapper;
        this.support = support;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest body, HttpServletRequest request) {
        if (body == null) {
            throw new ApiException(400, "Пустое тело запроса");
        }
        if (!rateLimiter.tryAcquire("register|" + request.getRemoteAddr(), 10, Duration.ofHours(1))) {
            throw new ApiException(429, "Слишком много попыток регистрации. Попробуйте позже");
        }
        AppService.Registered registered = appService.register(body.username(), body.email(), body.password());
        if (!registered.ok()) {
            throw new ApiException(400, registered.error());
        }
        boolean required = verificationService.isRequired();
        boolean sent = required && verificationService.sendVerification(registered.user());
        String message = !required
                ? "Регистрация прошла успешно"
                : sent ? "Регистрация прошла успешно. Подтвердите email по ссылке из письма"
                : "Аккаунт создан, но письмо отправить не удалось. Запросите его повторно";
        return ResponseEntity.status(201)
                .body(new RegisterResponse(message, required, sent, mapper.user(registered.user())));
    }

    @PostMapping("/auth/resend-verification")
    public ResponseEntity<MessageResponse> resend(@RequestBody ResendRequest body, HttpServletRequest request) {
        if (body == null || body.login() == null) {
            throw new ApiException(400, "Укажите login");
        }
        if (!rateLimiter.tryAcquire("resend|" + request.getRemoteAddr(), 10, Duration.ofHours(1))) {
            throw new ApiException(429, "Слишком много запросов. Попробуйте позже");
        }
        User user = appService.findByLogin(body.login());
        if (user != null && !user.isEmailVerified() && verificationService.canResend(user.getId())) {
            verificationService.sendVerification(user);
        }
        return ResponseEntity.ok(new MessageResponse(
                "Если аккаунт существует и email не подтверждён, мы отправили новое письмо"));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest body, HttpServletRequest request) {
        if (body == null || body.login() == null || body.password() == null) {
            throw new ApiException(400, "Укажите login и password");
        }
        String key = body.login().trim().toLowerCase() + "|" + request.getRemoteAddr();
        if (loginAttempts.isLocked(key)) {
            throw new ApiException(429, "Слишком много неудачных попыток. Попробуйте через 5 минут");
        }
        User user = appService.authenticate(body.login(), body.password());
        if (user == null) {
            loginAttempts.failed(key);
            throw new ApiException(401, "Неверный логин или пароль");
        }
        if (!user.isEmailVerified()) {
            throw new ApiException(403, "Email не подтверждён");
        }
        loginAttempts.succeeded(key);
        String token = tokenService.create(user.getId());
        return ResponseEntity.ok(new TokenResponse(token, "Bearer", tokenService.expiresAt(token), mapper.user(user)));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        Object token = request.getAttribute(ApiAuthInterceptor.TOKEN_ATTR);
        if (token instanceof String value) {
            tokenService.revoke(value);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(HttpServletRequest request) {
        User user = appService.getUserById(support.userId(request));
        if (user == null) {
            throw new ApiException(401, "Требуется авторизация");
        }
        return ResponseEntity.ok(mapper.user(user));
    }
}
