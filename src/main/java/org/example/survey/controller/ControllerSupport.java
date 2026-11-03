package org.example.survey.controller;

import jakarta.servlet.http.HttpSession;
import org.example.survey.model.Survey;
import org.example.survey.security.SessionKeys;
import org.example.survey.service.AppService;
import org.example.survey.service.SurveyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ControllerSupport {

    private final AppService appService;
    private final SurveyService surveyService;

    public ControllerSupport(AppService appService, SurveyService surveyService) {
        this.appService = appService;
        this.surveyService = surveyService;
    }

    public int userId(HttpSession session) {
        Object value = session.getAttribute(SessionKeys.USER_ID);
        if (value instanceof Integer id) {
            return id;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    public boolean isAdmin(HttpSession session) {
        return appService.isAdmin(userId(session));
    }

    public Survey requireSurvey(int id) {
        Survey survey = surveyService.getSurveyById(id);
        if (survey == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return survey;
    }

    public Survey requireAccessible(int id, HttpSession session) {
        Survey survey = requireSurvey(id);
        if (!surveyService.canAccess(survey, userId(session), isAdmin(session))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return survey;
    }

    public Survey requireManaged(int id, HttpSession session) {
        Survey survey = requireSurvey(id);
        if (!surveyService.canManage(survey, userId(session), isAdmin(session))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return survey;
    }
}
