package com.plcoding.audiorecorder.forms;

public class ChecklistForm {
    private int id;
    private String title;
    private String description;
    private String version;
    private boolean is_mandatory;
    private boolean show_start_button_after_completion;
    private int question_count;

    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public boolean is_mandatory() { return is_mandatory; }
    public void setIs_mandatory(boolean is_mandatory) { this.is_mandatory = is_mandatory; }

    public boolean isShow_start_button_after_completion() { return show_start_button_after_completion; }
    public void setShow_start_button_after_completion(boolean show_start_button_after_completion) {
        this.show_start_button_after_completion = show_start_button_after_completion;
    }

    public int getQuestion_count() { return question_count; }
    public void setQuestion_count(int question_count) { this.question_count = question_count; }
}