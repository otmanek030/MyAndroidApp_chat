// ChecklistQuestion.java - Fixed with all missing methods

package com.plcoding.audiorecorder.forms;

import java.util.List;

public class ChecklistQuestion {
    private int id;
    private String text;
    private String help_text;
    private String type;
    private boolean is_required;
    private int order;
    private ChecklistCategory category;
    private List<ChecklistOption> options;
    private ValidationRules validation;

    // ✅ NEW: Photo source configuration
    private PhotoSource photo_source;

    // Question types constants
    public static final String TYPE_YES_NO = "yes_no";
    public static final String TYPE_RADIO_SINGLE = "radio_single";
    public static final String TYPE_RADIO_MULTIPLE = "radio_multiple";
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_PARAGRAPH = "paragraph";
    public static final String TYPE_INTEGER = "integer";
    public static final String TYPE_DECIMAL = "decimal";
    public static final String TYPE_DATE = "date";
    public static final String TYPE_PHOTO_UPLOAD = "photo_upload";

    // ✅ NEW: Photo source configuration class
    public static class PhotoSource {
        private String source;
        private boolean camera_enabled;
        private boolean gallery_enabled;
        private String description;
        private String camera_preference;
        private boolean prefer_front_camera;
        private boolean prefer_back_camera;
        private boolean allow_camera_choice;
        private String camera_description;
        private String camera_instructions;

        // Getters and setters
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public boolean isCamera_enabled() { return camera_enabled; }
        public void setCamera_enabled(boolean camera_enabled) { this.camera_enabled = camera_enabled; }

        public boolean isGallery_enabled() { return gallery_enabled; }
        public void setGallery_enabled(boolean gallery_enabled) { this.gallery_enabled = gallery_enabled; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getCamera_preference() { return camera_preference; }
        public void setCamera_preference(String camera_preference) { this.camera_preference = camera_preference; }

        public boolean isPrefer_front_camera() { return prefer_front_camera; }
        public void setPrefer_front_camera(boolean prefer_front_camera) { this.prefer_front_camera = prefer_front_camera; }

        public boolean isPrefer_back_camera() { return prefer_back_camera; }
        public void setPrefer_back_camera(boolean prefer_back_camera) { this.prefer_back_camera = prefer_back_camera; }

        public boolean isAllow_camera_choice() { return allow_camera_choice; }
        public void setAllow_camera_choice(boolean allow_camera_choice) { this.allow_camera_choice = allow_camera_choice; }

        public String getCamera_description() { return camera_description; }
        public void setCamera_description(String camera_description) { this.camera_description = camera_description; }

        public String getCamera_instructions() { return camera_instructions; }
        public void setCamera_instructions(String camera_instructions) { this.camera_instructions = camera_instructions; }
    }

    // Nested class for validation rules
    public static class ValidationRules {
        private Double min_value;
        private Double max_value;
        private Integer decimal_places;
        private Long max_file_size;
        private List<String> allowed_file_types;

        // Getters and setters
        public Double getMin_value() { return min_value; }
        public void setMin_value(Double min_value) { this.min_value = min_value; }

        public Double getMax_value() { return max_value; }
        public void setMax_value(Double max_value) { this.max_value = max_value; }

        public Integer getDecimal_places() { return decimal_places; }
        public void setDecimal_places(Integer decimal_places) { this.decimal_places = decimal_places; }

        public Long getMax_file_size() { return max_file_size; }
        public void setMax_file_size(Long max_file_size) { this.max_file_size = max_file_size; }

        public List<String> getAllowed_file_types() { return allowed_file_types; }
        public void setAllowed_file_types(List<String> allowed_file_types) { this.allowed_file_types = allowed_file_types; }
    }

    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getHelp_text() { return help_text; }
    public void setHelp_text(String help_text) { this.help_text = help_text; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isIs_required() { return is_required; }
    public void setIs_required(boolean is_required) { this.is_required = is_required; }

    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }

    public ChecklistCategory getCategory() { return category; }
    public void setCategory(ChecklistCategory category) { this.category = category; }

    public List<ChecklistOption> getOptions() { return options; }
    public void setOptions(List<ChecklistOption> options) { this.options = options; }

    public ValidationRules getValidation() { return validation; }
    public void setValidation(ValidationRules validation) { this.validation = validation; }

    // ✅ NEW: Photo source getter and setter
    public PhotoSource getPhoto_source() { return photo_source; }
    public void setPhoto_source(PhotoSource photo_source) { this.photo_source = photo_source; }

    // Helper methods
    public boolean requiresOptions() {
        return TYPE_RADIO_SINGLE.equals(type) || TYPE_RADIO_MULTIPLE.equals(type);
    }

    public boolean isNumberType() {
        return TYPE_INTEGER.equals(type) || TYPE_DECIMAL.equals(type);
    }

    public boolean isFileUpload() {
        return TYPE_PHOTO_UPLOAD.equals(type);
    }

    public boolean allowsMultipleSelection() {
        return TYPE_RADIO_MULTIPLE.equals(type);
    }

    // ✅ NEW: Photo source validation methods
    public boolean isCameraEnabled() {
        return photo_source != null && photo_source.isCamera_enabled();
    }

    public boolean isGalleryEnabled() {
        return photo_source != null && photo_source.isGallery_enabled();
    }

    public String getCameraPreference() {
        return photo_source != null ? photo_source.getCamera_preference() : "back";
    }

    public String getCameraInstructions() {
        return photo_source != null ? photo_source.getCamera_instructions() : "Use the back camera";
    }

    public String getCameraDescription() {
        return photo_source != null ? photo_source.getCamera_description() : "Back Camera";
    }

    public boolean isPrefersrontCamera() {
        return photo_source != null && photo_source.isPrefer_front_camera();
    }

    public boolean isPrefersBackCamera() {
        return photo_source != null && photo_source.isPrefer_back_camera();
    }

    public boolean allowsCameraChoice() {
        return photo_source != null && photo_source.isAllow_camera_choice();
    }

    // ✅ MISSING METHOD: Check if this is a photo upload question
    public boolean isPhotoUpload() {
        return TYPE_PHOTO_UPLOAD.equals(type);
    }

    // ✅ MISSING METHOD: Check if this has photo source configuration
    public boolean hasPhotoSourceConfig() {
        return photo_source != null;
    }

    // ✅ MISSING METHOD: Get photo source config (alias for getPhoto_source)
    public PhotoSource getPhotoSourceConfig() {
        return photo_source;
    }

    // ✅ MISSING METHOD: Check if both camera and gallery are allowed
    public boolean isBothAllowed() {
        return photo_source != null && photo_source.isCamera_enabled() && photo_source.isGallery_enabled();
    }

    // ✅ MISSING METHOD: Check if only camera is allowed
    public boolean isCameraOnly() {
        return photo_source != null && photo_source.isCamera_enabled() && !photo_source.isGallery_enabled();
    }

    // ✅ MISSING METHOD: Check if only gallery is allowed
    public boolean isGalleryOnly() {
        return photo_source != null && !photo_source.isCamera_enabled() && photo_source.isGallery_enabled();
    }
}