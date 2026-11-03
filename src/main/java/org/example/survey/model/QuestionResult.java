package org.example.survey.model;

import java.util.ArrayList;
import java.util.List;

public class QuestionResult {

    private Question question;
    private List<String> chosen = new ArrayList<>();
    private String text;
    private Boolean correct;
    private String correctText;

    public Question getQuestion() {
        return question;
    }

    public void setQuestion(Question question) {
        this.question = question;
    }

    public List<String> getChosen() {
        return chosen;
    }

    public void setChosen(List<String> chosen) {
        this.chosen = chosen;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Boolean getCorrect() {
        return correct;
    }

    public void setCorrect(Boolean correct) {
        this.correct = correct;
    }

    public String getCorrectText() {
        return correctText;
    }

    public void setCorrectText(String correctText) {
        this.correctText = correctText;
    }

    public boolean isAnswered() {
        return !chosen.isEmpty() || (text != null && !text.isBlank());
    }

}
