package com.plcoding.audiorecorder.api;

import com.plcoding.audiorecorder.forms.ChecklistForm;

import java.util.List;

public class ChecklistResponse {
    private String status;
    private List<ChecklistForm> forms;
    private String message;

    // Getters and setters
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<ChecklistForm> getForms() { return forms; }
    public void setForms(List<ChecklistForm> forms) { this.forms = forms; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}