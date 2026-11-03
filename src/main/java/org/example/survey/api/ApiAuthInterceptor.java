package org.example.survey.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.survey.service.ApiTokenService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApiAuthInterceptor implements HandlerInterceptor {

    public static final String USER_ID_ATTR = "apiUserId";
    public static final String TOKEN_ATTR = "apiToken";

    private final ApiTokenService tokenService;

    public ApiAuthInterceptor(ApiTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = header.substring(7).trim();
            Integer userId = tokenService.authenticate(token);
            if (userId != null) {
                request.setAttribute(USER_ID_ATTR, userId);
                request.setAttribute(TOKEN_ATTR, token);
                return true;
            }
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":401,\"error\":\"Требуется авторизация\"}");
        return false;
    }
}
