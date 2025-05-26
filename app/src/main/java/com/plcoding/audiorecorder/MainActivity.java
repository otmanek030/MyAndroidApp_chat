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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.plcoding.audiorecorder.api.RetrofitClient;
import com.plcoding.audiorecorder.data.RecordingRepository;
import com.plcoding.audiorecorder.playback.AndroidAudioPlayer;
import com.plcoding.audiorecorder.record.AndroidAudioRecorder;
import com.plcoding.audiorecorder.ui.theme.RecordingAdapter;
import com.plcoding.audiorecorder.ui.theme.RecordingViewModel;
import com.plcoding.audiorecorder.utils.DraggableButtonHelper;
import com.plcoding.audiorecorder.data.Recording;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.plcoding.audiorecorder.forms.ChecklistCategory;


public class MainActivity extends AppCompatActivity {
    private AndroidAudioRecorder recorder;
    private AndroidAudioPlayer player;
    private RecordingViewModel viewModel;
    private RecyclerView recyclerView;
    private RecordingAdapter adapter;

    // FAB Menu - Updated to include task checklist FAB
    private FloatingActionButton fabMain;
    private FloatingActionButton fabVoice;
    private FloatingActionButton fabText;
    private FloatingActionButton fabTaskChecklist; // ADD THIS
    private View overlay;
    private boolean isFabMenuOpen = false;
    private boolean isRecording = false;

    private DraggableButtonHelper draggableHelper;
    private static final String TAG = "MainActivity";

    // For background operations
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        initializeViews();

        // Initialize recorder and player
        recorder = new AndroidAudioRecorder(getApplicationContext());
        player = new AndroidAudioPlayer(getApplicationContext());

        // Initialize ViewModel
        RecordingViewModel.Factory factory = new RecordingViewModel.Factory(
                getApplicationContext(),
                recorder,
                player
        );
        viewModel = new ViewModelProvider(this, factory).get(RecordingViewModel.class);

        // Try to sync any pending uploads
        // After your existing initialization code
        TextView networkStatusText = findViewById(R.id.network_status);

        // Check network status - PROPERLY USING BACKGROUND THREAD
        checkServerConnection(networkStatusText);

        // Request permissions including storage for Android 10 and below
        requestNecessaryPermissions();

        // Setup RecyclerView
        setupRecyclerView();

        // Setup FAB menu BEFORE draggable functionality
        setupFabMenu();

        // Setup draggable functionality AFTER setting click listener
        setupDraggableFab();

        // Observe ViewModel
        observeViewModel();

        // Setup dynamic padding
        setupDynamicPadding();
    }

    /**
     * Request all necessary permissions based on Android version
     */
    private void requestNecessaryPermissions() {
        String[] permissions;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_NETWORK_STATE
            };
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            permissions = new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
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

    /**
     * Check server connection using background thread
     */
    private void checkServerConnection(TextView networkStatusText) {
        // Set initial status
        networkStatusText.setText("Checking...");
        networkStatusText.setTextColor(Color.GRAY);

        // Use executor service to run network operations on background thread
        executor.execute(() -> {
            boolean isServerAvailable = false;
            String errorMessage = null;

            try {
                Log.d(TAG, "Testing server connection...");

                // Try different methods to connect to server
                RetrofitClient client = RetrofitClient.getInstance(MainActivity.this);

                // First try HTTP connection
                isServerAvailable = client.isServerReachable();
                Log.d(TAG, "HTTP check result: " + isServerAvailable);

                // If HTTP fails, try socket connection
                if (!isServerAvailable) {
                    isServerAvailable = client.pingServer();
                    Log.d(TAG, "Socket check result: " + isServerAvailable);
                }

                Log.d(TAG, "Server available: " + isServerAvailable);
            } catch (Exception e) {
                Log.e(TAG, "Error checking server connection", e);
                errorMessage = e.getMessage();
            }

            // Need final variables for lambda
            final boolean finalIsServerAvailable = isServerAvailable;
            final String finalErrorMessage = errorMessage;

            // Update UI on main thread
            mainHandler.post(() -> {
                if (finalIsServerAvailable) {
                    networkStatusText.setText("Online");
                    networkStatusText.setTextColor(Color.GREEN);
                    // If online, try to sync pending uploads
                    trySyncPendingUploads();
                } else {
                    networkStatusText.setText("Offline");
                    networkStatusText.setTextColor(Color.RED);

                    // Show toast with better error message
                    String message = "Server unavailable. Check your network connection or server status.";
                    if (finalErrorMessage != null) {
                        message += " Error: " + finalErrorMessage;
                    }
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();

                    // Show dialog with server configuration option
                    offerServerConfiguration();
                }
            });
        });
    }

    private void offerServerConfiguration() {
        new AlertDialog.Builder(this)
                .setTitle("Server Connection Failed")
                .setMessage("Unable to connect to the server. Would you like to update the server configuration?")
                .setPositiveButton("Configure Server", (dialog, which) -> {
                    // Open server configuration
                    Intent intent = new Intent(MainActivity.this, ServerConfigActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton("Try Again", (dialog, which) -> {
                    // Retry connection
                    TextView networkStatusText = findViewById(R.id.network_status);
                    checkServerConnection(networkStatusText);
                })
                .setNeutralButton("Cancel", null)
                .show();
    }

    /**
     * Try to sync any pending uploads
     */
    private void trySyncPendingUploads() {
        executor.execute(() -> {
            try {
                RecordingRepository repository = new RecordingRepository(MainActivity.this);
                int pendingCount = repository.getPendingUploadsCount();

                if (pendingCount > 0) {
                    mainHandler.post(() -> {
                        Snackbar.make(recyclerView,
                                "Found " + pendingCount + " pending uploads. Syncing...",
                                Snackbar.LENGTH_LONG).show();
                    });

                    repository.syncPendingUploads(null);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error syncing pending uploads", e);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    private void openTaskChecklistEnhanced() {
        Intent intent = new Intent(MainActivity.this, TaskChecklistActivity.class);
        intent.putExtra("enhanced_ui", true); // Flag for enhanced UI
        startActivity(intent);
    }

    // ADD THIS METHOD FOR MENU ITEM SELECTION
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        int id = item.getItemId();
//
//        if (id == R.id.action_task_checklist) {
//            Intent intent = new Intent(this, TaskChecklistActivity.class);
//            startActivity(intent);
//            return true;
//        } else if (id == R.id.action_conversations) {
//            Intent intent = new Intent(this, ConversationsActivity.class);
//            startActivity(intent);
//            return true;
//        } else if (id == R.id.action_sync) {
//            forceSyncAll();
//            return true;
//        } else if (id == R.id.action_settings) {
//            Intent intent = new Intent(this, ServerConfigActivity.class);
//            startActivity(intent);
//            return true;
//        }
//
//        return super.onOptionsItemSelected(item);
//    }

    /**
     * Force sync all pending uploads
     */
    private void forceSyncAll() {
        Snackbar syncingSnackbar = Snackbar.make(recyclerView,
                "Syncing all recordings...", Snackbar.LENGTH_INDEFINITE);
        syncingSnackbar.show();

        executor.execute(() -> {
            try {
                RecordingRepository repository = new RecordingRepository(MainActivity.this);
                repository.forceUploadPending(new RecordingRepository.OperationCallback() {
                    @Override
                    public void onSuccess() {
                        mainHandler.post(() -> {
                            syncingSnackbar.dismiss();
                            Snackbar.make(recyclerView,
                                    "Sync completed successfully",
                                    Snackbar.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        mainHandler.post(() -> {
                            syncingSnackbar.dismiss();
                            Snackbar.make(recyclerView,
                                    "Sync error: " + errorMessage,
                                    Snackbar.LENGTH_LONG).show();
                        });
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    syncingSnackbar.dismiss();
                    Snackbar.make(recyclerView,
                            "Sync error: " + e.getMessage(),
                            Snackbar.LENGTH_LONG).show();
                });
            }
        });
    }

    // UPDATED METHOD WITH TASK CHECKLIST INTEGRATION
    private void initializeViews() {
        recyclerView = findViewById(R.id.recordings_recycler_view);
        fabMain = findViewById(R.id.fab_main);
        fabVoice = findViewById(R.id.fab_voice);
        fabText = findViewById(R.id.fab_text);
        fabTaskChecklist = findViewById(R.id.fab_task_checklist);    // ADD THIS
        overlay = findViewById(R.id.overlay);

        // Initialize conversations button
        Button conversationsButton = findViewById(R.id.conversations_button);
        if (conversationsButton != null) {
            conversationsButton.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, ConversationsActivity.class);
                startActivity(intent);
            });
        }

        // ADD THIS: Initialize task checklist button
        Button taskChecklistButton = findViewById(R.id.task_checklist_button);
        if (taskChecklistButton != null) {
            taskChecklistButton.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, TaskChecklistActivity.class);
                startActivity(intent);
            });
        }
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecordingAdapter(
                recording -> {
                    if (recording.isVoiceRecording()) {
                        viewModel.playRecording(recording.getId());
                    } else {
                        showTextContent(recording.getTextContent());
                    }
                },
                recording -> viewModel.stopPlayback(),
                this::showDeleteConfirmation
        );
        recyclerView.setAdapter(adapter);
    }

    // UPDATED METHOD WITH TASK CHECKLIST FAB
    private void setupFabMenu() {
        // Set click listener first
        fabMain.setOnClickListener(v -> handleMainFabClick());

        fabVoice.setOnClickListener(v -> {
            closeFabMenu();
            startVoiceRecording();
        });

        fabText.setOnClickListener(v -> {
            closeFabMenu();
            showTextInputDialog();
        });

        // Enhanced Task Checklist FAB with new features
        if (fabTaskChecklist != null) {
            fabTaskChecklist.setOnClickListener(v -> {
                closeFabMenu();
                openTaskChecklistEnhanced(); // Use enhanced version
            });
        }

        overlay.setOnClickListener(v -> closeFabMenu());
    }


    private void openEnhancedChecklist() {
        Intent intent = new Intent(MainActivity.this, ChecklistCompletionActivity.class);
        intent.putExtra("form_id", 1); // Example form ID
        intent.putExtra("form_title", "Enhanced Safety Checklist");
        intent.putExtra("is_mandatory", true);
        startActivity(intent);
    }

    // ADD THIS METHOD:
    private void openTaskChecklist() {
        Intent intent = new Intent(MainActivity.this, TaskChecklistActivity.class);
        startActivity(intent);
    }

    private void setupDraggableFab() {
        draggableHelper = new DraggableButtonHelper();
        draggableHelper.makeDraggable(fabMain, null);

        // Add listener to update RecyclerView padding when FAB is moved
        fabMain.getViewTreeObserver().addOnGlobalLayoutListener(this::updateRecyclerViewPadding);
    }

    private void setupDynamicPadding() {
        // Initial padding setup
        fabMain.post(this::updateRecyclerViewPadding);
    }

    private void updateRecyclerViewPadding() {
        // Get FAB position
        int[] location = new int[2];
        fabMain.getLocationOnScreen(location);
        int fabTop = location[1];
        int fabHeight = fabMain.getHeight();

        // Get screen dimensions
        int screenHeight = getResources().getDisplayMetrics().heightPixels;

        // Calculate bottom padding based on FAB position
        int bottomPadding = screenHeight - fabTop + 20; // Extra 20dp margin

        // Apply padding to RecyclerView
        recyclerView.setPadding(
                recyclerView.getPaddingLeft(),
                recyclerView.getPaddingTop(),
                recyclerView.getPaddingRight(),
                bottomPadding
        );
    }

    private void handleMainFabClick() {
        if (isRecording) {
            stopVoiceRecording();
        } else {
            toggleFabMenu();
        }
    }

    private void toggleFabMenu() {
        if (isFabMenuOpen) {
            closeFabMenu();
        } else {
            openFabMenu();
        }
    }

    private void openFabMenu() {
        isFabMenuOpen = true;

        // Show overlay with fade animation
        overlay.setVisibility(View.VISIBLE);
        overlay.animate().alpha(1f).setDuration(200).start();

        // Rotate main FAB
        fabMain.animate().rotation(45f).setDuration(200).start();

        // Show menu FABs with animation - position them relative to main FAB
        showMenuFabs();
    }

    // UPDATED METHOD TO INCLUDE TASK CHECKLIST FAB
    private void showMenuFabs() {
        // Get main FAB position
        float mainFabX = fabMain.getX();
        float mainFabY = fabMain.getY();

        // Position voice FAB
        fabVoice.setX(mainFabX);
        fabVoice.setY(mainFabY - dpToPx(70));
        animateFabIn(fabVoice);

        // Position text FAB
        fabText.setX(mainFabX);
        fabText.setY(mainFabY - dpToPx(140));
        animateFabIn(fabText);

        // ADD THIS: Position task checklist FAB
        if (fabTaskChecklist != null) {
            fabTaskChecklist.setX(mainFabX);
            fabTaskChecklist.setY(mainFabY - dpToPx(210));
            animateFabIn(fabTaskChecklist);
        }
    }

    // UPDATED METHOD TO INCLUDE TASK CHECKLIST FAB
    private void closeFabMenu() {
        isFabMenuOpen = false;

        // Hide overlay with fade animation
        overlay.animate().alpha(0f).setDuration(200).withEndAction(() ->
                overlay.setVisibility(View.GONE)
        ).start();

        // Rotate main FAB back
        fabMain.animate().rotation(0f).setDuration(200).start();

        // Hide menu FABs with animation
        animateFabOut(fabText);
        animateFabOut(fabVoice);

        // ADD THIS: Hide task checklist FAB
        if (fabTaskChecklist != null) {
            animateFabOut(fabTaskChecklist);
        }
    }

    private void animateFabIn(FloatingActionButton fab) {
        fab.setVisibility(View.VISIBLE);
        fab.setAlpha(0f);
        fab.setScaleX(0.5f);
        fab.setScaleY(0.5f);

        fab.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .start();
    }

    private void animateFabOut(FloatingActionButton fab) {
        fab.animate()
                .alpha(0f)
                .scaleX(0.5f)
                .scaleY(0.5f)
                .setDuration(200)
                .withEndAction(() -> fab.setVisibility(View.INVISIBLE))
                .start();
    }

    @SuppressLint("UseCompatLoadingForColorStateLists")
    private void startVoiceRecording() {
        viewModel.startRecording();
        isRecording = true;

        // Change main FAB to stop recording
        fabMain.setImageResource(R.drawable.ic_stop);
        fabMain.setBackgroundTintList(getResources().getColorStateList(R.color.delete_color));

        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("UseCompatLoadingForColorStateLists")
    private void stopVoiceRecording() {
        viewModel.stopRecording();
        isRecording = false;

        // Restore main FAB appearance
        fabMain.setImageResource(R.drawable.ic_add);
        fabMain.setBackgroundTintList(getResources().getColorStateList(R.color.fab_main));

        Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
    }

    private void showTextInputDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_text_input, null);
        TextInputEditText editText = view.findViewById(R.id.text_input);

        if (editText == null) {
            Log.e(TAG, "EditText not found in dialog layout");
            Toast.makeText(this, "Error: Input field not found", Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Enter Text Note")
                .setView(view)
                .setPositiveButton("Save", (dialog, which) -> {
                    if (editText.getText() == null) {
                        Log.e(TAG, "EditText.getText() returned null");
                        Toast.makeText(this, "Error: Could not get text", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String text = editText.getText().toString().trim();
                    Log.d(TAG, "Text to save: '" + text + "'");

                    if (!text.isEmpty()) {
                        Log.d(TAG, "Calling viewModel.saveTextRecording");
                        viewModel.saveTextRecording(text);
                        Toast.makeText(this, "Text note saved", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.w(TAG, "Text is empty");
                        Toast.makeText(this, "Text cannot be empty", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showTextContent(String content) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Text Note")
                .setMessage(content)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showDeleteConfirmation(Recording recording) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Recording")
                .setMessage("Are you sure you want to delete this recording?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    viewModel.deleteRecording(recording.getId());
                    Toast.makeText(this, "Recording deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void observeViewModel() {
        viewModel.getRecordings().observe(this, recordings -> {
            if (recordings != null) {
                adapter.setRecordings(recordings);
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

        // New sync observers
        viewModel.getIsSyncing().observe(this, isSyncing -> {
            if (Boolean.TRUE.equals(isSyncing)) {
                // Show sync in progress UI
                Snackbar.make(recyclerView, "Syncing with server...", Snackbar.LENGTH_INDEFINITE).show();
            }
        });

        viewModel.getSyncMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                Snackbar.make(recyclerView, message, Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    public void onBackPressed() {
        if (isFabMenuOpen) {
            closeFabMenu();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recorder.stop();
        player.stop();
        // Shut down the executor to prevent memory leaks
        executor.shutdown();
    }
}