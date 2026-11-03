package org.example.survey.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.survey.service.AppService;
import org.springframework.web.servlet.HandlerInterceptor;

public class AuthInterceptor implements HandlerInterceptor {

    private final AppService appService;

    public AuthInterceptor(AppService appService) {
        this.appService = appService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        Object userId = session == null ? null : session.getAttribute(SessionKeys.USER_ID);
        if (!(userId instanceof Integer id)) {
            HttpSession created = request.getSession(true);
            created.setAttribute(SessionKeys.FLASH_ERROR, "Войдите в систему, чтобы продолжить");
            if ("GET".equals(request.getMethod())) {
                created.setAttribute(SessionKeys.NEXT_URL, requestUrl(request));
            }
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }
        if (request.getRequestURI().startsWith(request.getContextPath() + "/admin")) {
            if (!appService.isAdmin(id)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
                return false;
            }
        }
        return true;
    }

    private String requestUrl(HttpServletRequest request) {
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        String query = request.getQueryString();
        return query == null ? uri : uri + "?" + query;
    }
}
