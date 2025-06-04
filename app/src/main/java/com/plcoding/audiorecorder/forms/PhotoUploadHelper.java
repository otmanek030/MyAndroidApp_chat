// PhotoUploadHelper.java - Fixed version with all required methods

package com.plcoding.audiorecorder.forms;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class PhotoUploadHelper {
    private static final String TAG = "PhotoUploadHelper";
    private static final int MAX_WIDTH = 1920;
    private static final int MAX_HEIGHT = 1080;
    private static final int JPEG_QUALITY = 85;

    public static String encodeImageToBase64(String imagePath, ChecklistQuestion question) {
        try {
            // Load and compress the image
            Bitmap bitmap = loadAndCompressImage(imagePath);
            if (bitmap == null) {
                Log.e(TAG, "Failed to load image from path: " + imagePath);
                return null;
            }

            // Convert to base64
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream);
            byte[] imageBytes = outputStream.toByteArray();

            // Validate file size
            if (!ValidationHelper.validateFileSize(question, imageBytes.length)) {
                Log.e(TAG, "Image file size exceeds maximum allowed size");
                return null;
            }

            String base64String = Base64.encodeToString(imageBytes, Base64.DEFAULT);
            return "data:image/jpeg;base64," + base64String;

        } catch (Exception e) {
            Log.e(TAG, "Error encoding image to base64", e);
            return null;
        }
    }

    private static Bitmap loadAndCompressImage(String imagePath) {
        try {
            // First, get image dimensions
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, options);

            // Calculate scale factor
            int scaleFactor = calculateScaleFactor(options.outWidth, options.outHeight);

            // Load scaled image
            options.inJustDecodeBounds = false;
            options.inSampleSize = scaleFactor;

            return BitmapFactory.decodeFile(imagePath, options);

        } catch (Exception e) {
            Log.e(TAG, "Error loading and compressing image", e);
            return null;
        }
    }

    private static int calculateScaleFactor(int width, int height) {
        int scaleFactor = 1;

        if (height > MAX_HEIGHT || width > MAX_WIDTH) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / scaleFactor) >= MAX_HEIGHT
                    && (halfWidth / scaleFactor) >= MAX_WIDTH) {
                scaleFactor *= 2;
            }
        }

        return scaleFactor;
    }

    public static long getImageFileSize(String imagePath) {
        try {
            File file = new File(imagePath);
            return file.length();
        } catch (Exception e) {
            Log.e(TAG, "Error getting file size", e);
            return 0;
        }
    }

    public static String getImageMimeType(String imagePath) {
        if (imagePath == null) return null;

        String extension = imagePath.substring(imagePath.lastIndexOf('.') + 1).toLowerCase();
        switch (extension) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";
            default:
                return "image/jpeg"; // Default
        }
    }

    // ✅ NEW: Get photo source requirements as user-friendly text
    public static String getPhotoSourceRequirements(ChecklistQuestion question) {
        if (!question.isFileUpload()) {
            return "";
        }

        ChecklistQuestion.PhotoSource config = question.getPhoto_source();
        if (config == null) {
            return "📷 📖 Take a photo or select from gallery";
        }

        if (config.isCamera_enabled() && config.isGallery_enabled()) {
            return "📷 📖 Take a photo or select from gallery";
        } else if (config.isCamera_enabled()) {
            return "📷 Take a photo with camera";
        } else if (config.isGallery_enabled()) {
            return "📖 Select a photo from gallery";
        }

        return "";
    }

    // ✅ NEW: Get camera preference text
    public static String getCameraPreferenceText(ChecklistQuestion question) {
        if (!question.isFileUpload() || question.getPhoto_source() == null) {
            return "";
        }

        ChecklistQuestion.PhotoSource config = question.getPhoto_source();
        if (!config.isCamera_enabled()) {
            return "";
        }

        String preference = config.getCamera_preference();
        if (preference == null) {
            return "📷 Use back camera";
        }

        switch (preference) {
            case "front":
                return "👤 Use front camera (selfie)";
            case "back":
                return "📷 Use back camera";
            case "any":
                return "🔄 Choose camera (front or back)";
            default:
                return "📷 Use back camera";
        }
    }

    // ✅ NEW: Check if camera preference is met
    public static boolean isCameraPreferenceMet(ChecklistQuestion question, String usedCamera) {
        if (!question.isFileUpload() || question.getPhoto_source() == null) {
            return true;
        }

        ChecklistQuestion.PhotoSource config = question.getPhoto_source();
        if (!config.isCamera_enabled()) {
            return true; // No camera requirements
        }

        String preference = config.getCamera_preference();
        if ("any".equals(preference)) {
            return true; // Any camera is acceptable
        }

        return preference != null && preference.equals(usedCamera);
    }

    // ✅ NEW: Get file size limit text
    public static String getFileSizeLimitText(ChecklistQuestion question) {
        if (question.getValidation() == null || question.getValidation().getMax_file_size() == null) {
            return "Max size: 5MB";
        }

        long maxSizeBytes = question.getValidation().getMax_file_size();
        double maxSizeMB = maxSizeBytes / (1024.0 * 1024.0);

        if (maxSizeMB >= 1.0) {
            return String.format("Max size: %.1fMB", maxSizeMB);
        } else {
            double maxSizeKB = maxSizeBytes / 1024.0;
            return String.format("Max size: %.0fKB", maxSizeKB);
        }
    }

    // ✅ NEW: Get allowed file types text
    public static String getAllowedFileTypesText(ChecklistQuestion question) {
        if (question.getValidation() == null || question.getValidation().getAllowed_file_types() == null) {
            return "jpg, png, gif, webp";
        }

        return String.join(", ", question.getValidation().getAllowed_file_types());
    }

    // ✅ NEW: Validate photo configuration
    public static boolean validatePhotoConfiguration(ChecklistQuestion question) {
        if (!question.isFileUpload()) {
            return true;
        }

        ChecklistQuestion.PhotoSource config = question.getPhoto_source();
        if (config == null) {
            Log.w(TAG, "No photo source configuration for photo upload question");
            return false;
        }

        if (!config.isCamera_enabled() && !config.isGallery_enabled()) {
            Log.e(TAG, "Neither camera nor gallery is enabled for photo question");
            return false;
        }

        return true;
    }

    // ✅ NEW: Get complete photo info text
    public static String getCompletePhotoInfo(ChecklistQuestion question) {
        if (!question.isFileUpload()) {
            return "";
        }

        StringBuilder info = new StringBuilder();

        // Source requirements
        String sourceText = getPhotoSourceRequirements(question);
        if (!sourceText.isEmpty()) {
            info.append(sourceText).append("\n");
        }

        // Camera preference
        String cameraText = getCameraPreferenceText(question);
        if (!cameraText.isEmpty()) {
            info.append(cameraText).append("\n");
        }

        // File constraints
        String sizeText = getFileSizeLimitText(question);
        String typesText = getAllowedFileTypesText(question);
        info.append("📋 ").append(sizeText).append(" • Types: ").append(typesText);

        return info.toString();
    }
}