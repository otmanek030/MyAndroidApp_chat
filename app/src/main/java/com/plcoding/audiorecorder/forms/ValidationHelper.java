package com.plcoding.audiorecorder.forms;

import android.annotation.SuppressLint;

import java.util.List;

public class ValidationHelper {

    public static boolean validateIntegerResponse(ChecklistQuestion question, String input) {
        if (input == null || input.trim().isEmpty()) {
            return !question.isIs_required();
        }

        try {
            int value = Integer.parseInt(input.trim());
            ChecklistQuestion.ValidationRules validation = question.getValidation();

            if (validation != null) {
                if (validation.getMin_value() != null && value < validation.getMin_value()) {
                    return false;
                }
                if (validation.getMax_value() != null && value > validation.getMax_value()) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean validateDecimalResponse(ChecklistQuestion question, String input) {
        if (input == null || input.trim().isEmpty()) {
            return !question.isIs_required();
        }

        try {
            double value = Double.parseDouble(input.trim());
            ChecklistQuestion.ValidationRules validation = question.getValidation();

            if (validation != null) {
                if (validation.getMin_value() != null && value < validation.getMin_value()) {
                    return false;
                }
                if (validation.getMax_value() != null && value > validation.getMax_value()) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean validateFileSize(ChecklistQuestion question, long fileSize) {
        ChecklistQuestion.ValidationRules validation = question.getValidation();
        if (validation != null && validation.getMax_file_size() != null) {
            return fileSize <= validation.getMax_file_size();
        }
        return fileSize <= 5242880; // Default 5MB limit
    }

    public static boolean validateFileType(ChecklistQuestion question, String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return false;
        }

        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        ChecklistQuestion.ValidationRules validation = question.getValidation();

        if (validation != null && validation.getAllowed_file_types() != null) {
            return validation.getAllowed_file_types().contains(extension);
        }

        // Default allowed types
        List<String> defaultTypes = java.util.Arrays.asList("jpg", "jpeg", "png", "gif", "webp");
        return defaultTypes.contains(extension);
    }

    @SuppressLint("DefaultLocale")
    public static String getValidationErrorMessage(ChecklistQuestion question, String input) {
        if (question.isIs_required() && (input == null || input.trim().isEmpty())) {
            return "This field is required";
        }

        switch (question.getType()) {
            case ChecklistQuestion.TYPE_INTEGER:
                if (!validateIntegerResponse(question, input)) {
                    ChecklistQuestion.ValidationRules validation = question.getValidation();
                    if (validation != null) {
                        if (validation.getMin_value() != null && validation.getMax_value() != null) {
                            return String.format("Please enter a number between %.0f and %.0f",
                                    validation.getMin_value(), validation.getMax_value());
                        } else if (validation.getMin_value() != null) {
                            return String.format("Please enter a number greater than or equal to %.0f",
                                    validation.getMin_value());
                        } else if (validation.getMax_value() != null) {
                            return String.format("Please enter a number less than or equal to %.0f",
                                    validation.getMax_value());
                        }
                    }
                    return "Please enter a valid integer";
                }
                break;

            case ChecklistQuestion.TYPE_DECIMAL:
                if (!validateDecimalResponse(question, input)) {
                    ChecklistQuestion.ValidationRules validation = question.getValidation();
                    if (validation != null) {
                        if (validation.getMin_value() != null && validation.getMax_value() != null) {
                            return String.format("Please enter a number between %.2f and %.2f",
                                    validation.getMin_value(), validation.getMax_value());
                        } else if (validation.getMin_value() != null) {
                            return String.format("Please enter a number greater than or equal to %.2f",
                                    validation.getMin_value());
                        } else if (validation.getMax_value() != null) {
                            return String.format("Please enter a number less than or equal to %.2f",
                                    validation.getMax_value());
                        }
                    }
                    return "Please enter a valid decimal number";
                }
                break;
        }

        return null; // No validation error
    }
}