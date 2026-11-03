package org.example.survey.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Question {

    private int id;
    private String content;
    private int surveyId;
    private String type = "SINGLE";
    private boolean required = true;
    private Integer correctOptionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<SurveyOption> options = new ArrayList<>();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getSurveyId() {
        return surveyId;
    }

    public void setSurveyId(int surveyId) {
        this.surveyId = surveyId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public Integer getCorrectOptionId() {
        return correctOptionId;
    }

    public void setCorrectOptionId(Integer correctOptionId) {
        this.correctOptionId = correctOptionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<SurveyOption> getOptions() {
        return options;
    }

    public void setOptions(List<SurveyOption> options) {
        this.options = options;
    }

    public boolean isSingle() {
        return "SINGLE".equals(type);
    }

    public boolean isMultiple() {
        return "MULTIPLE".equals(type);
    }

    public boolean isText() {
        return "TEXT".equals(type);
    }

    public boolean isChoice() {
        return isSingle() || isMultiple();
    }

    public boolean isGraded() {
        return isSingle() && correctOptionId != null;
    }

    public String getTypeLabel() {
        if (isMultiple()) {
            return "Несколько вариантов";
        }
        if (isText()) {
            return "Текстовый ответ";
        }
        return "Один вариант";
    }

}
