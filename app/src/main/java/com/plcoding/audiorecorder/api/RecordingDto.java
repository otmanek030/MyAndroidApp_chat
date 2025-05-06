package com.plcoding.audiorecorder.api;

import com.google.gson.annotations.SerializedName;

public class RecordingDto {
    private Long id;
    private String title;

    @SerializedName("file_path")
    private String filePath;

    @SerializedName("text_content")
    private String textContent;

    private long duration;

    @SerializedName("created_at")
    private String createdAt;

    private String type;

    @SerializedName("device_id")
    private String deviceId;

    @SerializedName("is_voice_recording")
    private boolean isVoiceRecording;

    @SerializedName("is_text_recording")
    private boolean isTextRecording;

    // Default constructor for Retrofit
    public RecordingDto() {
    }

    // Constructor for creating text recordings
    public RecordingDto(String title, String textContent, String deviceId) {
        this.title = title;
        this.textContent = textContent;
        this.deviceId = deviceId;
        this.type = "text";
        this.duration = 0;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public boolean isVoiceRecording() {
        return isVoiceRecording;
    }

    public void setVoiceRecording(boolean voiceRecording) {
        isVoiceRecording = voiceRecording;
    }

    public boolean isTextRecording() {
        return isTextRecording;
    }

    public void setTextRecording(boolean textRecording) {
        isTextRecording = textRecording;
    }
}