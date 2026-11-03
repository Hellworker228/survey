package org.example.survey.model;

import java.util.ArrayList;
import java.util.List;

public class SurveyStats {

    private Survey survey;
    private int responsesCount;
    private Integer averagePercent;
    private List<QuestionStats> questions = new ArrayList<>();

    public Survey getSurvey() {
        return survey;
    }

    public void setSurvey(Survey survey) {
        this.survey = survey;
    }

    public int getResponsesCount() {
        return responsesCount;
    }

    public void setResponsesCount(int responsesCount) {
        this.responsesCount = responsesCount;
    }

    public Integer getAveragePercent() {
        return averagePercent;
    }

    public void setAveragePercent(Integer averagePercent) {
        this.averagePercent = averagePercent;
    }

    public List<QuestionStats> getQuestions() {
        return questions;
    }

    public void setQuestions(List<QuestionStats> questions) {
        this.questions = questions;
    }

}
