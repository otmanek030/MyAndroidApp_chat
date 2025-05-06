package com.plcoding.audiorecorder.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import java.util.UUID;

public class DeviceIdHelper {
    private static final String TAG = "DeviceIdHelper";
    private static final String PREFS_NAME = "AudioRecorderPrefs";
    private static final String PREF_UNIQUE_ID = "device_unique_id";

    private static String uniqueID = null;

    public static synchronized String getDeviceId(Context context) {
        if (uniqueID == null) {
            android.content.SharedPreferences sharedPrefs = context.getSharedPreferences(
                    PREFS_NAME, Context.MODE_PRIVATE);
            uniqueID = sharedPrefs.getString(PREF_UNIQUE_ID, null);

            if (uniqueID == null) {
                // First try to get Android ID
                uniqueID = getAndroidId(context);

                // If Android ID is not available, generate a UUID
                if (uniqueID == null || uniqueID.isEmpty()) {
                    uniqueID = UUID.randomUUID().toString();
                    Log.d(TAG, "Generated UUID as device ID: " + uniqueID);
                } else {
                    Log.d(TAG, "Using Android ID as device ID: " + uniqueID);
                }

                // Save to preferences
                android.content.SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putString(PREF_UNIQUE_ID, uniqueID);
                editor.apply();
            }
        }

        return uniqueID;
    }

    @SuppressLint("HardwareIds")
    private static String getAndroidId(Context context) {
        try {
            String androidId = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            if (androidId != null && !androidId.isEmpty() && !"9774d56d682e549c".equals(androidId)) {
                // "9774d56d682e549c" is a known buggy Android ID on some devices
                return androidId;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting Android ID", e);
        }
        return null;
    }
}