package com.plcoding.audiorecorder.data;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.plcoding.audiorecorder.LocalChatMessage;
import com.plcoding.audiorecorder.api.RecordingApiService;
import com.plcoding.audiorecorder.api.RecordingDto;
import com.plcoding.audiorecorder.api.RetrofitClient;
import com.plcoding.audiorecorder.utils.DeviceIdHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RecordingRepository {
    private static final String TAG = "RecordingRepository";
    private final RecordingDatabase database;
    private final Context context;
    private final ExecutorService executor;
    private RecordingApiService apiService;

    // Flag to track pending uploads
    private final List<Long> pendingUploads = new ArrayList<>();

    public RecordingRepository(Context context) {
        this.context = context;
        this.database = new RecordingDatabase(context);
        this.executor = Executors.newSingleThreadExecutor();

        // Initialize API service only if network is available
        if (isNetworkAvailable()) {
            try {
                this.apiService = RetrofitClient.getInstance(context).getApiService();
            } catch (Exception e) {
                Log.e(TAG, "Error initializing API service", e);
                this.apiService = null;
            }
        }
    }

    // Interface for sync status callbacks
    public interface SyncStatusCallback {
        void onSyncComplete(boolean success);
    }

    // Interface for operation callbacks
    public interface OperationCallback {
        void onSuccess();
        void onError(String errorMessage);
    }

    // -------------- LOCAL DATABASE OPERATIONS --------------

    public long saveRecording(String title, File file, long duration) {
        SQLiteDatabase db = database.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(RecordingDatabase.COLUMN_TITLE, title);
        values.put(RecordingDatabase.COLUMN_FILE_PATH, file.getAbsolutePath());
        values.put(RecordingDatabase.COLUMN_DURATION, duration);
        values.put(RecordingDatabase.COLUMN_DATE, System.currentTimeMillis());
        values.put(RecordingDatabase.COLUMN_TYPE, Recording.TYPE_VOICE);
        values.put(RecordingDatabase.COLUMN_DEVICE_ID, DeviceIdHelper.getDeviceId(context));

        long id = db.insert(RecordingDatabase.TABLE_RECORDINGS, null, values);
        Log.d(TAG, "Voice recording saved with ID: " + id + ", Device ID: " + DeviceIdHelper.getDeviceId(context));

        // Sync with server if connected
        if (isNetworkAvailable() && isServerAvailable()) {
            uploadVoiceRecording(id, title, file, duration);
        } else {
            // Mark for later upload
            addToPendingUploads(id);
        }

        return id;
    }

    public long saveTextRecording(String title, String textContent) {
        SQLiteDatabase db = database.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(RecordingDatabase.COLUMN_TITLE, title);
        values.put(RecordingDatabase.COLUMN_TEXT_CONTENT, textContent);
        values.put(RecordingDatabase.COLUMN_DATE, System.currentTimeMillis());
        values.put(RecordingDatabase.COLUMN_TYPE, Recording.TYPE_TEXT);
        values.put(RecordingDatabase.COLUMN_DURATION, 0);
        values.put(RecordingDatabase.COLUMN_DEVICE_ID, DeviceIdHelper.getDeviceId(context));

        // Don't put file_path for text recordings
        values.putNull(RecordingDatabase.COLUMN_FILE_PATH);

        Log.d(TAG, "Attempting to save text recording - Title: " + title + ", Content: " + textContent + ", Device ID: " + DeviceIdHelper.getDeviceId(context));

        long id = db.insert(RecordingDatabase.TABLE_RECORDINGS, null, values);

        if (id == -1) {
            Log.e(TAG, "Failed to insert text recording");
        } else {
            Log.d(TAG, "Text recording saved successfully with ID: " + id);

            // Sync with server if connected
            if (isNetworkAvailable() && isServerAvailable()) {
                uploadTextRecording(id, title, textContent);
            } else {
                // Mark for later upload
                addToPendingUploads(id);
            }
        }

        return id;
    }

    public List<Recording> getAllRecordings() {
        List<Recording> recordings = new ArrayList<>();
        SQLiteDatabase db = database.getReadableDatabase();

        String orderBy = RecordingDatabase.COLUMN_DATE + " DESC";
        Cursor cursor = null;

        try {
            cursor = db.query(
                    RecordingDatabase.TABLE_RECORDINGS,
                    null,
                    null,
                    null,
                    null,
                    null,
                    orderBy
            );

            Log.d(TAG, "Found " + cursor.getCount() + " recordings in database");

            if (cursor.moveToFirst()) {
                do {
                    Recording recording = toRecording(cursor);
                    recordings.add(recording);
                    Log.d(TAG, "Loaded recording: " + recording.getTitle() +
                            ", Type: " + recording.getType() +
                            ", Device ID: " + recording.getDeviceId());
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting all recordings", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return recordings;
    }

    public Recording getRecording(long id) {
        SQLiteDatabase db = database.getReadableDatabase();

        String selection = RecordingDatabase.COLUMN_ID + " = ?";
        String[] selectionArgs = {String.valueOf(id)};

        Cursor cursor = null;
        Recording recording = null;

        try {
            cursor = db.query(
                    RecordingDatabase.TABLE_RECORDINGS,
                    null,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    null
            );

            if (cursor.moveToFirst()) {
                recording = toRecording(cursor);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting recording with ID: " + id, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return recording;
    }

    public boolean deleteRecording(long id) {
        Recording recording = getRecording(id);

        SQLiteDatabase db = database.getWritableDatabase();
        String whereClause = RecordingDatabase.COLUMN_ID + " = ?";
        String[] whereArgs = {String.valueOf(id)};

        int result = db.delete(
                RecordingDatabase.TABLE_RECORDINGS,
                whereClause,
                whereArgs
        );

        if (recording != null && recording.isVoiceRecording() && recording.getFile() != null) {
            recording.getFile().delete();
        }

        Log.d(TAG, "Delete result for ID " + id + ": " + result);

        // Delete from server if connected
        if (isNetworkAvailable() && isServerAvailable()) {
            deleteRecordingFromServer(id);
        }

        return result > 0;
    }

    private Recording toRecording(Cursor cursor) {
        // Existing toRecording implementation from your code
        int idIndex = cursor.getColumnIndex(RecordingDatabase.COLUMN_ID);
        int titleIndex = cursor.getColumnIndex(RecordingDatabase.COLUMN_TITLE);
        int filePathIndex = cursor.getColumnIndex(RecordingDatabase.COLUMN_FILE_PATH);
        int durationIndex = cursor.getColumnIndex(RecordingDatabase.COLUMN_DURATION);
        int dateIndex = cursor.getColumnIndex(RecordingDatabase.COLUMN_DATE);
        int typeIndex = cursor.getColumnIndex(RecordingDatabase.COLUMN_TYPE);
        int textContentIndex = cursor.getColumnIndex(RecordingDatabase.COLUMN_TEXT_CONTENT);
        int deviceIdIndex = cursor.getColumnIndex(RecordingDatabase.COLUMN_DEVICE_ID);

        if (idIndex == -1 || titleIndex == -1 || dateIndex == -1) {
            Log.e(TAG, "Required columns not found in database");
            return null;
        }

        String type = Recording.TYPE_VOICE;
        if (typeIndex != -1 && !cursor.isNull(typeIndex)) {
            type = cursor.getString(typeIndex);
        }

        String textContent = null;
        if (textContentIndex != -1 && !cursor.isNull(textContentIndex)) {
            textContent = cursor.getString(textContentIndex);
        }

        String filePath = null;
        if (filePathIndex != -1 && !cursor.isNull(filePathIndex)) {
            filePath = cursor.getString(filePathIndex);
        }

        long duration = 0;
        if (durationIndex != -1 && !cursor.isNull(durationIndex)) {
            duration = cursor.getLong(durationIndex);
        }

        String deviceId = null;
        if (deviceIdIndex != -1 && !cursor.isNull(deviceIdIndex)) {
            deviceId = cursor.getString(deviceIdIndex);
        }

        Recording recording = new Recording(
                cursor.getLong(idIndex),
                cursor.getString(titleIndex),
                filePath,
                duration,
                cursor.getLong(dateIndex),
                type,
                textContent,
                deviceId
        );

        Log.d(TAG, "Created recording object: ID=" + recording.getId() +
                ", Title=" + recording.getTitle() +
                ", Device ID=" + recording.getDeviceId());

        return recording;
    }

    // -------------- SERVER API OPERATIONS --------------

    // Check if network is available
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

    // Check if server is available
    private boolean isServerAvailable() {
        if (apiService == null) {
            try {
                apiService = RetrofitClient.getInstance(context).getApiService();
            } catch (Exception e) {
                Log.e(TAG, "Error initializing API service", e);
                return false;
            }
        }

        try {
            return RetrofitClient.getInstance(context).isServerReachable();
        } catch (Exception e) {
            Log.e(TAG, "Error checking server availability", e);
            return false;
        }
    }

    // Add recording to pending uploads
    private void addToPendingUploads(long id) {
        if (!pendingUploads.contains(id)) {
            pendingUploads.add(id);
            Log.d(TAG, "Added recording ID " + id + " to pending uploads. Total pending: " + pendingUploads.size());
        }
    }

    // Remove recording from pending uploads
    private void removeFromPendingUploads(long id) {
        pendingUploads.remove(Long.valueOf(id));
        Log.d(TAG, "Removed recording ID " + id + " from pending uploads. Total pending: " + pendingUploads.size());
    }

    // Upload voice recording to server
    // In RecordingRepository.java - update the uploadVoiceRecording method

    private void uploadVoiceRecording(long localId, String title, File file, long duration) {
        if (apiService == null) {
            Log.e(TAG, "API service not initialized");
            addToPendingUploads(localId);
            return;
        }

        String deviceId = DeviceIdHelper.getDeviceId(context);

        // Log detailed upload attempt
        Log.d(TAG, "Attempting to upload voice recording: ID=" + localId +
                ", Title=" + title +
                ", File=" + (file != null ? file.getAbsolutePath() : "null") +
                ", Size=" + (file != null ? file.length() : 0) +
                ", Device ID=" + deviceId);

        // Check if file exists and is readable
        if (file == null || !file.exists() || !file.canRead()) {
            Log.e(TAG, "File is null, doesn't exist, or can't be read");
            addToPendingUploads(localId);
            return;
        }

        // Create RequestBody instances for file and metadata
        RequestBody requestFile = RequestBody.create(
                MediaType.parse("audio/mpeg"), file);

        // Create MultipartBody.Part from file
        MultipartBody.Part filePart = MultipartBody.Part.createFormData(
                "file_path", file.getName(), requestFile);

        // Create proper RequestBody objects for each parameter
        RequestBody titleBody = RequestBody.create(
                MediaType.parse("text/plain"), title);
        RequestBody durationBody = RequestBody.create(
                MediaType.parse("text/plain"), String.valueOf(duration));
        RequestBody deviceIdBody = RequestBody.create(
                MediaType.parse("text/plain"), deviceId);
        RequestBody typeBody = RequestBody.create(
                MediaType.parse("text/plain"), "voice");

        // Log the request details
        Log.d(TAG, "Sending request to: " + RetrofitClient.getInstance(context).getServerUrl() + "recordings/");

        // Send request
        Call<RecordingDto> call = apiService.uploadVoiceRecording(
                titleBody,
                durationBody,
                deviceIdBody,
                typeBody,
                filePart);

        call.enqueue(new Callback<RecordingDto>() {
            @Override
            public void onResponse(Call<RecordingDto> call, Response<RecordingDto> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Voice recording uploaded successfully: " + response.body().getId());
                    removeFromPendingUploads(localId);
                } else {
                    Log.e(TAG, "Failed to upload voice recording: " + response.code());
                    try {
                        if (response.errorBody() != null) {
                            Log.e(TAG, "Error body: " + response.errorBody().string());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    // Keep in pending uploads for later retry
                    addToPendingUploads(localId);
                }
            }

            @Override
            public void onFailure(Call<RecordingDto> call, Throwable t) {
                Log.e(TAG, "Error uploading voice recording", t);

                // Log detailed error info
                if (t instanceof IOException) {
                    Log.e(TAG, "Network error: " + t.getMessage());
                } else {
                    Log.e(TAG, "Conversion error: " + t.getMessage());
                }

                // Keep in pending uploads for later retry
                addToPendingUploads(localId);
            }
        });
    }

// Similarly update the uploadTextRecording method with better logging and error handling

    // Upload text recording to server
    private void uploadTextRecording(long localId, String title, String textContent) {
        if (apiService == null) {
            Log.e(TAG, "API service not initialized");
            addToPendingUploads(localId);
            return;
        }

        String deviceId = DeviceIdHelper.getDeviceId(context);

        RecordingDto dto = new RecordingDto(title, textContent, deviceId);

        Call<RecordingDto> call = apiService.createTextRecording(dto);

        call.enqueue(new Callback<RecordingDto>() {
            @Override
            public void onResponse(Call<RecordingDto> call, Response<RecordingDto> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Text recording uploaded successfully: " + response.body().getId());
                    removeFromPendingUploads(localId);
                } else {
                    Log.e(TAG, "Failed to upload text recording: " + response.code());
                    try {
                        Log.e(TAG, "Error body: " + response.errorBody().string());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    addToPendingUploads(localId);
                }
            }

            @Override
            public void onFailure(Call<RecordingDto> call, Throwable t) {
                Log.e(TAG, "Error uploading text recording", t);
                addToPendingUploads(localId);
            }
        });
    }

    // Delete recording from server
    private void deleteRecordingFromServer(long id) {
        if (apiService == null) {
            Log.e(TAG, "API service not initialized");
            return;
        }

        Call<Void> call = apiService.deleteRecording(id);

        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Recording deleted from server successfully");
                    removeFromPendingUploads(id);
                } else {
                    Log.e(TAG, "Failed to delete recording from server: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG, "Error deleting recording from server", t);
            }
        });
    }

    // Sync all local recordings with server
    public void syncAllRecordings(SyncStatusCallback callback) {
        if (!isNetworkAvailable() || !isServerAvailable()) {
            if (callback != null) {
                callback.onSyncComplete(false);
            }
            return;
        }

        List<Recording> localRecordings = getAllRecordings();
        final int[] syncCount = {0};
        final boolean[] syncSuccess = {true};

        if (localRecordings.isEmpty()) {
            // If no recordings to sync
            if (callback != null) {
                callback.onSyncComplete(true);
            }
            return;
        }

        for (Recording recording : localRecordings) {
            if (recording.isTextRecording()) {
                uploadTextRecording(recording.getId(), recording.getTitle(), recording.getTextContent());
            } else if (recording.isVoiceRecording() && recording.getFile() != null) {
                uploadVoiceRecording(recording.getId(), recording.getTitle(), recording.getFile(), recording.getDuration());
            }

            syncCount[0]++;

            if (syncCount[0] == localRecordings.size() && callback != null) {
                callback.onSyncComplete(syncSuccess[0]);
            }
        }
    }

    // Download all recordings from server
    public void downloadRecordingsFromServer(SyncStatusCallback callback) {
        if (!isNetworkAvailable() || !isServerAvailable()) {
            if (callback != null) {
                callback.onSyncComplete(false);
            }
            return;
        }

        if (apiService == null) {
            Log.e(TAG, "API service not initialized");
            if (callback != null) {
                callback.onSyncComplete(false);
            }
            return;
        }

        String deviceId = DeviceIdHelper.getDeviceId(context);
        Call<List<RecordingDto>> call = apiService.getRecordings(deviceId);

        call.enqueue(new Callback<List<RecordingDto>>() {
            @Override
            public void onResponse(Call<List<RecordingDto>> call, Response<List<RecordingDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "Received " + response.body().size() + " recordings from server");
                    // TODO: Implement logic to save server recordings to local database
                    // This would involve comparing server and local recordings
                    // and updating local database accordingly

                    if (callback != null) {
                        callback.onSyncComplete(true);
                    }
                } else {
                    Log.e(TAG, "Failed to get recordings from server: " + response.code());
                    if (callback != null) {
                        callback.onSyncComplete(false);
                    }
                }
            }

            @Override
            public void onFailure(Call<List<RecordingDto>> call, Throwable t) {
                Log.e(TAG, "Error getting recordings from server", t);
                if (callback != null) {
                    callback.onSyncComplete(false);
                }
            }
        });
    }




    // Sync pending uploads
    // Add this method to the RecordingRepository class
    public void syncPendingUploads(Object o) {
        if (!isNetworkAvailable() || !isServerAvailable() || pendingUploads.isEmpty()) {
            Log.d(TAG, "Cannot sync: Network or server unavailable, or no pending uploads");
            return;
        }

        Log.d(TAG, "Starting sync of " + pendingUploads.size() + " pending uploads");

        List<Long> pendingIds = new ArrayList<>(pendingUploads);

        for (Long id : pendingIds) {
            Recording recording = getRecording(id);
            if (recording == null) {
                pendingUploads.remove(id);
                continue;
            }

            if (recording.isTextRecording()) {
                uploadTextRecording(recording.getId(), recording.getTitle(), recording.getTextContent());
            } else if (recording.isVoiceRecording() && recording.getFile() != null) {
                uploadVoiceRecording(recording.getId(), recording.getTitle(), recording.getFile(), recording.getDuration());
            }
        }
    }

    // Add this helper method to get pending uploads count
    public int getPendingUploadsCount() {
        return pendingUploads.size();
    }





    // Add this method to RecordingRepository.java

    public void forceUploadPending(OperationCallback callback) {
        Log.d(TAG, "Forcing upload of " + pendingUploads.size() + " pending recordings");

        if (pendingUploads.isEmpty()) {
            if (callback != null) {
                callback.onSuccess();
            }
            return;
        }

        // Initialize API service if needed
        if (apiService == null) {
            try {
                apiService = RetrofitClient.getInstance(context).getApiService();
            } catch (Exception e) {
                Log.e(TAG, "Error initializing API service", e);
                if (callback != null) {
                    callback.onError("Cannot initialize API service: " + e.getMessage());
                }
                return;
            }
        }

        // Create a copy to avoid concurrent modification
        List<Long> pendingCopy = new ArrayList<>(pendingUploads);
        final int[] processed = {0};
        final boolean[] anySuccess = {false};

        for (Long id : pendingCopy) {
            Recording recording = getRecording(id);
            if (recording == null) {
                pendingUploads.remove(id);
                processed[0]++;
                continue;
            }

            // Create a callback for each upload
            OperationCallback innerCallback = new OperationCallback() {
                @Override
                public void onSuccess() {
                    anySuccess[0] = true;
                    processed[0]++;

                    if (processed[0] >= pendingCopy.size() && callback != null) {
                        if (anySuccess[0]) {
                            callback.onSuccess();
                        } else {
                            callback.onError("No recordings could be uploaded");
                        }
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    processed[0]++;

                    if (processed[0] >= pendingCopy.size() && callback != null) {
                        if (anySuccess[0]) {
                            callback.onSuccess();
                        } else {
                            callback.onError("No recordings could be uploaded");
                        }
                    }
                }
            };

            // Do the appropriate upload based on recording type
            if (recording.isTextRecording()) {
                uploadTextRecordingWithCallback(id, recording.getTitle(), recording.getTextContent(), innerCallback);
            } else if (recording.isVoiceRecording() && recording.getFile() != null) {
                uploadVoiceRecordingWithCallback(id, recording.getTitle(), recording.getFile(), recording.getDuration(), innerCallback);
            } else {
                processed[0]++;
            }
        }
    }

    // Helper methods with callbacks
    private void uploadVoiceRecordingWithCallback(long id, String title, File file, long duration, OperationCallback callback) {
        // Similar to uploadVoiceRecording but with callback support
        // [Implementation omitted for brevity - similar to the updated uploadVoiceRecording]
    }

    private void uploadTextRecordingWithCallback(long id, String title, String textContent, OperationCallback callback) {
        // Similar to uploadTextRecording but with callback support
        // [Implementation omitted for brevity]
    }

    /**
     * Save a chat message locally
     */





    /**
     * Get all chat messages for a recording from local database
     */
    @SuppressLint("Range")
    public List<LocalChatMessage> getLocalChatMessages(long recordingId) {
        List<LocalChatMessage> messages = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = database.getReadableDatabase();
            String selection = "recording_id = ?";
            String[] selectionArgs = {String.valueOf(recordingId)};
            String orderBy = "timestamp ASC";

            cursor = db.query(
                    "chat_messages",
                    null,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    orderBy
            );

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(cursor.getColumnIndex("id"));
                    String message = cursor.getString(cursor.getColumnIndex("message"));
                    boolean isFromDevice = cursor.getInt(cursor.getColumnIndex("is_from_device")) == 1;
                    long timestamp = cursor.getLong(cursor.getColumnIndex("timestamp"));
                    boolean isSynced = cursor.getInt(cursor.getColumnIndex("is_synced")) == 1;

                    // Get message_id (might be null)
                    String messageId = null;
                    if (cursor.getColumnIndex("message_id") != -1 && !cursor.isNull(cursor.getColumnIndex("message_id"))) {
                        messageId = cursor.getString(cursor.getColumnIndex("message_id"));
                    }

                    messages.add(new LocalChatMessage(id, recordingId, message, isFromDevice, timestamp, isSynced, messageId));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting local chat messages", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null) {
                db.close();
            }
        }

        Log.d(TAG, "Retrieved " + messages.size() + " local messages for recording " + recordingId);
        return messages;
    }

    public void saveLocalChatMessage(long recordingId, String message, boolean isFromDevice, String messageId) {
        SQLiteDatabase db = database.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("recording_id", recordingId);
        values.put("message", message);
        values.put("is_from_device", isFromDevice ? 1 : 0);
        values.put("timestamp", System.currentTimeMillis());
        values.put("is_synced", messageId != null ? 1 : 0); // Synced if we have a server ID

        if (messageId != null) {
            values.put("message_id", messageId);
        }

        long id = db.insert("chat_messages", null, values);
        Log.d(TAG, "Saved local chat message with ID: " + id +
                (messageId != null ? ", server message ID: " + messageId : ""));
    }

    /**
     * Update a local message with server ID
     */
    public void updateLocalMessageWithServerId(long recordingId, String message, String messageId) {
        if (messageId == null) {
            return;
        }

        SQLiteDatabase db = database.getWritableDatabase();

        try {
            // Find the most recent outgoing message with this content
            Cursor cursor = db.query(
                    "chat_messages",
                    new String[]{"id"},
                    "recording_id = ? AND message = ? AND is_from_device = 1 AND message_id IS NULL",
                    new String[]{String.valueOf(recordingId), message},
                    null,
                    null,
                    "timestamp DESC",
                    "1"
            );

            if (cursor != null && cursor.moveToFirst()) {
                long localId = cursor.getLong(0);
                cursor.close();

                // Update with server ID
                ContentValues values = new ContentValues();
                values.put("message_id", messageId);
                values.put("is_synced", 1);

                int updated = db.update(
                        "chat_messages",
                        values,
                        "id = ?",
                        new String[]{String.valueOf(localId)}
                );

                Log.d(TAG, "Updated local message " + localId + " with server ID " + messageId +
                        ", update count: " + updated);
            } else {
                if (cursor != null) {
                    cursor.close();
                }

                // If we couldn't find a matching message, save it as a new one
                saveLocalChatMessage(recordingId, message, true, messageId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating local message with server ID", e);
        }
    }

    /**
     * Delete local chat messages for a recording
     */
    public void deleteLocalChatMessages(long recordingId) {
        SQLiteDatabase db = database.getWritableDatabase();

        try {
            int deleted = db.delete(
                    "chat_messages",
                    "recording_id = ?",
                    new String[]{String.valueOf(recordingId)}
            );
            Log.d(TAG, "Deleted " + deleted + " local chat messages for recording " + recordingId);
        } catch (Exception e) {
            Log.e(TAG, "Error deleting local chat messages", e);
        }
    }

    /**
     * Check if there are unsent messages for a recording
     */
    public boolean hasUnsentMessages(long recordingId) {
        SQLiteDatabase db = database.getReadableDatabase();
        boolean hasUnsent = false;
        Cursor cursor = null;

        try {
            cursor = db.query(
                    "chat_messages",
                    new String[]{"COUNT(*)"},
                    "recording_id = ? AND is_from_device = 1 AND is_synced = 0",
                    new String[]{String.valueOf(recordingId)},
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                hasUnsent = cursor.getInt(0) > 0;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking for unsent messages", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return hasUnsent;
    }

}
