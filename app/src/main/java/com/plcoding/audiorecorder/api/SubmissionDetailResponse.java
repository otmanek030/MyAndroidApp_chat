package com.plcoding.audiorecorder.api;

import com.plcoding.audiorecorder.forms.ChecklistForm;
import com.plcoding.audiorecorder.forms.ChecklistCategory;

import java.util.List;

public class SubmissionDetailResponse {
    private String status;
    private String message;
    private Submission submission;
    private List<CategoryResponse> categorized_responses;
    private List<ResponseDetail> uncategorized_responses;

    // Nested classes
    public static class Submission {
        private int id;
        private ChecklistForm form;
        private Device device;
        private String submitted_at;
        private boolean is_completed;
        private String completed_at;

        // Getters and setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public ChecklistForm getForm() { return form; }
        public void setForm(ChecklistForm form) { this.form = form; }

        public Device getDevice() { return device; }
        public void setDevice(Device device) { this.device = device; }

        public String getSubmitted_at() { return submitted_at; }
        public void setSubmitted_at(String submitted_at) { this.submitted_at = submitted_at; }

        public boolean isIs_completed() { return is_completed; }
        public void setIs_completed(boolean is_completed) { this.is_completed = is_completed; }

        public String getCompleted_at() { return completed_at; }
        public void setCompleted_at(String completed_at) { this.completed_at = completed_at; }
    }

    public static class Device {
        private int id;
        private String device_id;
        private String name;

        // Getters and setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public String getDevice_id() { return device_id; }
        public void setDevice_id(String device_id) { this.device_id = device_id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class CategoryResponse {
        private ChecklistCategory category;
        private List<ResponseDetail> responses;

        // Getters and setters
        public ChecklistCategory getCategory() { return category; }
        public void setCategory(ChecklistCategory category) { this.category = category; }

        public List<ResponseDetail> getResponses() { return responses; }
        public void setResponses(List<ResponseDetail> responses) { this.responses = responses; }
    }

    public static class ResponseDetail {
        private int id;
        private Question question;
        private String value;
        private String display_value;
        private String created_at;
        private String photo_url;

        // Nested Question class
        public static class Question {
            private int id;
            private String text;
            private String type;

            // Getters and setters
            public int getId() { return id; }
            public void setId(int id) { this.id = id; }

            public String getText() { return text; }
            public void setText(String text) { this.text = text; }

            public String getType() { return type; }
            public void setType(String type) { this.type = type; }
        }

        // Getters and setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public Question getQuestion() { return question; }
        public void setQuestion(Question question) { this.question = question; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }

        public String getDisplay_value() { return display_value; }
        public void setDisplay_value(String display_value) { this.display_value = display_value; }

        public String getCreated_at() { return created_at; }
        public void setCreated_at(String created_at) { this.created_at = created_at; }

        public String getPhoto_url() { return photo_url; }
        public void setPhoto_url(String photo_url) { this.photo_url = photo_url; }
    }

    // Main class getters and setters
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Submission getSubmission() { return submission; }
    public void setSubmission(Submission submission) { this.submission = submission; }

    public List<CategoryResponse> getCategorized_responses() { return categorized_responses; }
    public void setCategorized_responses(List<CategoryResponse> categorized_responses) {
        this.categorized_responses = categorized_responses;
    }

    public List<ResponseDetail> getUncategorized_responses() { return uncategorized_responses; }
    public void setUncategorized_responses(List<ResponseDetail> uncategorized_responses) {
        this.uncategorized_responses = uncategorized_responses;
    }
}