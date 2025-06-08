package com.plcoding.audiorecorder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback;

import com.plcoding.audiorecorder.api.RetrofitClient;
import com.plcoding.audiorecorder.data.RecordingRepository;
import com.plcoding.audiorecorder.playback.AndroidAudioPlayer;
import com.plcoding.audiorecorder.record.AndroidAudioRecorder;
import com.plcoding.audiorecorder.ui.theme.RecordingAdapter;
import com.plcoding.audiorecorder.ui.theme.RecordingViewModel;
import com.plcoding.audiorecorder.data.Recording;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // Core components
    private AndroidAudioRecorder recorder;
    private AndroidAudioPlayer player;
    private RecordingViewModel viewModel;

    // UI Components
    private MaterialToolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private RecordingAdapter adapter;
    private BottomNavigationView bottomNavigation;
    private ExtendedFloatingActionButton recordingFab;

    // Quick Action Cards
    private MaterialCardView voiceRecordingCard;
    private MaterialCardView textNoteCard;
    private MaterialCardView taskChecklistCard;

    // Status Views
    private TextView networkStatus;
    private TextView syncStatus;
    private View emptyStateLayout;

    // State
    private boolean isRecording = false;
    private boolean isInitialized = false;

    // Background operations
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Set up shared element transitions
        setExitSharedElementCallback(new MaterialContainerTransformSharedElementCallback());
        getWindow().setSharedElementsUseOverlay(false);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);




        initializeComponents();
//        setupToolbar();
        setupBottomNavigation();
        setupQuickActionCards();
        setupRecyclerView();
        setupSwipeRefresh();
        observeViewModel();

        requestNecessaryPermissions();
        checkServerConnection();

        isInitialized = true;
    }



    private void initializeComponents() {
        // Initialize core components
        recorder = new AndroidAudioRecorder(getApplicationContext());
        player = new AndroidAudioPlayer(getApplicationContext());

        // Initialize ViewModel
        RecordingViewModel.Factory factory = new RecordingViewModel.Factory(
                getApplicationContext(), recorder, player
        );
        viewModel = new ViewModelProvider(this, factory).get(RecordingViewModel.class);

        // Find views
        toolbar = findViewById(R.id.toolbar);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        recyclerView = findViewById(R.id.recordings_recycler_view);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        recordingFab = findViewById(R.id.recording_fab);

        voiceRecordingCard = findViewById(R.id.voice_recording_card);
        textNoteCard = findViewById(R.id.text_note_card);
        taskChecklistCard = findViewById(R.id.task_checklist_card);

        networkStatus = findViewById(R.id.network_status);
        syncStatus = findViewById(R.id.sync_status);
        emptyStateLayout = findViewById(R.id.empty_state_layout);
    }

//    private void setupToolbar() {
//        setSupportActionBar(toolbar);
//        if (getSupportActionBar() != null) {
//            getSupportActionBar().setTitle(getString(R.string.app_name));
//        }
//    }

    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                // Already on home
                return true;
            } else if (itemId == R.id.nav_chat) {
                startActivity(new Intent(this, ConversationsActivity.class));
                return true;
            } else if (itemId == R.id.nav_tasks) {
                startActivity(new Intent(this, TaskChecklistActivity.class));
                return true;
            } else if (itemId == R.id.nav_profile) {
                // Start profile activity (to be implemented)
                showFeatureComingSoon("Profile");
                return true;
            }

            return false;
        });

        // Set home as selected by default
        bottomNavigation.setSelectedItemId(R.id.nav_home);
    }

    private void setupQuickActionCards() {
        // Voice Recording Card
        voiceRecordingCard.setOnClickListener(v -> {
            if (isRecording) {
                stopVoiceRecording();
            } else {
                startVoiceRecording();
            }
        });

        // Text Note Card
        textNoteCard.setOnClickListener(v -> showTextInputDialog());

        // Task Checklist Card
        taskChecklistCard.setOnClickListener(v -> {
            Intent intent = new Intent(this, TaskChecklistActivity.class);
            startActivity(intent);
        });

        // Recording FAB
        if (recordingFab != null) {
            recordingFab.setOnClickListener(v -> {
                if (isRecording) {
                    stopVoiceRecording();
                } else {
                    startVoiceRecording();
                }
            });
        }
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecordingAdapter(
                this::handleRecordingPlay,
                this::handleRecordingStop,
                this::handleRecordingDelete
        );
        recyclerView.setAdapter(adapter);

        // Add scroll listener for FAB behavior
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (recordingFab != null) {
                    if (dy > 0 && recordingFab.getVisibility() == View.VISIBLE) {
                        // Scrolling down - hide FAB
                        recordingFab.hide();
                    } else if (dy < 0 && recordingFab.getVisibility() != View.VISIBLE) {
                        // Scrolling up - show FAB
                        recordingFab.show();
                    }
                }
            }
        });
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeResources(
                R.color.primary,
                R.color.secondary,
                R.color.tertiary
        );

        swipeRefreshLayout.setOnRefreshListener(() -> {
            refreshData();
        });
    }

    private void observeViewModel() {
        viewModel.getRecordings().observe(this, recordings -> {
            if (recordings != null) {
                adapter.setRecordings(recordings);
                updateEmptyState(recordings.isEmpty());
            }
        });

        viewModel.getIsRecording().observe(this, isRecordingState -> {
            if (isRecordingState != null) {
                isRecording = isRecordingState;
                updateRecordingUI(isRecordingState);
            }
        });

        viewModel.getIsPlaying().observe(this, isPlaying -> {
            if (isPlaying != null) {
                adapter.setIsPlaying(isPlaying);
            }
        });

        viewModel.getCurrentlyPlayingId().observe(this, currentlyPlayingId -> {
            adapter.setCurrentlyPlayingId(currentlyPlayingId);
        });

        viewModel.getIsSyncing().observe(this, isSyncing -> {
            if (Boolean.TRUE.equals(isSyncing)) {
                syncStatus.setText(R.string.syncing);
                syncStatus.setTextColor(getColor(R.color.warning));
            } else {
                syncStatus.setText(R.string.synced);
                syncStatus.setTextColor(getColor(R.color.success));
            }
        });

        viewModel.getSyncMessage().observe(this, message -> {
            if (message != null && !message.isEmpty() && isInitialized) {
                showSnackbar(message);
            }
        });
    }

    private void handleRecordingPlay(Recording recording) {
        if (recording.isVoiceRecording()) {
            viewModel.playRecording(recording.getId());
        } else {
            showTextContent(recording.getTextContent());
        }
    }

    private void handleRecordingStop(Recording recording) {
        viewModel.stopPlayback();
    }

    private void handleRecordingDelete(Recording recording) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_recording_title)
                .setMessage(R.string.delete_recording_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    viewModel.deleteRecording(recording.getId());
                    showSnackbar(getString(R.string.recording_deleted));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateRecordingUI(boolean isRecording) {
        if (isRecording) {
            // Update voice recording card to show stop state
            voiceRecordingCard.setCardBackgroundColor(getColor(R.color.error_container));

            // Update FAB if visible
            if (recordingFab != null && recordingFab.getVisibility() == View.VISIBLE) {
                recordingFab.setText(R.string.stop_recording);
                recordingFab.setIcon(getDrawable(R.drawable.ic_stop));
                recordingFab.setBackgroundTintList(getColorStateList(R.color.error));
            }
        } else {
            // Restore normal state
            voiceRecordingCard.setCardBackgroundColor(getColor(R.color.surface));

            if (recordingFab != null && recordingFab.getVisibility() == View.VISIBLE) {
                recordingFab.setText(R.string.record);
                recordingFab.setIcon(getDrawable(R.drawable.ic_mic));
                recordingFab.setBackgroundTintList(getColorStateList(R.color.primary));
            }
        }
    }

    private void updateEmptyState(boolean isEmpty) {
        if (isEmpty) {
            recyclerView.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);
        }
    }

    @SuppressLint("UseCompatLoadingForColorStateLists")
    private void startVoiceRecording() {
        try {
            viewModel.startRecording();
            showSnackbar(getString(R.string.recording_started));
        } catch (Exception e) {
            Log.e(TAG, "Error starting recording", e);
            showSnackbar(getString(R.string.error_starting_recording));
        }
    }

    private void stopVoiceRecording() {
        try {
            viewModel.stopRecording();
            showSnackbar(getString(R.string.recording_stopped));
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording", e);
            showSnackbar(getString(R.string.error_stopping_recording));
        }
    }

    private void showTextInputDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_text_input_enhanced, null);
        TextInputEditText editText = view.findViewById(R.id.text_input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.create_text_note)
                .setView(view)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String text = editText.getText() != null ? editText.getText().toString().trim() : "";
                    if (!text.isEmpty()) {
                        viewModel.saveTextRecording(text);
                        showSnackbar(getString(R.string.text_note_saved));
                    } else {
                        showSnackbar(getString(R.string.text_cannot_be_empty));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showTextContent(String content) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.text_note)
                .setMessage(content)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void refreshData() {
        // Simulate refresh delay
        mainHandler.postDelayed(() -> {
            swipeRefreshLayout.setRefreshing(false);
            checkServerConnection();
            viewModel.syncWithServer();
        }, 1000);
    }

    private void checkServerConnection() {
        executor.execute(() -> {
            boolean isServerAvailable = false;

            try {
                RetrofitClient client = RetrofitClient.getInstance(this);
                isServerAvailable = client.isServerReachable();
            } catch (Exception e) {
                Log.e(TAG, "Error checking server connection", e);
            }

            final boolean finalIsServerAvailable = isServerAvailable;

            mainHandler.post(() -> {
                if (finalIsServerAvailable) {
                    networkStatus.setText(R.string.online);
                    networkStatus.setTextColor(getColor(R.color.success));
                    trySyncPendingUploads();
                } else {
                    networkStatus.setText(R.string.offline);
                    networkStatus.setTextColor(getColor(R.color.error));
                }
            });
        });
    }

    private void trySyncPendingUploads() {
        executor.execute(() -> {
            try {
                RecordingRepository repository = new RecordingRepository(this);
                int pendingCount = repository.getPendingUploadsCount();

                if (pendingCount > 0) {
                    mainHandler.post(() -> {
                        showSnackbar(getString(R.string.syncing_pending_uploads, pendingCount));
                    });
                    repository.syncPendingUploads(null);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error syncing pending uploads", e);
            }
        });
    }

    private void requestNecessaryPermissions() {
        String[] permissions;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_NETWORK_STATE
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_NETWORK_STATE
            };
        }

        ActivityCompat.requestPermissions(this, permissions, 0);
    }

    private void showSnackbar(String message) {
        if (recyclerView != null) {
            Snackbar.make(recyclerView, message, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void showFeatureComingSoon(String featureName) {
        showSnackbar(featureName + " coming soon!");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu_enhanced, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_search) {
            // Implement search functionality
            showFeatureComingSoon("Search");
            return true;
        } else if (id == R.id.action_sync) {
            viewModel.syncWithServer();
            return true;
        } else if (id == R.id.action_settings) {
//            startActivity(new Intent(this, ServerConfigActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Set bottom navigation selection
        bottomNavigation.setSelectedItemId(R.id.nav_home);

        // Check connection status
        checkServerConnection();

        // Show FAB if not recording
        if (recordingFab != null && !isRecording) {
            recordingFab.show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Hide FAB to prevent overlay issues
        if (recordingFab != null) {
            recordingFab.hide();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Clean up resources
        if (recorder != null) {
            recorder.stop();
        }
        if (player != null) {
            player.stop();
        }

        // Shutdown executor
        executor.shutdown();
    }

    @Override
    public void onBackPressed() {
        if (isRecording) {
            // Show confirmation dialog if recording
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.stop_recording_title)
                    .setMessage(R.string.stop_recording_message)
                    .setPositiveButton(R.string.stop_and_save, (dialog, which) -> {
                        stopVoiceRecording();
                        super.onBackPressed();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.discard, (dialog, which) -> {
                        viewModel.cancelRecording();
                        super.onBackPressed();
                    })
                    .show();
        } else {
            super.onBackPressed();
        }
    }
}