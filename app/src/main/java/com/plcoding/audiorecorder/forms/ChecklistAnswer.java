// ChecklistAnswer.java - Enhanced with camera tracking

package com.plcoding.audiorecorder.forms;

import java.util.List;

public class ChecklistAnswer {
    private String question_id;
    private Object value;
    private String photo_base64; // For photo uploads
    // ✅ NEW: Camera tracking fields
    private String photo_source_used; // "camera" or "gallery"
    private String camera_used; // "front" or "back"

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

    // ✅ NEW: Constructor with camera information
    public ChecklistAnswer(String questionId, String photoBase64, String photoSource, String cameraUsed) {
        this.question_id = questionId;
        this.photo_base64 = photoBase64;
        this.value = photoBase64;
        this.photo_source_used = photoSource;
        this.camera_used = cameraUsed;
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

    // ✅ NEW: Enhanced photo value with camera info
    public void setPhotoValue(String base64Image, String photoSource, String cameraUsed) {
        this.photo_base64 = base64Image;
        this.value = base64Image;
        this.photo_source_used = photoSource;
        this.camera_used = cameraUsed;
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

    // ✅ NEW: Camera tracking getters and setters
    public String getPhoto_source_used() {
        return photo_source_used;
    }

    public void setPhoto_source_used(String photo_source_used) {
        this.photo_source_used = photo_source_used;
    }

    public String getCamera_used() {
        return camera_used;
    }

    public void setCamera_used(String camera_used) {
        this.camera_used = camera_used;
    }

    // Helper method to check if this is a photo response
    public boolean isPhotoResponse() {
        return photo_base64 != null && !photo_base64.isEmpty();
    }

    // ✅ NEW: Helper method to check if camera was used
    public boolean isCameraPhoto() {
        return "camera".equals(photo_source_used) && camera_used != null;
    }

    // ✅ NEW: Helper method to get camera type
    public String getCameraType() {
        return isCameraPhoto() ? camera_used : null;
    }

    // ✅ NEW: Helper method to check front camera usage
    public boolean isFrontCameraUsed() {
        return "front".equals(camera_used);
    }

    // ✅ NEW: Helper method to check back camera usage
    public boolean isBackCameraUsed() {
        return "back".equals(camera_used);
    }

    // ✅ NEW: Helper method to check gallery usage
    public boolean isGalleryPhoto() {
        return "gallery".equals(photo_source_used);
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
                ", photo_source_used='" + photo_source_used + '\'' +
                ", camera_used='" + camera_used + '\'' +
                '}';
    }

    // ✅ NEW: Get human-readable photo info
    public String getPhotoInfo() {
        if (!isPhotoResponse()) {
            return "No photo";
        }

        StringBuilder info = new StringBuilder();
        info.append("Photo");

        if (photo_source_used != null) {
            if ("camera".equals(photo_source_used)) {
                info.append(" (Camera");
                if (camera_used != null) {
                    info.append(" - ").append(camera_used.equals("front") ? "Front" : "Back");
                }
                info.append(")");
            } else if ("gallery".equals(photo_source_used)) {
                info.append(" (Gallery)");
            }
        }

        return info.toString();
    }

    // ✅ NEW: Validate camera configuration compliance
    public boolean isConfigurationCompliant(String expectedCameraPreference) {
        if (!isCameraPhoto()) {
            return true; // Gallery photos are always compliant
        }

        if ("any".equals(expectedCameraPreference)) {
            return true; // Any camera is allowed
        }

        return expectedCameraPreference.equals(camera_used);
    }
}