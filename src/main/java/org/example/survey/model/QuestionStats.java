package org.example.survey.model;

import java.util.ArrayList;
import java.util.List;

public class QuestionStats {

    private Question question;
    private int answered;
    private List<String> texts = new ArrayList<>();

    public Question getQuestion() {
        return question;
    }

    public void setQuestion(Question question) {
        this.question = question;
    }

    public int getAnswered() {
        return answered;
    }

    public void setAnswered(int answered) {
        this.answered = answered;
    }

    public List<String> getTexts() {
        return texts;
    }

    public void setTexts(List<String> texts) {
        this.texts = texts;
    }

}
