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
}