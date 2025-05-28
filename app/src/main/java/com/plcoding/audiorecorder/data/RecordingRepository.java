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

        // Log database info for debugging
        database.logDatabaseInfo();
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

        String query = "SELECT * FROM " + RecordingDatabase.TABLE_RECORDINGS + " ORDER BY " + RecordingDatabase.COLUMN_DATE + " DESC";
        Cursor cursor = db.rawQuery(query, null);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                Recording recording = createRecordingFromCursor(cursor);
                recordings.add(recording);
            }
            cursor.close();
        }

        db.close();
        Log.d(TAG, "Retrieved " + recordings.size() + " recordings from local database");
        return recordings;
    }

    public Recording getRecording(long id) {
        SQLiteDatabase db = database.getReadableDatabase();
        Recording recording = null;

        String query = "SELECT * FROM " + RecordingDatabase.TABLE_RECORDINGS + " WHERE " + RecordingDatabase.COLUMN_ID + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(id)});

        if (cursor != null && cursor.moveToFirst()) {
            recording = createRecordingFromCursor(cursor);
            cursor.close();
        }

        db.close();
        return recording;
    }

    private Recording createRecordingFromCursor(Cursor cursor) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow(RecordingDatabase.COLUMN_ID));
        String title = cursor.getString(cursor.getColumnIndexOrThrow(RecordingDatabase.COLUMN_TITLE));
        String filePath = cursor.getString(cursor.getColumnIndexOrThrow(RecordingDatabase.COLUMN_FILE_PATH));
        long duration = cursor.getLong(cursor.getColumnIndexOrThrow(RecordingDatabase.COLUMN_DURATION));
        long createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(RecordingDatabase.COLUMN_DATE));
        String type = cursor.getString(cursor.getColumnIndexOrThrow(RecordingDatabase.COLUMN_TYPE));
        String textContent = cursor.getString(cursor.getColumnIndexOrThrow(RecordingDatabase.COLUMN_TEXT_CONTENT));
        String deviceId = cursor.getString(cursor.getColumnIndexOrThrow(RecordingDatabase.COLUMN_DEVICE_ID));

        return new Recording(id, title, filePath, duration, createdAt, type, textContent, deviceId);
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

    // -------------- SERVER API OPERATIONS --------------

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

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

    private void addToPendingUploads(long id) {
        if (!pendingUploads.contains(id)) {
            pendingUploads.add(id);
            Log.d(TAG, "Added recording ID " + id + " to pending uploads. Total pending: " + pendingUploads.size());
        }
    }

    private void removeFromPendingUploads(long id) {
        pendingUploads.remove(Long.valueOf(id));
        Log.d(TAG, "Removed recording ID " + id + " from pending uploads. Total pending: " + pendingUploads.size());
    }

    private void uploadVoiceRecording(long localId, String title, File file, long duration) {
        if (apiService == null) {
            Log.e(TAG, "API service not initialized");
            addToPendingUploads(localId);
            return;
        }

        String deviceId = DeviceIdHelper.getDeviceId(context);

        Log.d(TAG, "Attempting to upload voice recording: ID=" + localId +
                ", Title=" + title +
                ", File=" + (file != null ? file.getAbsolutePath() : "null") +
                ", Size=" + (file != null ? file.length() : 0) +
                ", Device ID=" + deviceId);

        if (file == null || !file.exists() || !file.canRead()) {
            Log.e(TAG, "File is null, doesn't exist, or can't be read");
            addToPendingUploads(localId);
            return;
        }

        RequestBody requestFile = RequestBody.create(
                MediaType.parse("audio/mpeg"), file);

        MultipartBody.Part filePart = MultipartBody.Part.createFormData(
                "file_path", file.getName(), requestFile);

        RequestBody titleBody = RequestBody.create(
                MediaType.parse("text/plain"), title);
        RequestBody durationBody = RequestBody.create(
                MediaType.parse("text/plain"), String.valueOf(duration));
        RequestBody deviceIdBody = RequestBody.create(
                MediaType.parse("text/plain"), deviceId);
        RequestBody typeBody = RequestBody.create(
                MediaType.parse("text/plain"), "voice");

        Log.d(TAG, "Sending request to: " + RetrofitClient.getInstance(context).getServerUrl() + "recordings/");

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
                    addToPendingUploads(localId);
                }
            }

            @Override
            public void onFailure(Call<RecordingDto> call, Throwable t) {
                Log.e(TAG, "Error uploading voice recording", t);

                if (t instanceof IOException) {
                    Log.e(TAG, "Network error: " + t.getMessage());
                } else {
                    Log.e(TAG, "Conversion error: " + t.getMessage());
                }

                addToPendingUploads(localId);
            }
        });
    }

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

    public int getPendingUploadsCount() {
        return pendingUploads.size();
    }

    public void forceUploadPending(OperationCallback callback) {
        Log.d(TAG, "Forcing upload of " + pendingUploads.size() + " pending recordings");

        if (pendingUploads.isEmpty()) {
            if (callback != null) {
                callback.onSuccess();
            }
            return;
        }

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

            if (recording.isTextRecording()) {
                uploadTextRecordingWithCallback(id, recording.getTitle(), recording.getTextContent(), innerCallback);
            } else if (recording.isVoiceRecording() && recording.getFile() != null) {
                uploadVoiceRecordingWithCallback(id, recording.getTitle(), recording.getFile(), recording.getDuration(), innerCallback);
            } else {
                processed[0]++;
            }
        }
    }

    // -------------- CHAT MESSAGE METHODS --------------

    public long saveLocalChatMessage(long recordingId, String message, boolean isFromDevice, String serverMessageId) {
        SQLiteDatabase db = database.getWritableDatabase();
        long messageId = -1;

        try {
            // Check for duplicate by server message ID
            if (serverMessageId != null && !serverMessageId.isEmpty()) {
                if (chatMessageExistsByServerId(db, serverMessageId)) {
                    Log.d(TAG, "Chat message with server ID " + serverMessageId + " already exists, skipping");
                    return -1;
                }
            }

            // Check for near-duplicate content
            if (isDuplicateMessage(db, recordingId, message, System.currentTimeMillis(), 10000)) {
                Log.d(TAG, "Duplicate chat message detected, skipping: " + message.substring(0, Math.min(50, message.length())));
                return -1;
            }

            ContentValues values = new ContentValues();
            values.put(RecordingDatabase.COLUMN_MSG_RECORDING_ID, recordingId);
            values.put(RecordingDatabase.COLUMN_MSG_CONTENT, message);
            values.put(RecordingDatabase.COLUMN_MSG_IS_FROM_DEVICE, isFromDevice ? 1 : 0);
            values.put(RecordingDatabase.COLUMN_MSG_TIMESTAMP, System.currentTimeMillis());
            values.put(RecordingDatabase.COLUMN_MSG_IS_SYNCED, serverMessageId != null ? 1 : 0);
            values.put(RecordingDatabase.COLUMN_MSG_SERVER_ID, serverMessageId);
            values.put(RecordingDatabase.COLUMN_MSG_SENDER_TYPE, isFromDevice ? "device" : "admin");

            messageId = db.insert(RecordingDatabase.TABLE_CHAT_MESSAGES, null, values);
            Log.d(TAG, "Saved chat message: ID=" + messageId + ", content='" + message.substring(0, Math.min(50, message.length())) + "...'");

        } catch (Exception e) {
            Log.e(TAG, "Error saving chat message", e);
        } finally {
            db.close();
        }

        return messageId;
    }

    public List<LocalChatMessage> getLocalChatMessages(long recordingId) {
        List<LocalChatMessage> messages = new ArrayList<>();
        SQLiteDatabase db = database.getReadableDatabase();

        String query = "SELECT * FROM " + RecordingDatabase.TABLE_CHAT_MESSAGES +
                " WHERE " + RecordingDatabase.COLUMN_MSG_RECORDING_ID + " = ?" +
                " ORDER BY " + RecordingDatabase.COLUMN_MSG_TIMESTAMP + " ASC";

        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(recordingId)});

        if (cursor != null) {
            while (cursor.moveToNext()) {
                try {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(RecordingDatabase.COLUMN_MSG_ID));
                    String message = cursor.getString(cursor.getColumnIndexOrThrow(RecordingDatabase.COLUMN_MSG_CONTENT));
                    boolean isFromDevice = cursor.getInt(cursor.getColumnIndexOrThrow(RecordingDatabase.COLUMN_MSG_IS_FROM_DEVICE)) == 1;
                    long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(RecordingDatabase.COLUMN_MSG_TIMESTAMP));
                    boolean isSynced = cursor.getInt(cursor.getColumnIndexOrThrow(RecordingDatabase.COLUMN_MSG_IS_SYNCED)) == 1;
                    String serverMessageId = cursor.getString(cursor.getColumnIndexOrThrow(RecordingDatabase.COLUMN_MSG_SERVER_ID));

                    // Skip empty messages and ping/pong
                    if (message == null || message.trim().isEmpty()) {
                        continue;
                    }
                    String msgLower = message.toLowerCase().trim();
                    if (msgLower.equals("ping") || msgLower.equals("pong")) {
                        continue;
                    }

                    LocalChatMessage chatMessage = new LocalChatMessage(id, recordingId, message, isFromDevice, timestamp, isSynced, serverMessageId);
                    messages.add(chatMessage);

                } catch (Exception e) {
                    Log.e(TAG, "Error parsing chat message from cursor", e);
                }
            }
            cursor.close();
        }

        db.close();
        Log.d(TAG, "Retrieved " + messages.size() + " chat messages for recording " + recordingId);
        return messages;
    }

    public void updateChatMessageWithServerId(long localMessageId, String serverMessageId) {
        SQLiteDatabase db = database.getWritableDatabase();

        try {
            ContentValues values = new ContentValues();
            values.put(RecordingDatabase.COLUMN_MSG_SERVER_ID, serverMessageId);
            values.put(RecordingDatabase.COLUMN_MSG_IS_SYNCED, 1);

            int rowsUpdated = db.update(
                    RecordingDatabase.TABLE_CHAT_MESSAGES,
                    values,
                    RecordingDatabase.COLUMN_MSG_ID + " = ?",
                    new String[]{String.valueOf(localMessageId)}
            );

            Log.d(TAG, "Updated " + rowsUpdated + " chat message(s) with server ID: " + serverMessageId);

        } catch (Exception e) {
            Log.e(TAG, "Error updating chat message with server ID", e);
        } finally {
            db.close();
        }
    }

    public int getUnsyncedChatMessageCount() {
        SQLiteDatabase db = database.getReadableDatabase();
        int count = 0;

        String query = "SELECT COUNT(*) FROM " + RecordingDatabase.TABLE_CHAT_MESSAGES +
                " WHERE " + RecordingDatabase.COLUMN_MSG_IS_SYNCED + " = 0";

        Cursor cursor = db.rawQuery(query, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            cursor.close();
        }

        db.close();
        return count;
    }

    public void clearAllChatMessages() {
        SQLiteDatabase db = database.getWritableDatabase();
        try {
            int deletedRows = db.delete(RecordingDatabase.TABLE_CHAT_MESSAGES, null, null);
            Log.d(TAG, "Cleared " + deletedRows + " chat messages");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing chat messages", e);
        } finally {
            db.close();
        }
    }

    public boolean hasUnsentMessages(long recordingId) {
        SQLiteDatabase db = database.getReadableDatabase();
        boolean hasUnsent = false;
        Cursor cursor = null;

        try {
            cursor = db.query(
                    RecordingDatabase.TABLE_CHAT_MESSAGES,
                    new String[]{"COUNT(*)"},
                    RecordingDatabase.COLUMN_MSG_RECORDING_ID + " = ? AND " +
                            RecordingDatabase.COLUMN_MSG_IS_FROM_DEVICE + " = 1 AND " +
                            RecordingDatabase.COLUMN_MSG_IS_SYNCED + " = 0",
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
            db.close();
        }

        return hasUnsent;
    }

    // -------------- HELPER METHODS --------------

    private boolean chatMessageExistsByServerId(SQLiteDatabase db, String serverMessageId) {
        String query = "SELECT 1 FROM " + RecordingDatabase.TABLE_CHAT_MESSAGES +
                " WHERE " + RecordingDatabase.COLUMN_MSG_SERVER_ID + " = ? LIMIT 1";

        Cursor cursor = db.rawQuery(query, new String[]{serverMessageId});
        boolean exists = cursor != null && cursor.moveToFirst();
        if (cursor != null) {
            cursor.close();
        }

        return exists;
    }

    private boolean isDuplicateMessage(SQLiteDatabase db, long recordingId, String message, long timestamp, long timeWindowMs) {
        String query = "SELECT 1 FROM " + RecordingDatabase.TABLE_CHAT_MESSAGES +
                " WHERE " + RecordingDatabase.COLUMN_MSG_RECORDING_ID + " = ?" +
                " AND " + RecordingDatabase.COLUMN_MSG_CONTENT + " = ?" +
                " AND ABS(" + RecordingDatabase.COLUMN_MSG_TIMESTAMP + " - ?) < ?" +
                " LIMIT 1";

        Cursor cursor = db.rawQuery(query, new String[]{
                String.valueOf(recordingId),
                message,
                String.valueOf(timestamp),
                String.valueOf(timeWindowMs)
        });

        boolean isDuplicate = cursor != null && cursor.moveToFirst();
        if (cursor != null) {
            cursor.close();
        }

        return isDuplicate;
    }

    private void uploadVoiceRecordingWithCallback(long id, String title, File file, long duration, OperationCallback callback) {
        // Implementation similar to uploadVoiceRecording but with callback support
        // [Omitted for brevity]
    }

    private void uploadTextRecordingWithCallback(long id, String title, String textContent, OperationCallback callback) {
        // Implementation similar to uploadTextRecording but with callback support
        // [Omitted for brevity]
    }
}