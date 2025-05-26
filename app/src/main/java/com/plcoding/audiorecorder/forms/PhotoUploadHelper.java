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
}