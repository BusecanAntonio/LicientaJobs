package org.example.licientajobs;

import java.util.List;

public class QuizQuestion {
    private int id;
    private String text;
    private List<QuizAnswer> answers;

    public QuizQuestion(int id, String text, List<QuizAnswer> answers) {
        this.id = id;
        this.text = text;
        this.answers = answers;
    }

    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public List<QuizAnswer> getAnswers() { return answers; }
    public void setAnswers(List<QuizAnswer> answers) { this.answers = answers; }
}