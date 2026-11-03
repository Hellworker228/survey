package org.example.survey.controller;

import jakarta.servlet.http.HttpSession;
import org.example.survey.database.DatabaseException;
import org.example.survey.security.CsrfInterceptor;
import org.example.survey.security.SessionKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(basePackages = "org.example.survey.controller")
public class GlobalModelAdvice {

    private static final Logger log = LoggerFactory.getLogger(GlobalModelAdvice.class);

    @ModelAttribute
    public void addCommon(Model model, HttpSession session) {
        model.addAttribute("csrfToken", CsrfInterceptor.tokenFor(session));
        Object flash = session.getAttribute(SessionKeys.FLASH_ERROR);
        if (flash != null) {
            session.removeAttribute(SessionKeys.FLASH_ERROR);
            model.addAttribute("error", flash);
        }
    }

    @ExceptionHandler(DatabaseException.class)
    public String handleDatabase(DatabaseException e, Model model, jakarta.servlet.http.HttpServletResponse response) {
        log.error("Database error", e);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        model.addAttribute("status", 500);
        model.addAttribute("message", "Ошибка при работе с базой данных. Попробуйте позже.");
        return "error";
    }
}
