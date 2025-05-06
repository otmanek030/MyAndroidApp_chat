package com.plcoding.audiorecorder.data;

import android.util.Log;
import java.io.File;

public class Recording {
    private static final String TAG = "Recording";

    public static final String TYPE_VOICE = "voice";
    public static final String TYPE_TEXT = "text";

    private long id;
    private String title;
    private String filePath;
    private long duration;
    private long createdAt;
    private String type;
    private String textContent;
    private String deviceId;

    public Recording(long id, String title, String filePath, long duration, long createdAt, String type, String textContent, String deviceId) {
        this.id = id;
        this.title = title;
        this.filePath = filePath;
        this.duration = duration;
        this.createdAt = createdAt;
        this.type = type;
        this.textContent = textContent;
        this.deviceId = deviceId;

        Log.d(TAG, "Created Recording: id=" + id + ", title=" + title + ", type=" + type + ", deviceId=" + deviceId);
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getFilePath() {
        return filePath;
    }

    public long getDuration() {
        return duration;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getType() {
        return type;
    }

    public String getTextContent() {
        return textContent;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public File getFile() {
        if (filePath != null && !filePath.isEmpty()) {
            return new File(filePath);
        }
        return null;
    }

    public boolean isVoiceRecording() {
        return TYPE_VOICE.equals(type);
    }

    public boolean isTextRecording() {
        return TYPE_TEXT.equals(type);
    }

    @Override
    public String toString() {
        return "Recording{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", type='" + type + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", textContent='" + textContent + '\'' +
                ", filePath='" + filePath + '\'' +
                ", duration=" + duration +
                ", createdAt=" + createdAt +
                '}';
    }
}