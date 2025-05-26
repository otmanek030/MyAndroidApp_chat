package com.plcoding.audiorecorder.forms;

import java.util.List;

public class ChecklistAnswer {
    private String question_id;
    private Object value;
    private String photo_base64; // For photo uploads

    // Constructors
    public ChecklistAnswer() {}

    public ChecklistAnswer(String questionId, Object value) {
        this.question_id = questionId;
        this.value = value;
    }

    public ChecklistAnswer(String questionId, String photoBase64) {
        this.question_id = questionId;
        this.photo_base64 = photoBase64;
        this.value = photoBase64; // Also set as value for compatibility
    }

    // Helper methods for different value types
    public void setBooleanValue(boolean value) {
        this.value = value;
    }

    public void setStringValue(String value) {
        this.value = value;
    }

    public void setIntegerValue(int value) {
        this.value = value;
    }

    public void setDecimalValue(double value) {
        this.value = value;
    }

    public void setDateValue(String dateString) {
        this.value = dateString; // Should be in YYYY-MM-DD format
    }

    public void setPhotoValue(String base64Image) {
        this.photo_base64 = base64Image;
        this.value = base64Image; // Also set as value for compatibility
    }

    public void setMultipleChoiceValue(List<Integer> optionIds) {
        this.value = optionIds;
    }

    public void setSingleChoiceValue(int optionId) {
        this.value = optionId;
    }

    // Getters and setters
    public String getQuestion_id() {
        return question_id;
    }

    public void setQuestion_id(String question_id) {
        this.question_id = question_id;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getPhoto_base64() {
        return photo_base64;
    }

    public void setPhoto_base64(String photo_base64) {
        this.photo_base64 = photo_base64;
        // Also set as value if it's not already set
        if (this.value == null) {
            this.value = photo_base64;
        }
    }

    // Helper method to check if this is a photo response
    public boolean isPhotoResponse() {
        return photo_base64 != null && !photo_base64.isEmpty();
    }

    // Helper method to get the effective value (photo_base64 or value)
    public Object getEffectiveValue() {
        return isPhotoResponse() ? photo_base64 : value;
    }

    @Override
    public String toString() {
        return "ChecklistAnswer{" +
                "question_id='" + question_id + '\'' +
                ", value=" + value +
                ", photo_base64=" + (photo_base64 != null ? "[" + photo_base64.length() + " chars]" : "null") +
                '}';
    }
}