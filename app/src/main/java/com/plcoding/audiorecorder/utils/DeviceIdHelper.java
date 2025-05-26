package com.plcoding.audiorecorder.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.util.UUID;

/**
 * Helper class to manage device ID generation and retrieval
 */
public class DeviceIdHelper {
    private static final String TAG = "DeviceIdHelper";
    private static final String PREFS_FILE = "device_info";
    private static final String PREF_DEVICE_ID = "device_id";

    /**
     * Get a unique device ID that persists across app reinstalls
     */
    public static String getDeviceId(Context context) {
        if (context == null) {
            Log.e(TAG, "Context is null in getDeviceId");
            return generateFallbackId();
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        String deviceId = prefs.getString(PREF_DEVICE_ID, null);

        // If we already have a device ID, return it
        if (deviceId != null && !deviceId.isEmpty()) {
            Log.d(TAG, "Retrieved existing device ID: " + deviceId);
            return deviceId;
        }

        // Generate and save a new device ID
        deviceId = generateDeviceId(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_DEVICE_ID, deviceId);
        editor.apply();

        Log.d(TAG, "Generated new device ID: " + deviceId);
        return deviceId;
    }

    /**
     * Generate a unique device ID - FIXED VERSION
     */
    @SuppressLint("HardwareIds")
    private static String generateDeviceId(Context context) {
        String androidId = null;

        try {
            // Try to use Android ID first
            androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            Log.d(TAG, "Raw Android ID: " + androidId);
        } catch (Exception e) {
            Log.e(TAG, "Error getting Android ID", e);
        }

        // If Android ID is null or "9774d56d682e549c" (known bug value), generate a UUID
        if (androidId == null || androidId.equals("9774d56d682e549c") || androidId.length() < 8) {
            Log.d(TAG, "Using UUID fallback for device ID");
            return generateFallbackId();
        }

        // IMPORTANT: Clean the ID to avoid path traversal issues
        // Remove any characters that could be interpreted as path traversal
        androidId = androidId.replaceAll("[^a-zA-Z0-9]", "");

        // Ensure ID is at least 12 characters long
        if (androidId.length() < 12) {
            // Pad with a partial UUID (cleaned)
            String padUuid = UUID.randomUUID().toString().replaceAll("[^a-zA-Z0-9]", "");
            androidId = androidId + padUuid.substring(0, Math.min(padUuid.length(), 16 - androidId.length()));
        }

        // Truncate if longer than 16 characters
        if (androidId.length() > 16) {
            androidId = androidId.substring(0, 16);
        }

        Log.d(TAG, "Final cleaned device ID: " + androidId);
        return androidId;
    }

    /**
     * Fallback method to generate a random device ID - CLEANED VERSION
     */
    private static String generateFallbackId() {
        // Generate a clean alphanumeric ID
        String uuid = UUID.randomUUID().toString().replaceAll("[^a-zA-Z0-9]", "");
        String fallbackId = uuid.substring(0, Math.min(16, uuid.length()));
        Log.d(TAG, "Generated fallback ID: " + fallbackId);
        return fallbackId;
    }

    /**
     * Clear the stored device ID (for testing only)
     */
    public static void clearDeviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        prefs.edit().remove(PREF_DEVICE_ID).apply();
        Log.d(TAG, "Cleared stored device ID");
    }

    /**
     * Force regenerate device ID (for debugging)
     */
    public static String regenerateDeviceId(Context context) {
        clearDeviceId(context);
        return getDeviceId(context);
    }
}