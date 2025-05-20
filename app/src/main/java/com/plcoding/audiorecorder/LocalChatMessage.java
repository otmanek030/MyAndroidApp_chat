package com.plcoding.audiorecorder;

/**
 * Model class for storing chat messages in the local database
 */
public class LocalChatMessage {
    private long id;
    private long recordingId;
    private String message;
    private boolean isFromDevice;
    private long timestamp;
    private boolean isSynced;
    private String messageId;

    public LocalChatMessage(long id, long recordingId, String message,
                            boolean isFromDevice, long timestamp,
                            boolean isSynced, String messageId) {
        this.id = id;
        this.recordingId = recordingId;
        this.message = message;
        this.isFromDevice = isFromDevice;
        this.timestamp = timestamp;
        this.isSynced = isSynced;
        this.messageId = messageId;
    }

    // Getters
    public long getId() {
        return id;
    }

    public long getRecordingId() {
        return recordingId;
    }

    public String getMessage() {
        return message;
    }

    public boolean isFromDevice() {
        return isFromDevice;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isSynced() {
        return isSynced;
    }

    public String getMessageId() {
        return messageId;
    }
}