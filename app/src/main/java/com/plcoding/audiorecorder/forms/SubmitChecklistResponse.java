// SubmitChecklistResponse.java - Enhanced with camera statistics

package com.plcoding.audiorecorder.forms;

import java.util.Map;

public class SubmitChecklistResponse {
    private String status;
    private String message;
    private int submission_id;
    private String submission_date;
    private int photos_processed;
    // ✅ NEW: Camera statistics
    private Map<String, Object> camera_statistics;

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

    public int getSubmission_id() {
        return submission_id;
    }

    public void setSubmission_id(int submission_id) {
        this.submission_id = submission_id;
    }

    public String getSubmission_date() {
        return submission_date;
    }

    public void setSubmission_date(String submission_date) {
        this.submission_date = submission_date;
    }

    public int getPhotos_processed() {
        return photos_processed;
    }

    public void setPhotos_processed(int photos_processed) {
        this.photos_processed = photos_processed;
    }

    // ✅ NEW: Camera statistics getter and setter
    public Map<String, Object> getCamera_statistics() {
        return camera_statistics;
    }

    public void setCamera_statistics(Map<String, Object> camera_statistics) {
        this.camera_statistics = camera_statistics;
    }

    // ✅ NEW: Helper methods for camera statistics
    public int getTotalCameraPhotos() {
        if (camera_statistics != null && camera_statistics.containsKey("total_camera_photos")) {
            Object value = camera_statistics.get("total_camera_photos");
            return value instanceof Integer ? (Integer) value : 0;
        }
        return 0;
    }

    public int getFrontCameraUsed() {
        if (camera_statistics != null && camera_statistics.containsKey("front_camera_used")) {
            Object value = camera_statistics.get("front_camera_used");
            return value instanceof Integer ? (Integer) value : 0;
        }
        return 0;
    }

    public int getBackCameraUsed() {
        if (camera_statistics != null && camera_statistics.containsKey("back_camera_used")) {
            Object value = camera_statistics.get("back_camera_used");
            return value instanceof Integer ? (Integer) value : 0;
        }
        return 0;
    }

    public int getConfigurationMatches() {
        if (camera_statistics != null && camera_statistics.containsKey("configuration_matches")) {
            Object value = camera_statistics.get("configuration_matches");
            return value instanceof Integer ? (Integer) value : 0;
        }
        return 0;
    }

    public String getConfigurationMatchRate() {
        if (camera_statistics != null && camera_statistics.containsKey("configuration_match_rate")) {
            Object value = camera_statistics.get("configuration_match_rate");
            return value instanceof String ? (String) value : "N/A";
        }
        return "N/A";
    }

    // ✅ NEW: Get camera usage summary
    public String getCameraUsageSummary() {
        if (getTotalCameraPhotos() == 0) {
            return "No camera photos";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("📸 ").append(getTotalCameraPhotos()).append(" camera photos");

        if (getFrontCameraUsed() > 0) {
            summary.append(" (👤 ").append(getFrontCameraUsed()).append(" front");
        }

        if (getBackCameraUsed() > 0) {
            if (getFrontCameraUsed() > 0) {
                summary.append(", 📷 ").append(getBackCameraUsed()).append(" back");
            } else {
                summary.append(" (📷 ").append(getBackCameraUsed()).append(" back");
            }
        }

        if (getFrontCameraUsed() > 0 || getBackCameraUsed() > 0) {
            summary.append(")");
        }

        summary.append(" • ✅ ").append(getConfigurationMatchRate()).append(" compliant");

        return summary.toString();
    }

    @Override
    public String toString() {
        return "SubmitChecklistResponse{" +
                "status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", submission_id=" + submission_id +
                ", submission_date='" + submission_date + '\'' +
                ", photos_processed=" + photos_processed +
                ", camera_statistics=" + camera_statistics +
                '}';
    }
}