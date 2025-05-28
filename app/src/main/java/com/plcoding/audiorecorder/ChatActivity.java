// ChatActivity.java - FIXED VERSION to prevent multiple connections

package com.plcoding.audiorecorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.plcoding.audiorecorder.api.ChatWebSocketClient;
import com.plcoding.audiorecorder.api.RetrofitClient;
import com.plcoding.audiorecorder.data.Recording;
import com.plcoding.audiorecorder.data.RecordingRepository;
import com.plcoding.audiorecorder.utils.DeviceIdHelper;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatActivity extends AppCompatActivity implements ChatWebSocketClient.MessageListener {
    private static final String TAG = "ChatActivity";
    private static final int MAX_RECONNECT_ATTEMPTS = 3;

    // UI Components
    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private EditText messageInput;
    private Button sendButton;
    private TextView connectionStatus;
    private ProgressBar connectionProgress;
    private View connectionStatusContainer;
    private Button retryButton;

    // Data
    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private final Set<String> processedMessageIds = new HashSet<>();
    private final Set<String> recentSystemMessages = new HashSet<>();

    // Connection - FIXED: Add atomic flags to prevent race conditions
    private ChatWebSocketClient chatWebSocket;
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private String recordingId;
    private String deviceId;
    private int reconnectAttempts = 0;

    // Background processing
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private RecordingRepository repository;

    // State management
    private boolean isDestroyed = false;
    private boolean initialHistoryLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Get parameters
        recordingId = getIntent().getStringExtra("recording_id");
        if (TextUtils.isEmpty(recordingId)) {
            showErrorAndFinish("No recording ID provided");
            return;
        }

        deviceId = DeviceIdHelper.getDeviceId(this);
        if (TextUtils.isEmpty(deviceId)) {
            showErrorAndFinish("Failed to get device ID");
            return;
        }

        Log.d(TAG, "Starting chat: deviceId=" + deviceId + ", recordingId=" + recordingId);

        // Initialize repository
        repository = new RecordingRepository(this);

        // Initialize UI
        initializeViews();

        // Load recording info and start connection
        loadRecordingInfo();

        // Register network receiver
        registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    private void initializeViews() {
        // Find views
        chatRecyclerView = findViewById(R.id.chat_recycler_view);
        messageInput = findViewById(R.id.message_input);
        sendButton = findViewById(R.id.send_button);
        connectionStatus = findViewById(R.id.connection_status);
        connectionProgress = findViewById(R.id.connection_progress);
        connectionStatusContainer = findViewById(R.id.connection_status_container);
        retryButton = findViewById(R.id.retry_button);

        // Setup RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(layoutManager);

        chatAdapter = new ChatAdapter(chatMessages);
        chatRecyclerView.setAdapter(chatAdapter);

        // Setup input controls
        updateInputState(false);

        // Setup button listeners
        sendButton.setOnClickListener(v -> sendMessage());
        retryButton.setOnClickListener(v -> retryConnection());

        // Show initial status
        showConnectionStatus("Preparing to connect...", true);
    }

    private void loadRecordingInfo() {
        executor.execute(() -> {
            try {
                Recording recording = repository.getRecording(Long.parseLong(recordingId));

                mainHandler.post(() -> {
                    if (recording != null) {
                        setTitle("Chat - " + recording.getTitle());
                        addSystemMessage("Found recording: " + recording.getTitle());

                        // Load local messages first
                        loadLocalMessages();

                        // Then connect to server
                        connectToServerSafely();
                    } else {
                        showErrorAndFinish("Recording not found");
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading recording info", e);
                mainHandler.post(() -> showErrorAndFinish("Error loading recording: " + e.getMessage()));
            }
        });
    }

    private void loadLocalMessages() {
        executor.execute(() -> {
            try {
                List<LocalChatMessage> localMessages = repository.getLocalChatMessages(Long.parseLong(recordingId));

                mainHandler.post(() -> {
                    if (!localMessages.isEmpty()) {
                        addSystemMessage("Loading " + localMessages.size() + " local messages...");

                        for (LocalChatMessage msg : localMessages) {
                            // Skip empty messages and ping/pong
                            if (TextUtils.isEmpty(msg.getMessage()) ||
                                    isPingPongMessage(msg.getMessage()) ||
                                    processedMessageIds.contains(msg.getMessageId())) {
                                continue;
                            }

                            ChatMessage chatMessage = new ChatMessage(
                                    msg.getMessage(),
                                    msg.isFromDevice() ? ChatMessage.TYPE_DEVICE : ChatMessage.TYPE_ADMIN,
                                    msg.isFromDevice() ? "You" : "Admin",
                                    formatTimestamp(msg.getTimestamp()),
                                    msg.getMessageId(),
                                    true // Mark as historical
                            );

                            addMessageToChat(chatMessage);

                            if (msg.getMessageId() != null) {
                                processedMessageIds.add(msg.getMessageId());
                            }
                        }

                        addSystemMessage("Local messages loaded");
                        initialHistoryLoaded = true;
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading local messages", e);
                mainHandler.post(() -> addSystemMessage("Error loading local messages"));
            }
        });
    }

    // FIXED: Prevent multiple simultaneous connections
    private void connectToServerSafely() {
        if (isDestroyed || isConnecting.get()) {
            Log.d(TAG, "Skipping connection attempt - already connecting or destroyed");
            return;
        }

        if (isConnected.get()) {
            Log.d(TAG, "Already connected, skipping connection attempt");
            return;
        }

        showConnectionStatus("Connecting to server...", true);
        isConnecting.set(true);

        executor.execute(() -> {
            // Check server reachability first
            boolean serverReachable = RetrofitClient.getInstance(this).isServerReachable();

            mainHandler.post(() -> {
                if (serverReachable) {
                    startWebSocketConnectionSafely();
                } else {
                    isConnecting.set(false);
                    showConnectionStatus("Server not reachable", false);
                    addSystemMessage("Server not reachable. Will retry...");
                    scheduleReconnect();
                }
            });
        });
    }

    // FIXED: Ensure only one connection at a time
    private void startWebSocketConnectionSafely() {
        try {
            // Close any existing connection and wait
            closeExistingConnectionAndWait();

            String serverUrl = getWebSocketUrl();
            Log.d(TAG, "Creating single WebSocket connection to: " + serverUrl);

            chatWebSocket = new ChatWebSocketClient(serverUrl, this, deviceId, recordingId);
            chatWebSocket.connect();

        } catch (URISyntaxException e) {
            Log.e(TAG, "Invalid WebSocket URL", e);
            isConnecting.set(false);
            showConnectionStatus("Invalid server URL", false);
            addSystemMessage("Invalid server URL: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error starting WebSocket connection", e);
            isConnecting.set(false);
            showConnectionStatus("Connection error", false);
            addSystemMessage("Connection error: " + e.getMessage());
            scheduleReconnect();
        }
    }

    // FIXED: Properly wait for connection closure
    private void closeExistingConnectionAndWait() {
        if (chatWebSocket != null) {
            Log.d(TAG, "Closing existing WebSocket connection");

            try {
                ChatWebSocketClient clientToClose = chatWebSocket;
                chatWebSocket = null; // Clear reference immediately
                clientToClose.closePermanently();

                // Wait a bit for connection to fully close
                Thread.sleep(100);

            } catch (Exception e) {
                Log.e(TAG, "Error closing existing WebSocket", e);
            }
        }
    }

    public String getWebSocketUrl() {
        String baseUrl = RetrofitClient.getInstance(this).getWebSocketBaseUrl();
        baseUrl = baseUrl.replace("http://", "").replace("https://", "")
                .replace("ws://", "").replace("wss://", "");

        // Remove trailing slashes
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        return String.format("ws://%s/ws/chat/%s/%s/", baseUrl, deviceId, recordingId);
    }

    private void retryConnection() {
        Log.d(TAG, "Manual retry connection requested");
        reconnectAttempts = 0;
        isConnecting.set(false);
        isConnected.set(false);
        connectToServerSafely();
    }

    private void scheduleReconnect() {
        if (isDestroyed || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            showConnectionStatus("Max retries reached", false);
            return;
        }

        reconnectAttempts++;
        long delay = Math.min(30000, 2000L * reconnectAttempts); // Max 30 seconds

        showConnectionStatus("Retrying in " + (delay / 1000) + "s...", true);

        mainHandler.postDelayed(() -> {
            if (!isDestroyed) {
                connectToServerSafely();
            }
        }, delay);
    }

    // === MESSAGE HANDLING ===

    private void sendMessage() {
        String message = messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(message)) {
            return;
        }

        if (!isConnected.get() || chatWebSocket == null) {
            // Save message locally for offline use
            saveMessageLocally(message, true);

            ChatMessage chatMessage = new ChatMessage(
                    message, ChatMessage.TYPE_DEVICE, "You", getCurrentTimestamp(), null, false
            );
            addMessageToChat(chatMessage);
            messageInput.setText("");

            Toast.makeText(this, "Message saved offline", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Send to server with correct sender_type
            chatWebSocket.sendMessage(message, deviceId);

            // Clear input
            messageInput.setText("");

            // Don't add to UI here - wait for server confirmation
            // The message will be added when we receive it back from the server

            // Save locally
            saveMessageLocally(message, true);

        } catch (Exception e) {
            Log.e(TAG, "Error sending message", e);
            Toast.makeText(this, "Error sending message", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveMessageLocally(String message, boolean isFromDevice) {
        executor.execute(() -> {
            try {
                repository.saveLocalChatMessage(Long.parseLong(recordingId), message, isFromDevice, null);
            } catch (Exception e) {
                Log.e(TAG, "Error saving message locally", e);
            }
        });
    }

    private void addMessageToChat(ChatMessage message) {
        if (shouldSkipMessage(message)) {
            return;
        }

        chatMessages.add(message);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
    }

    private void addSystemMessage(String message) {
        // Avoid duplicate system messages
        if (recentSystemMessages.contains(message)) {
            return;
        }

        recentSystemMessages.add(message);
        if (recentSystemMessages.size() > 10) {
            recentSystemMessages.clear();
        }

        ChatMessage systemMessage = new ChatMessage(
                message, ChatMessage.TYPE_SYSTEM, "System", getCurrentTimestamp(), null, false
        );
        addMessageToChat(systemMessage);
    }

    private boolean shouldSkipMessage(ChatMessage message) {
        // Skip empty messages
        if (TextUtils.isEmpty(message.getMessage())) {
            return true;
        }

        // Skip ping/pong messages
        if (isPingPongMessage(message.getMessage())) {
            return true;
        }

        // FIXED: Only skip exact duplicates with same message ID
        // Don't skip based on content to allow for full conversation display
        if (message.getMessageId() != null && processedMessageIds.contains(message.getMessageId())) {
            Log.d(TAG, "Skipping duplicate message ID: " + message.getMessageId());
            return true;
        }

        return false;
    }

    private boolean isPingPongMessage(String message) {
        if (message == null) return false;
        String msg = message.toLowerCase().trim();
        return msg.equals("ping") || msg.equals("pong");
    }

    // === WebSocket LISTENER IMPLEMENTATION ===

    @Override
    public void onMessageReceived(String sender, String message, String timestamp, String messageId, boolean isHistorical) {
        if (isDestroyed || TextUtils.isEmpty(message) || isPingPongMessage(message)) {
            return;
        }

        // Skip duplicates
        if (messageId != null && processedMessageIds.contains(messageId)) {
            Log.d(TAG, "Skipping duplicate message: " + messageId);
            return;
        }

        mainHandler.post(() -> {
            int messageType;
            String displaySender;

            // FIXED: Properly handle ALL sender types
            switch (sender.toLowerCase()) {
                case "admin":
                    messageType = ChatMessage.TYPE_ADMIN;
                    displaySender = "Admin";
                    break;
                case "system":
                    messageType = ChatMessage.TYPE_SYSTEM;
                    displaySender = "System";
                    break;
                case "device":
                default:
                    messageType = ChatMessage.TYPE_DEVICE;
                    // Show "You" for messages from this device, otherwise show device ID
                    displaySender = deviceId.equals(sender) ? "You" : sender;
                    break;
            }

            ChatMessage chatMessage = new ChatMessage(
                    message, messageType, displaySender, timestamp, messageId, isHistorical
            );

            // IMPORTANT: Don't skip any messages - show ALL messages to create full conversation
            addMessageToChat(chatMessage);

            // Track processed message ID
            if (messageId != null) {
                processedMessageIds.add(messageId);
            }

            // Save admin messages locally (but not our own device messages)
            if (messageType == ChatMessage.TYPE_ADMIN && !isHistorical) {
                saveMessageLocally(message, false);
            }

            Log.d(TAG, "Displayed message from " + sender + " (" + messageType + "): " + message);
        });
    }

    @Override
    public void onTypingStarted() {
        // Not implemented in this version
    }

    @Override
    public void onConnectionStateChange(boolean connected, String message) {
        mainHandler.post(() -> {
            isConnected.set(connected);
            isConnecting.set(false); // Clear connecting flag

            if (connected) {
                reconnectAttempts = 0;
                showConnectionStatus("Connected", false);
                updateInputState(true);
                addSystemMessage("Connected to chat server");
            } else {
                showConnectionStatus(message, false);
                updateInputState(false);
                addSystemMessage("Disconnected: " + message);

                // Auto-reconnect on connection loss (but not if manually disconnecting)
                if (!isDestroyed && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    scheduleReconnect();
                }
            }
        });
    }

    @Override
    public void onError(String errorMessage) {
        mainHandler.post(() -> {
            isConnecting.set(false); // Clear connecting flag on error
            addSystemMessage("Error: " + errorMessage);
            Log.e(TAG, "WebSocket error: " + errorMessage);
        });
    }

    // === UI HELPERS ===

    private void showConnectionStatus(String status, boolean showProgress) {
        connectionStatus.setText(status);
        connectionProgress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        connectionStatusContainer.setVisibility(View.VISIBLE);

        // Auto-hide status after success
        if (!showProgress && status.contains("Connected")) {
            mainHandler.postDelayed(() -> {
                if (connectionStatusContainer != null) {
                    connectionStatusContainer.setVisibility(View.GONE);
                }
            }, 2000);
        }
    }

    private void updateInputState(boolean enabled) {
        messageInput.setEnabled(enabled);
        sendButton.setEnabled(enabled);

        if (enabled) {
            messageInput.setHint("Type a message...");
        } else {
            messageInput.setHint("Connecting...");
        }
    }

    private void showErrorAndFinish(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    private String getCurrentTimestamp() {
        return android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", new java.util.Date()).toString();
    }

    private String formatTimestamp(long timestamp) {
        return android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", new java.util.Date(timestamp)).toString();
    }

    // === NETWORK RECEIVER - FIXED: Prevent multiple reconnections ===

    private final BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isDestroyed) return;

            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean hasConnectivity = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

            Log.d(TAG, "Network connectivity changed: " + hasConnectivity);

            if (hasConnectivity && !isConnected.get() && !isConnecting.get()) {
                addSystemMessage("Network restored, reconnecting...");
                reconnectAttempts = 0;
                connectToServerSafely();
            } else if (!hasConnectivity) {
                addSystemMessage("Network connection lost");
                showConnectionStatus("No network", false);
            }
        }
    };

    // === LIFECYCLE METHODS ===

    @Override
    protected void onResume() {
        super.onResume();

        // Only reconnect if not connected and not currently connecting
        if (!isConnected.get() && !isConnecting.get() && !isDestroyed) {
            connectToServerSafely();
        }
    }

    @Override
    protected void onDestroy() {
        isDestroyed = true;

        // Close WebSocket connection
        closeExistingConnectionAndWait();

        // Unregister network receiver
        try {
            unregisterReceiver(networkReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering network receiver", e);
        }

        // Shutdown executor
        executor.shutdownNow();

        // Clear handlers
        mainHandler.removeCallbacksAndMessages(null);

        super.onDestroy();
    }
}

// [ChatAdapter and ChatMessage classes remain the same as in your original code]
class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {
    private final List<ChatMessage> messages;

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
        setHasStableIds(true);
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType();
    }

    @Override
    public long getItemId(int position) {
        ChatMessage message = messages.get(position);
        if (message.getMessageId() != null) {
            try {
                return Long.parseLong(message.getMessageId());
            } catch (NumberFormatException e) {
                return message.getMessageId().hashCode();
            }
        }
        return position;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case ChatMessage.TYPE_ADMIN:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_admin, parent, false);
                break;
            case ChatMessage.TYPE_SYSTEM:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_system, parent, false);
                break;
            default: // TYPE_DEVICE
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_device, parent, false);
                break;
        }
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage message = messages.get(position);

        holder.messageText.setText(message.getMessage());

        // Set message info
        String info = message.getSender() + " • " + message.getTimestamp();
        if (message.isHistorical()) {
            info += " • Historical";
        }
        holder.infoText.setText(info);

        // Set background based on message type
        switch (message.getType()) {
            case ChatMessage.TYPE_ADMIN:
                holder.messageText.setBackgroundResource(R.drawable.bg_message_admin);
                break;
            case ChatMessage.TYPE_SYSTEM:
                holder.messageText.setBackgroundResource(R.drawable.bg_message_system);
                break;
            default:
                holder.messageText.setBackgroundResource(R.drawable.bg_message_device);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        TextView infoText;

        ChatViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.message_text);
            infoText = itemView.findViewById(R.id.info_text);
        }
    }
}

// === CHAT MESSAGE MODEL ===

class ChatMessage {
    public static final int TYPE_ADMIN = 1;
    public static final int TYPE_DEVICE = 2;
    public static final int TYPE_SYSTEM = 3;

    private final String message;
    private final int type;
    private final String sender;
    private final String timestamp;
    private final String messageId;
    private final boolean isHistorical;

    public ChatMessage(String message, int type, String sender, String timestamp, String messageId, boolean isHistorical) {
        this.message = message;
        this.type = type;
        this.sender = sender;
        this.timestamp = timestamp;
        this.messageId = messageId;
        this.isHistorical = isHistorical;
    }

    // Getters
    public String getMessage() { return message; }
    public int getType() { return type; }
    public String getSender() { return sender; }
    public String getTimestamp() { return timestamp; }
    public String getMessageId() { return messageId; }
    public boolean isHistorical() { return isHistorical; }
}