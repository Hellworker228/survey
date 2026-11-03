package org.example.survey.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

public class CsrfInterceptor implements HandlerInterceptor {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final String PARAM = "_csrf";
    public static final String HEADER = "X-CSRF-TOKEN";

    public static String tokenFor(HttpSession session) {
        Object existing = session.getAttribute(SessionKeys.CSRF);
        if (existing instanceof String token) {
            return token;
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(SessionKeys.CSRF, token);
        return token;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(true);
        String expected = tokenFor(session);
        if (SAFE_METHODS.contains(request.getMethod())) {
            return true;
        }
        String actual = request.getParameter(PARAM);
        if (actual == null || actual.isEmpty()) {
            actual = request.getHeader(HEADER);
        }
        if (actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token");
        return false;
    }
}
