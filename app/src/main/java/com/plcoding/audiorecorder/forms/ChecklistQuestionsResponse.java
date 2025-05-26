package com.plcoding.audiorecorder.forms;

import java.util.Collection;
import java.util.List;

public class ChecklistQuestionsResponse {
    private String status;
    private ChecklistForm form;
    private List<CategorySection> categorized_questions;
    private List<ChecklistQuestion> uncategorized_questions;
    private String message;



    // Nested class for categorized questions
    public static class CategorySection {
        private ChecklistCategory category;
        private List<ChecklistQuestion> questions;

        // Getters and setters
        public ChecklistCategory getCategory() { return category; }
        public void setCategory(ChecklistCategory category) { this.category = category; }

        public List<ChecklistQuestion> getQuestions() { return questions; }
        public void setQuestions(List<ChecklistQuestion> questions) { this.questions = questions; }
    }

    // Getters and setters
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public ChecklistForm getForm() { return form; }
    public void setForm(ChecklistForm form) { this.form = form; }

    public List<CategorySection> getCategorized_questions() { return categorized_questions; }
    public void setCategorized_questions(List<CategorySection> categorized_questions) {
        this.categorized_questions = categorized_questions;
    }

    public List<ChecklistQuestion> getUncategorized_questions() { return uncategorized_questions; }
    public void setUncategorized_questions(List<ChecklistQuestion> uncategorized_questions) {
        this.uncategorized_questions = uncategorized_questions;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    // Helper method to get all questions in order
    public List<ChecklistQuestion> getAllQuestions() {
        List<ChecklistQuestion> allQuestions = new java.util.ArrayList<>();

        // Add categorized questions
        if (categorized_questions != null) {
            for (CategorySection section : categorized_questions) {
                if (section.getQuestions() != null) {
                    allQuestions.addAll(section.getQuestions());
                }
            }
        }

        // Add uncategorized questions
        if (uncategorized_questions != null) {
            allQuestions.addAll(uncategorized_questions);
        }

        return allQuestions;
    }
}
