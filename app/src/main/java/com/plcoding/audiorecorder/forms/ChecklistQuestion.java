// ChecklistQuestion.java - Fixed with all missing methods

package com.plcoding.audiorecorder.forms;

import java.util.List;

public class ChecklistQuestion {
    private int id;
    private String text;
    private String help_text;
    private String type;
    private boolean is_required;

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
        private String camera_preference;

        private String camera_instructions;

        // Getters and setters
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public boolean isCamera_enabled() { return camera_enabled; }
        public void setCamera_enabled(boolean camera_enabled) { this.camera_enabled = camera_enabled; }

        public boolean isGallery_enabled() { return gallery_enabled; }


        public String getCamera_preference() { return camera_preference; }


        public String getCamera_instructions() { return camera_instructions; }
        public void setCamera_instructions(String camera_instructions) { this.camera_instructions = camera_instructions; }
    }

    // Nested class for validation rules
    public static class ValidationRules {
        private Double min_value;
        private Double max_value;

        private Long max_file_size;
        private List<String> allowed_file_types;

        // Getters and setters
        public Double getMin_value() { return min_value; }

        public Double getMax_value() { return max_value; }

        public Long getMax_file_size() { return max_file_size; }

        public List<String> getAllowed_file_types() { return allowed_file_types; }
    }

    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getHelp_text() { return help_text; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isIs_required() { return is_required; }

    public List<ChecklistOption> getOptions() { return options; }

    public ValidationRules getValidation() { return validation; }
    public void setValidation(ValidationRules validation) { this.validation = validation; }

    // ✅ NEW: Photo source getter and setter
    public PhotoSource getPhoto_source() { return photo_source; }


    public boolean isFileUpload() {
        return TYPE_PHOTO_UPLOAD.equals(type);
    }



}