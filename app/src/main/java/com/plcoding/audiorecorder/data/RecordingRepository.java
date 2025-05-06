package com.plcoding.audiorecorder.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

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
    private final RecordingApiService apiService;

    public RecordingRepository(Context context) {
        this.context = context;
        this.database = new RecordingDatabase(context);
        this.executor = Executors.newSingleThreadExecutor();
        this.apiService = RetrofitClient.getInstance().getRecordingApi();
    }

    // Interface for sync status callbacks
    public interface SyncStatusCallback {
        void onSyncComplete(boolean success);
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
        if (isNetworkAvailable()) {
            uploadVoiceRecording(id, title, file, duration);
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
            if (isNetworkAvailable()) {
                uploadTextRecording(id, title, textContent);
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
        if (isNetworkAvailable()) {
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

    // Upload voice recording to server
    private void uploadVoiceRecording(long localId, String title, File file, long duration) {
        String deviceId = DeviceIdHelper.getDeviceId(context);

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
                } else {
                    Log.e(TAG, "Failed to upload voice recording: " + response.code());
                    try {
                        Log.e(TAG, "Error body: " + response.errorBody().string());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<RecordingDto> call, Throwable t) {
                Log.e(TAG, "Error uploading voice recording", t);
            }
        });
    }

    // Upload text recording to server
    private void uploadTextRecording(long localId, String title, String textContent) {
        String deviceId = DeviceIdHelper.getDeviceId(context);

        RecordingDto dto = new RecordingDto(title, textContent, deviceId);

        Call<RecordingDto> call = apiService.createTextRecording(dto);

        call.enqueue(new Callback<RecordingDto>() {
            @Override
            public void onResponse(Call<RecordingDto> call, Response<RecordingDto> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Text recording uploaded successfully: " + response.body().getId());
                } else {
                    Log.e(TAG, "Failed to upload text recording: " + response.code());
                    try {
                        Log.e(TAG, "Error body: " + response.errorBody().string());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<RecordingDto> call, Throwable t) {
                Log.e(TAG, "Error uploading text recording", t);
            }
        });
    }

    // Delete recording from server
    private void deleteRecordingFromServer(long id) {
        Call<Void> call = apiService.deleteRecording(id);

        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Recording deleted from server successfully");
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
        if (!isNetworkAvailable()) {
            if (callback != null) {
                callback.onSyncComplete(false);
            }
            return;
        }

        List<Recording> localRecordings = getAllRecordings();
        final int[] syncCount = {0};
        final boolean[] syncSuccess = {true};

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

        // If no recordings to sync
        if (localRecordings.isEmpty() && callback != null) {
            callback.onSyncComplete(true);
        }
    }

    // Download all recordings from server
    public void downloadRecordingsFromServer(SyncStatusCallback callback) {
        if (!isNetworkAvailable()) {
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
}