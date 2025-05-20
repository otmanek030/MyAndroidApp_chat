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
     * Generate a unique device ID
     */
    @SuppressLint("HardwareIds")
    private static String generateDeviceId(Context context) {
        String androidId = null;

        try {
            // Try to use Android ID first
            androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception e) {
            Log.e(TAG, "Error getting Android ID", e);
        }

        // If Android ID is null or "9774d56d682e549c" (known bug value), generate a UUID
        if (androidId == null || androidId.equals("9774d56d682e549c") || androidId.length() < 8) {
            // Combine some device info with a random UUID
            String deviceInfo = Build.BRAND + Build.DEVICE + Build.MANUFACTURER + Build.MODEL;
            UUID deviceUuid = new UUID(deviceInfo.hashCode(), System.currentTimeMillis());
            androidId = deviceUuid.toString();
        }

        // Clean up the ID (remove hyphens and ensure it's the right length)
        androidId = androidId.replace("-", "");

        // Ensure ID is at least 12 characters long
        if (androidId.length() < 12) {
            // Pad with a partial UUID
            String padUuid = UUID.randomUUID().toString().replace("-", "");
            androidId = androidId + padUuid.substring(0, 16 - androidId.length());
        }

        // Truncate if longer than 16 characters
        if (androidId.length() > 16) {
            androidId = androidId.substring(0, 16);
        }

        return androidId;
    }

    /**
     * Fallback method to generate a random device ID
     */
    private static String generateFallbackId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * Clear the stored device ID (for testing only)
     */
    public static void clearDeviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        prefs.edit().remove(PREF_DEVICE_ID).apply();
    }
}