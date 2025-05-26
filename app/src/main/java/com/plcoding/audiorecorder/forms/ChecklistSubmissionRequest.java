package com.plcoding.audiorecorder.forms;

import com.plcoding.audiorecorder.api.ChecklistResponse;

import java.util.List;

public class ChecklistSubmissionRequest {
    private int form_id;
    private List<ChecklistAnswer> responses;

    // Getters and setters
    public int getForm_id() {
        return form_id;
    }

    public void setForm_id(int form_id) {
        this.form_id = form_id;
    }

    public List<ChecklistAnswer> getResponses() {
        return responses;
    }

    public void setResponses(List<ChecklistAnswer> responses) {
        this.responses = responses;
    }
}
