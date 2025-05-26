package com.plcoding.audiorecorder.api;

import com.plcoding.audiorecorder.forms.ChecklistCategory;
import java.util.List;

public class CategoriesResponse {
    private String status;
    private String message;
    private List<ChecklistCategory> categories;

    // Constructors
    public CategoriesResponse() {}

    public CategoriesResponse(String status, List<ChecklistCategory> categories) {
        this.status = status;
        this.categories = categories;
    }

    // Getters and setters
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<ChecklistCategory> getCategories() {
        return categories;
    }

    public void setCategories(List<ChecklistCategory> categories) {
        this.categories = categories;
    }

    // Helper methods
    public boolean isSuccessful() {
        return "success".equals(status);
    }

    public int getCategoryCount() {
        return categories != null ? categories.size() : 0;
    }
}