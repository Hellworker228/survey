package org.example.survey.model;

import java.time.LocalDateTime;

public class SurveyResult {

    private int surveyId;
    private String surveyTitle;
    private String surveyDescription;
    private LocalDateTime submittedAt;
    private int gradedCount;
    private int correctCount;
    private Integer percent;

    public int getSurveyId() {
        return surveyId;
    }

    public void setSurveyId(int surveyId) {
        this.surveyId = surveyId;
    }

    public String getSurveyTitle() {
        return surveyTitle;
    }

    public void setSurveyTitle(String surveyTitle) {
        this.surveyTitle = surveyTitle;
    }

    public String getSurveyDescription() {
        return surveyDescription;
    }

    public void setSurveyDescription(String surveyDescription) {
        this.surveyDescription = surveyDescription;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public int getGradedCount() {
        return gradedCount;
    }

    public void setGradedCount(int gradedCount) {
        this.gradedCount = gradedCount;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(int correctCount) {
        this.correctCount = correctCount;
    }

    public Integer getPercent() {
        return percent;
    }

    public void setPercent(Integer percent) {
        this.percent = percent;
    }

    public boolean isGraded() {
        return gradedCount > 0;
    }

}
