package com.plcoding.audiorecorder;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.plcoding.audiorecorder.api.ChecklistResponse;
import com.plcoding.audiorecorder.api.TaskChecklistApiService;
import com.plcoding.audiorecorder.api.RetrofitClient;
import com.plcoding.audiorecorder.forms.ChecklistForm;
import com.plcoding.audiorecorder.utils.DeviceIdHelper;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TaskChecklistActivity extends AppCompatActivity {
    private static final String TAG = "TaskChecklistActivity";

    private RecyclerView checklistsRecyclerView;
    private ChecklistAdapter adapter;
    private List<ChecklistForm> availableChecklists = new ArrayList<>();
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_checklist);

        initializeViews();

        // DEBUG: Check device ID first
        debugDeviceId();

        loadAvailableChecklists();
    }

    private void debugDeviceId() {
        String deviceId = DeviceIdHelper.getDeviceId(this);
        Log.d(TAG, "=== DEVICE ID DEBUG ===");
        Log.d(TAG, "Device ID: '" + deviceId + "'");
        Log.d(TAG, "Device ID length: " + deviceId.length());
        Log.d(TAG, "Device ID bytes: " + java.util.Arrays.toString(deviceId.getBytes()));

        // Check for problematic characters
        boolean hasProblematicChars = deviceId.contains(".") || deviceId.contains("/") || deviceId.contains("\\");
        Log.d(TAG, "Has problematic chars: " + hasProblematicChars);

        if (hasProblematicChars) {
            Log.w(TAG, "Device ID contains problematic characters, regenerating...");
            deviceId = DeviceIdHelper.regenerateDeviceId(this);
            Log.d(TAG, "New device ID: '" + deviceId + "'");
        }

        Log.d(TAG, "=====================");
    }

    private void initializeViews() {
        checklistsRecyclerView = findViewById(R.id.checklists_recycler_view);
        emptyView = findViewById(R.id.empty_view);

        checklistsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChecklistAdapter(availableChecklists, this::onChecklistSelected);
        checklistsRecyclerView.setAdapter(adapter);
    }

    private void onChecklistSelected(ChecklistForm form) {
        Intent intent = new Intent(this, ChecklistCompletionActivity.class);
        intent.putExtra("form_id", form.getId());
        intent.putExtra("form_title", form.getTitle());
        intent.putExtra("is_mandatory", form.is_mandatory());
        startActivity(intent);
    }

    private void loadAvailableChecklists() {
        String deviceId = DeviceIdHelper.getDeviceId(this);

        // IMPORTANT: Clean the device ID before sending
        deviceId = cleanDeviceId(deviceId);

        Log.d(TAG, "Loading checklists for device ID: '" + deviceId + "'");

        TaskChecklistApiService apiService = RetrofitClient.getInstance(this).createService(TaskChecklistApiService.class);

        Call<ChecklistResponse> call = apiService.getAvailableChecklists(deviceId);
        call.enqueue(new Callback<ChecklistResponse>() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onResponse(Call<ChecklistResponse> call, Response<ChecklistResponse> response) {
                Log.d(TAG, "Response received: " + response.code());
                if (response.isSuccessful() && response.body() != null) {
                    ChecklistResponse checklistResponse = response.body();
                    if ("success".equals(checklistResponse.getStatus())) {
                        availableChecklists.clear();
                        availableChecklists.addAll(checklistResponse.getForms());
                        adapter.notifyDataSetChanged();

                        if (availableChecklists.isEmpty()) {
                            checklistsRecyclerView.setVisibility(View.GONE);
                            emptyView.setVisibility(View.VISIBLE);
                        } else {
                            checklistsRecyclerView.setVisibility(View.VISIBLE);
                            emptyView.setVisibility(View.GONE);
                        }

                        Log.d(TAG, "Loaded " + availableChecklists.size() + " checklists");
                    } else {
                        Log.e(TAG, "API error: " + checklistResponse.getMessage());
                        Toast.makeText(TaskChecklistActivity.this, "API Error: " + checklistResponse.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Log.e(TAG, "Failed to load checklists: " + response.code());
                    if (response.errorBody() != null) {
                        try {
                            String errorBody = response.errorBody().string();
                            Log.e(TAG, "Error body: " + errorBody);
                        } catch (Exception e) {
                            Log.e(TAG, "Error reading error body", e);
                        }
                    }
                    Toast.makeText(TaskChecklistActivity.this, "Failed to load checklists: " + response.code(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ChecklistResponse> call, Throwable t) {
                Log.e(TAG, "Error loading checklists", t);
                Toast.makeText(TaskChecklistActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Clean device ID to ensure it doesn't contain problematic characters
     */
    private String cleanDeviceId(String deviceId) {
        if (deviceId == null) {
            return "fallback_device_id";
        }

        // Remove any characters that could cause path traversal issues
        String cleaned = deviceId.replaceAll("[^a-zA-Z0-9]", "");

        // Ensure minimum length
        if (cleaned.length() < 8) {
            cleaned = cleaned + "00000000".substring(0, 8 - cleaned.length());
        }

        // Ensure maximum length
        if (cleaned.length() > 16) {
            cleaned = cleaned.substring(0, 16);
        }

        Log.d(TAG, "Original device ID: '" + deviceId + "' -> Cleaned: '" + cleaned + "'");
        return cleaned;
    }
}