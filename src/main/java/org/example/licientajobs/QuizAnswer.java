package org.example.licientajobs;

public class QuizAnswer {
    private String text;
    private String trait; // e.g., "LEADERSHIP", "CREATIVITY"

    public QuizAnswer(String text, String trait) {
        this.text = text;
        this.trait = trait;
    }

    // Getters and setters
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getTrait() { return trait; }
    public void setTrait(String trait) { this.trait = trait; }
}