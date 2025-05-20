package com.plcoding.audiorecorder;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.plcoding.audiorecorder.api.RetrofitClient;
import com.plcoding.audiorecorder.data.Recording;
import com.plcoding.audiorecorder.data.RecordingRepository;
import com.plcoding.audiorecorder.utils.DeviceIdHelper;

import java.util.ArrayList;
import java.util.List;

public class ConversationsActivity extends AppCompatActivity implements ConversationsAdapter.ConversationClickListener {
    private static final String TAG = "ConversationsActivity";

    private RecyclerView recyclerView;
    private ConversationsAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView emptyView;
    private RecordingRepository repository;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversations);

        // Set title
        setTitle("My Conversations");

        // Initialize views
        recyclerView = findViewById(R.id.conversations_recycler_view);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        emptyView = findViewById(R.id.empty_view);

        // Get device ID
        deviceId = DeviceIdHelper.getDeviceId(this);

        // Initialize repository
        repository = new RecordingRepository(this);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ConversationsAdapter(this);
        recyclerView.setAdapter(adapter);

        // Setup SwipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener(this::loadConversations);

        // Load conversations
        loadConversations();
    }

    private void loadConversations() {
        // Show loading
        swipeRefreshLayout.setRefreshing(true);

        // Load recordings with chat history
        new Thread(() -> {
            try {
                // Get all recordings
                List<Recording> recordings = repository.getAllRecordings();

                // Filter recordings with chat history (in a real app, we'd have a better way to track this)
                List<ConversationItem> conversations = new ArrayList<>();

                for (Recording recording : recordings) {
                    // In a real app, we would check if this recording has chat history
                    // For now, we'll simply show all recordings as potential conversations
                    conversations.add(new ConversationItem(
                            recording.getId(),
                            recording.getTitle(),
                            recording.isVoiceRecording() ? "Voice Recording" : "Text Note",
                            recording.getCreatedAt(),
                            recording.getDeviceId()
                    ));
                }

                // Update UI on main thread
                runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);

                    if (conversations.isEmpty()) {
                        recyclerView.setVisibility(View.GONE);
                        emptyView.setVisibility(View.VISIBLE);
                    } else {
                        recyclerView.setVisibility(View.VISIBLE);
                        emptyView.setVisibility(View.GONE);
                        adapter.setConversations(conversations);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading conversations", e);
                runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(ConversationsActivity.this,
                            "Error loading conversations: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    @Override
    public void onConversationClick(ConversationItem conversation) {
        // Open chat activity
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("recording_id", String.valueOf(conversation.getId()));
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload conversations when activity is resumed
        loadConversations();
    }
}

// Model class for conversation items
class ConversationItem {
    private long id;
    private String title;
    private String type;
    private long timestamp;
    private String deviceId;
    private int unreadCount;

    public ConversationItem(long id, String title, String type, long timestamp, String deviceId) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.timestamp = timestamp;
        this.deviceId = deviceId;
        this.unreadCount = 0; // In a real app, we'd get the actual unread count
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }
}