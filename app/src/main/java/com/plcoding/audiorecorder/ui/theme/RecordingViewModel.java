package com.plcoding.audiorecorder.ui.theme;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.plcoding.audiorecorder.data.Recording;
import com.plcoding.audiorecorder.data.RecordingRepository;
import com.plcoding.audiorecorder.playback.AudioPlayer;
import com.plcoding.audiorecorder.record.AudioRecorder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class RecordingViewModel extends ViewModel {
    private static final String TAG = "RecordingViewModel";
    private final RecordingRepository repository;
    private final AudioRecorder recorder;
    private final AudioPlayer player;
    private final Context appContext;
    private final Executor executor;

    private final MutableLiveData<List<Recording>> _recordings = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> _isRecording = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> _isPlaying = new MutableLiveData<>(false);
    private final MutableLiveData<Long> _currentlyPlayingId = new MutableLiveData<>(null);

    // New sync related LiveData
    private final MutableLiveData<Boolean> _isSyncing = new MutableLiveData<>(false);
    private final MutableLiveData<String> _syncMessage = new MutableLiveData<>("");

    private File currentRecordingFile;
    private long recordingStartTime;

    public RecordingViewModel(
            RecordingRepository repository,
            AudioRecorder recorder,
            AudioPlayer player,
            Context appContext
    ) {
        this.repository = repository;
        this.recorder = recorder;
        this.player = player;
        this.appContext = appContext;
        this.executor = Executors.newSingleThreadExecutor();

        loadRecordings();
    }

    private void loadRecordings() {
        executor.execute(() -> {
            try {
                List<Recording> recordingsList = repository.getAllRecordings();
                _recordings.postValue(recordingsList);
                Log.d(TAG, "Loaded " + (recordingsList != null ? recordingsList.size() : 0) + " recordings");
            } catch (Exception e) {
                Log.e(TAG, "Error loading recordings", e);
            }
        });
    }

    public void startRecording() {
        if (Boolean.TRUE.equals(_isRecording.getValue())) return;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        String fileName = "recording_" + timestamp + ".mp3";

        File file = new File(appContext.getFilesDir(), fileName);
        recorder.start(file);
        currentRecordingFile = file;
        recordingStartTime = System.currentTimeMillis();
        _isRecording.setValue(true);
    }

    public void stopRecording() {
        if (Boolean.FALSE.equals(_isRecording.getValue())) return;

        recorder.stop();
        File file = currentRecordingFile;
        if (file != null) {
            long duration = System.currentTimeMillis() - recordingStartTime;
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
            String title = "Recording " + sdf.format(new Date());

            executor.execute(() -> {
                try {
                    repository.saveRecording(title, file, duration);
                    loadRecordings();
                    Log.d(TAG, "Voice recording saved: " + title);
                } catch (Exception e) {
                    Log.e(TAG, "Error saving voice recording", e);
                }
            });
        }

        _isRecording.setValue(false);
        currentRecordingFile = null;
    }

    public void saveTextRecording(String text) {
        if (text == null || text.trim().isEmpty()) {
            Log.w(TAG, "Attempted to save empty text");
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
        String title = "Text Note " + sdf.format(new Date());

        executor.execute(() -> {
            try {
                long id = repository.saveTextRecording(title, text);
                Log.d(TAG, "Text recording saved with ID: " + id + ", Title: " + title + ", Content: " + text);
                loadRecordings();
            } catch (Exception e) {
                Log.e(TAG, "Error saving text recording", e);
            }
        });
    }

    public void cancelRecording() {
        if (Boolean.FALSE.equals(_isRecording.getValue())) return;

        recorder.stop();
        if (currentRecordingFile != null) {
            currentRecordingFile.delete();
        }

        _isRecording.setValue(false);
        currentRecordingFile = null;
    }

    public void playRecording(long id) {
        if (Boolean.TRUE.equals(_isPlaying.getValue())) {
            player.stop();
            _isPlaying.setValue(false);
            _currentlyPlayingId.setValue(null);
        }

        executor.execute(() -> {
            Recording recording = repository.getRecording(id);
            if (recording != null && recording.isVoiceRecording() && recording.getFile() != null) {
                player.playFile(recording.getFile());
                _isPlaying.postValue(true);
                _currentlyPlayingId.postValue(id);
            }
        });
    }

    public void stopPlayback() {
        player.stop();
        _isPlaying.setValue(false);
        _currentlyPlayingId.setValue(null);
    }

    public void deleteRecording(long id) {
        Long currentlyPlayingId = _currentlyPlayingId.getValue();
        if (currentlyPlayingId != null && currentlyPlayingId == id) {
            stopPlayback();
        }

        executor.execute(() -> {
            try {
                repository.deleteRecording(id);
                loadRecordings();
                Log.d(TAG, "Recording deleted: " + id);
            } catch (Exception e) {
                Log.e(TAG, "Error deleting recording", e);
            }
        });
    }

    // New sync methods
    public void syncWithServer() {
        if (Boolean.TRUE.equals(_isSyncing.getValue())) {
            return;  // Already syncing
        }

        _isSyncing.setValue(true);
        _syncMessage.setValue("Syncing recordings with server...");

        executor.execute(() -> {
            // First upload local recordings to server
            repository.syncAllRecordings(uploadSuccess -> {
                if (uploadSuccess) {
                    _syncMessage.postValue("Uploading successful. Downloading from server...");

                    // Then download recordings from server
                    repository.downloadRecordingsFromServer(downloadSuccess -> {
                        _isSyncing.postValue(false);

                        if (downloadSuccess) {
                            _syncMessage.postValue("Sync completed successfully");
                            loadRecordings();  // Reload recordings after sync
                        } else {
                            _syncMessage.postValue("Error downloading recordings from server");
                        }
                    });
                } else {
                    _isSyncing.postValue(false);
                    _syncMessage.postValue("Error uploading recordings to server");
                }
            });
        });
    }

    public LiveData<List<Recording>> getRecordings() {
        return _recordings;
    }

    public LiveData<Boolean> getIsRecording() {
        return _isRecording;
    }

    public LiveData<Boolean> getIsPlaying() {
        return _isPlaying;
    }

    public LiveData<Long> getCurrentlyPlayingId() {
        return _currentlyPlayingId;
    }

    public LiveData<Boolean> getIsSyncing() {
        return _isSyncing;
    }

    public LiveData<String> getSyncMessage() {
        return _syncMessage;
    }

    public static class Factory implements ViewModelProvider.Factory {
        private final Context context;
        private final AudioRecorder recorder;
        private final AudioPlayer player;

        public Factory(Context context, AudioRecorder recorder, AudioPlayer player) {
            this.context = context;
            this.recorder = recorder;
            this.player = player;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            return (T) new RecordingViewModel(
                    new RecordingRepository(context),
                    recorder,
                    player,
                    context
            );
        }
    }
}