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
import android.view.View;
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

import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity implements ChatWebSocketClient.MessageListener {
    private static final String TAG = "ChatActivity";

    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private final Set<String> messageIds = new HashSet<>(); // To track message IDs and prevent duplicates

    private EditText messageInput;
    private Button sendButton;
    private TextView typingIndicator;
    private View connectionStatusView;
    private TextView connectionStatus;
    private ProgressBar connectionProgress;

    private ChatWebSocketClient chatWebSocket;
    private ChatWebSocketClient testClient;

    private String recordingId;
    private String deviceId;
    private boolean isConnected = false;
    private boolean isReconnecting = false;
    private int reconnectAttempts = 0;

    // Background thread management
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<String> queuedMessages = new ArrayList<>();

    // Connection state
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    private int connectionState = STATE_DISCONNECTED;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Get recording ID from intent
        recordingId = getIntent().getStringExtra("recording_id");
        if (recordingId == null) {
            showErrorAndFinish("No recording ID provided");
            return;
        }

        // Get device ID
        deviceId = DeviceIdHelper.getDeviceId(this);
        if (TextUtils.isEmpty(deviceId)) {
            showErrorAndFinish("Failed to get device ID");
            return;
        }

        Log.d(TAG, "Activity started with deviceId: " + deviceId + ", recordingId: " + recordingId);

        // Initialize views
        initializeViews();

        // Load local messages first
        loadLocalMessages();

        // Verify that the recording exists, then proceed with connection
        verifyRecordingExists(recordingId);

        // Register network receiver
        registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    private void initializeViews() {
        chatRecyclerView = findViewById(R.id.chat_recycler_view);
        messageInput = findViewById(R.id.message_input);
        sendButton = findViewById(R.id.send_button);
        typingIndicator = findViewById(R.id.typing_indicator);
        connectionStatusView = findViewById(R.id.connection_status_container);
        connectionStatus = findViewById(R.id.connection_status);
        connectionProgress = findViewById(R.id.connection_progress);

        // Set up RecyclerView with stable IDs for better performance
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(layoutManager);

        chatAdapter = new ChatAdapter(chatMessages);
        chatRecyclerView.setAdapter(chatAdapter);

        // Disable input until connected
        updateInputState(false);

        // Set up retry button
        Button retryButton = findViewById(R.id.retry_button);
        retryButton.setOnClickListener(v -> {
            // Reset connection attempts
            isReconnecting = false;
            reconnectAttempts = 0;

            // Clear any existing connection
            if (chatWebSocket != null) {
                chatWebSocket.close();
                chatWebSocket = null;
            }

            // Show connecting status
            showConnectionStatus(true, "Connecting...");

            // Check connectivity
            executor.execute(this::verifyServerConnectivity);
        });

        // Set up message sending
        sendButton.setOnClickListener(v -> {
            String message = messageInput.getText().toString().trim();
            if (!message.isEmpty()) {
                sendMessage(message);
                messageInput.setText("");
            }
        });

        // Handle "Enter" key press
        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendButton.performClick();
                return true;
            }
            return false;
        });
    }

    private void updateInputState(boolean enabled) {
        messageInput.setEnabled(enabled);
        sendButton.setEnabled(enabled);

        if (enabled) {
            messageInput.setHint(R.string.type_message_hint);
            messageInput.requestFocus();
        } else {
            messageInput.setHint(R.string.waiting_for_connection);
        }
    }

    private void verifyRecordingExists(String recordingId) {
        executor.execute(() -> {
            try {
                // Use the RecordingRepository to check if the recording exists
                RecordingRepository repository = new RecordingRepository(this);
                Recording recording = repository.getRecording(Long.parseLong(recordingId));

                if (recording == null) {
                    // Recording doesn't exist, show error and finish activity
                    mainHandler.post(() -> showErrorAndFinish("Recording ID " + recordingId + " not found."));
                } else {
                    // Recording exists, proceed with connection
                    mainHandler.post(() -> {
                        // Set title to recording title
                        setTitle(recording.getTitle());

                        // Show connecting status
                        showConnectionStatus(true, "Connecting to chat...");

                        // First check if server is reachable
                        addSystemMessage("Found recording: " + recording.getTitle());
                        verifyServerConnectivity();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error verifying recording", e);
                mainHandler.post(() -> showErrorAndFinish("Error: " + e.getMessage()));
            }
        });
    }

    private void showErrorAndFinish(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    private void verifyServerConnectivity() {
        executor.execute(() -> {
            try {
                Log.d(TAG, "Testing server connectivity...");
                boolean isServerReachable = RetrofitClient.getInstance(this).isServerReachable();

                mainHandler.post(() -> {
                    if (isServerReachable) {
                        showConnectionStatus(true, "Connecting to chat...");
                        testWebSocketConnection();
                    } else {
                        showConnectionStatus(false, "Server not reachable");
                        addSystemMessage("Server not reachable. Please check your network connection.");
                        scheduleReconnect();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error checking server connection", e);
                mainHandler.post(() -> {
                    showConnectionStatus(false, "Connection error");
                    addSystemMessage("Error checking server: " + e.getMessage());
                    scheduleReconnect();
                });
            }
        });
    }

    private void testWebSocketConnection() {
        // Test echo WebSocket first to verify basic WebSocket connectivity
        String echoUrl = "ws://" + getServerUrl() + "/ws/echo/";
        Log.d(TAG, "Testing WebSocket connection to: " + echoUrl);

        try {
            // Create test client as class member variable
            testClient = new ChatWebSocketClient(
                    echoUrl,
                    new ChatWebSocketClient.MessageListener() {
                        @Override
                        public void onMessageReceived(String sender, String message, String timestamp,
                                                      String messageId, boolean isHistorical) {
                            Log.d(TAG, "Echo test received: " + message);
                            addSystemMessage("Test connection successful");

                            // Now testClient is a class variable, accessible here
                            try {
                                testClient.close();
                                testClient = null; // Clean up reference
                            } catch (Exception e) {
                                Log.e(TAG, "Error closing test client", e);
                            }

                            // Connect to actual chat after successful test
                            mainHandler.postDelayed(() -> connectWebSocket(), 500);
                        }

                        @Override
                        public void onTypingStarted() { }

                        @Override
                        public void onConnectionStateChange(boolean connected, String message) {
                            if (connected) {
                                addSystemMessage("Test connection established");
                                // Send a test message
                                try {
                                    JSONObject testMessage = new JSONObject();
                                    testMessage.put("message", "test");
                                    testMessage.put("device_id", deviceId);
                                    testClient.send(testMessage.toString());
                                } catch (Exception e) {
                                    Log.e(TAG, "Error sending test message", e);
                                }
                            } else {
                                addSystemMessage("Test connection failed: " + message);
                                // Try direct connection instead
                                connectWebSocket();
                            }
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Log.e(TAG, "Echo test error: " + errorMessage);
                            addSystemMessage("Test connection error: " + errorMessage);
                            // Try direct connection after error
                            connectWebSocket();
                        }

                        @Override
                        public String getWebSocketUrl() {
                            return null;
                        }
                    },
                    deviceId,
                    "test"
            );

            // Add headers
            testClient.addHeader("User-Agent", "AndroidVoiceRecorder");
            testClient.addHeader("X-Client-Type", "android_mobile");
            testClient.addHeader("X-Device-ID", deviceId);

            // Connect
            testClient.connect();
            addSystemMessage("Testing connection...");

        } catch (Exception e) {
            Log.e(TAG, "Error creating test client", e);
            addSystemMessage("Error testing connection: " + e.getMessage());
            // Try direct connection after error
            connectWebSocket();
        }
    }

    private void connectWebSocket() {
        // Don't create multiple connections
        if (connectionState == STATE_CONNECTING || connectionState == STATE_CONNECTED) {
            return;
        }

        try {
            // Close any existing connection
            if (chatWebSocket != null) {
                chatWebSocket.closePermanently();
                chatWebSocket = null;
            }

            connectionState = STATE_CONNECTING;
            showConnectionStatus(true, "Connecting to chat...");

            String serverUrl = getServerUrl();
            // Build WebSocket URL
            String chatUrl = String.format("ws://%s/ws/chat/%s/%s/",
                    serverUrl, deviceId, recordingId);

            Log.d(TAG, "Connecting to chat WebSocket: " + chatUrl);
            addSystemMessage("Connecting to chat...");

            // Create the WebSocket client
            chatWebSocket = new ChatWebSocketClient(chatUrl, this, deviceId, recordingId);

            // Add headers for identification
            chatWebSocket.addHeader("X-Device-ID", deviceId);
            chatWebSocket.addHeader("X-Recording-ID", recordingId);
            chatWebSocket.addHeader("User-Agent", "AndroidVoiceRecorder");
            chatWebSocket.addHeader("X-Client-Type", "android_mobile");

            // Connect to the WebSocket
            chatWebSocket.connect();
            Log.d(TAG, "WebSocket connect() called");

            // Set connection timeout
            mainHandler.postDelayed(() -> {
                if (connectionState != STATE_CONNECTED && chatWebSocket != null) {
                    Log.e(TAG, "WebSocket connection timed out");
                    chatWebSocket.close();
                    connectionState = STATE_DISCONNECTED;
                    onConnectionStateChange(false, "Connection timeout");
                    scheduleReconnect();
                }
            }, 10000);

        } catch (URISyntaxException e) {
            Log.e(TAG, "URI syntax error: " + e.getMessage(), e);
            connectionState = STATE_DISCONNECTED;
            showConnectionStatus(false, "Invalid URL: " + e.getMessage());
            addSystemMessage("Error connecting: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error connecting to WebSocket: " + e.getMessage(), e);
            connectionState = STATE_DISCONNECTED;
            showConnectionStatus(false, "Error: " + e.getMessage());
            addSystemMessage("Error connecting: " + e.getMessage());
        }
    }

    private void scheduleReconnect() {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        reconnectAttempts++;
        int delay = Math.min(30000, 1000 * (int) Math.pow(2, Math.min(reconnectAttempts, 5)));

        Log.d(TAG, "Scheduling reconnect attempt #" + reconnectAttempts + " in " + delay + "ms");
        addSystemMessage("Will retry connection in " + (delay / 1000) + " seconds...");

        mainHandler.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                verifyServerConnectivity();
            }
        }, delay);
    }

    private String getServerUrl() {
        String fullUrl = RetrofitClient.getInstance(this).getWebSocketBaseUrl();

        // Clean up URL
        fullUrl = fullUrl
                .replace("http://", "")
                .replace("https://", "")
                .replace("ws://", "")
                .replace("wss://", "");

        // Remove trailing slashes
        while (fullUrl.endsWith("/")) {
            fullUrl = fullUrl.substring(0, fullUrl.length() - 1);
        }

        Log.d(TAG, "WebSocket server URL: " + fullUrl);
        return fullUrl;
    }

    private void showConnectionStatus(boolean isConnecting, String statusText) {
        connectionStatusView.setVisibility(View.VISIBLE);
        connectionStatus.setText(statusText);
        connectionProgress.setVisibility(isConnecting ? View.VISIBLE : View.GONE);
    }

    private void hideConnectionStatus() {
        connectionStatusView.setVisibility(View.GONE);
    }

    private void addMessageToChat(ChatMessage message) {
        // Check for duplicates by ID
        if (message.getMessageId() != null && messageIds.contains(message.getMessageId())) {
            // Skip duplicate message
            return;
        }

        // Ignore empty messages
        if (TextUtils.isEmpty(message.getMessage()) && message.getType() != ChatMessage.TYPE_SYSTEM) {
            return;
        }

        // Add message and update UI
        chatMessages.add(message);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);

        // Add ID to tracking set if available
        if (message.getMessageId() != null) {
            messageIds.add(message.getMessageId());
        }
    }

    private void addSystemMessage(String message) {
        ChatMessage systemMessage = new ChatMessage(
                message,
                ChatMessage.TYPE_SYSTEM,
                "System",
                getCurrentTimestamp(),
                null,
                false
        );
        addMessageToChat(systemMessage);
    }

    private String getCurrentTimestamp() {
        return android.text.format.DateFormat.format(
                "yyyy-MM-dd HH:mm:ss",
                new java.util.Date()
        ).toString();
    }

    private void sendMessage(String message) {
        if (message.trim().isEmpty()) {
            return;
        }

        if (connectionState != STATE_CONNECTED || chatWebSocket == null) {
            Log.d(TAG, "Not connected - queueing message");

            // Save message locally
            saveLocalMessage(message);

            // Show in UI
            ChatMessage chatMessage = new ChatMessage(
                    message,
                    ChatMessage.TYPE_DEVICE,
                    "You",
                    getCurrentTimestamp(),
                    null,
                    true // Mark as offline
            );
            addMessageToChat(chatMessage);

            // Queue message
            queuedMessages.add(message);

            Toast.makeText(this, "Message saved offline", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Send to server
            chatWebSocket.sendMessage(message, deviceId);

            // Add to local display immediately (will be updated when server confirms)
            ChatMessage chatMessage = new ChatMessage(
                    message,
                    ChatMessage.TYPE_DEVICE,
                    "You",
                    getCurrentTimestamp(),
                    null,
                    false
            );
            addMessageToChat(chatMessage);

            // Save locally (will be updated with server ID when received)
            saveLocalMessage(message);

        } catch (Exception e) {
            Log.e(TAG, "Error sending message", e);
            Toast.makeText(this, "Error sending message", Toast.LENGTH_SHORT).show();

            // Add to local display as offline
            ChatMessage chatMessage = new ChatMessage(
                    message,
                    ChatMessage.TYPE_DEVICE,
                    "You",
                    getCurrentTimestamp(),
                    null,
                    true
            );
            addMessageToChat(chatMessage);

            // Save locally
            saveLocalMessage(message);

            // Queue for later
            queuedMessages.add(message);
        }
    }

    private void saveLocalMessage(String message) {
        executor.execute(() -> {
            try {
                RecordingRepository repository = new RecordingRepository(this);
                repository.saveLocalChatMessage(
                        Long.parseLong(recordingId),
                        message,
                        true, // isFromDevice
                        null  // messageId (will be updated when received from server)
                );
            } catch (Exception e) {
                Log.e(TAG, "Error saving message locally", e);
            }
        });
    }

    private void loadLocalMessages() {
        executor.execute(() -> {
            try {
                RecordingRepository repository = new RecordingRepository(this);
                List<LocalChatMessage> localMessages = repository.getLocalChatMessages(Long.parseLong(recordingId));

                if (!localMessages.isEmpty()) {
                    mainHandler.post(() -> {
                        addSystemMessage("Loading local message history...");

                        for (LocalChatMessage msg : localMessages) {
                            // Skip empty messages
                            if (TextUtils.isEmpty(msg.getMessage())) {
                                continue;
                            }

                            // Skip messages we already have
                            if (msg.getMessageId() != null && messageIds.contains(msg.getMessageId())) {
                                continue;
                            }

                            ChatMessage uiMessage = new ChatMessage(
                                    msg.getMessage(),
                                    msg.isFromDevice() ? ChatMessage.TYPE_DEVICE : ChatMessage.TYPE_ADMIN,
                                    msg.isFromDevice() ? "You" : "Admin",
                                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                            .format(new java.util.Date(msg.getTimestamp())),
                                    msg.getMessageId(),
                                    true // Mark as historical
                            );
                            addMessageToChat(uiMessage);

                            // Add ID to tracking set if available
                            if (msg.getMessageId() != null) {
                                messageIds.add(msg.getMessageId());
                            }
                        }

                        addSystemMessage("Local message history loaded.");
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading local messages", e);
            }
        });
    }

    @Override
    public void onMessageReceived(String sender, String message, String timestamp,
                                  String messageId, boolean isHistorical) {
        // Skip empty messages
        if (TextUtils.isEmpty(message)) {
            return;
        }

        mainHandler.post(() -> {
            int messageType;
            String displaySender;

            if ("admin".equals(sender)) {
                messageType = ChatMessage.TYPE_ADMIN;
                displaySender = "admin";
            } else if ("system".equals(sender)) {
                messageType = ChatMessage.TYPE_SYSTEM;
                displaySender = "System";
            } else {
                messageType = ChatMessage.TYPE_DEVICE;
                displaySender = deviceId.equals(sender) ? "You" : sender;
            }

            // Check for duplicate messages
            if (messageId != null && messageIds.contains(messageId)) {
                return; // Skip duplicates
            }

            ChatMessage chatMessage = new ChatMessage(
                    message,
                    messageType,
                    displaySender,
                    timestamp,
                    messageId,
                    isHistorical
            );

            addMessageToChat(chatMessage);

            // Hide typing indicator after receiving a message
            typingIndicator.setVisibility(View.GONE);

            // Save message to local database if it's from admin
            if (messageType == ChatMessage.TYPE_ADMIN && messageId != null) {
                executor.execute(() -> {
                    try {
                        RecordingRepository repository = new RecordingRepository(ChatActivity.this);
                        repository.saveLocalChatMessage(
                                Long.parseLong(recordingId),
                                message,
                                false, // Not from device
                                messageId
                        );
                    } catch (Exception e) {
                        Log.e(TAG, "Error saving admin message locally", e);
                    }
                });
            }
        });
    }

    @Override
    public void onTypingStarted() {
        mainHandler.post(() -> {
            // Show typing indicator
            typingIndicator.setVisibility(View.VISIBLE);

            // Auto-hide after 5 seconds
            mainHandler.postDelayed(() ->
                    typingIndicator.setVisibility(View.GONE), 5000);
        });
    }

    @Override
    public void onConnectionStateChange(boolean connected, String message) {
        Log.d(TAG, "Connection state changed: connected=" + connected + ", message=" + message);

        mainHandler.post(() -> {
            if (connected) {
                connectionState = STATE_CONNECTED;
                isConnected = true;
                hideConnectionStatus();
                addSystemMessage("Connected to chat");
                updateInputState(true);
                reconnectAttempts = 0; // Reset on successful connection

                // Send any queued messages
                if (!queuedMessages.isEmpty()) {
                    sendQueuedMessages();
                }
            } else {
                connectionState = STATE_DISCONNECTED;
                isConnected = false;
                showConnectionStatus(false, message);
                updateInputState(false);
                addSystemMessage("Disconnected: " + message);

                // Don't reconnect if it's an auth failure or explicit close
                if (!isFinishing() && !message.contains("403") && !message.contains("Authentication")) {
                    scheduleReconnect();
                }
            }
        });
    }

    private void sendQueuedMessages() {
        if (queuedMessages.isEmpty() || connectionState != STATE_CONNECTED) {
            return;
        }

        Log.d(TAG, "Sending " + queuedMessages.size() + " queued messages");
        List<String> toSend = new ArrayList<>(queuedMessages);
        queuedMessages.clear();

        for (String message : toSend) {
            sendMessage(message);

            // Add small delay between messages
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void onError(String errorMessage) {
        mainHandler.post(() -> {
            showConnectionStatus(false, "Error");
            addSystemMessage("Error: " + errorMessage);
        });
    }

    @Override
    public String getWebSocketUrl() {
        // Not used in this implementation
        return "";
    }

    private final BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean hasConnectivity = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

            Log.d(TAG, "Network connectivity changed: " + hasConnectivity);

            if (hasConnectivity && connectionState == STATE_DISCONNECTED) {
                // Network came back online, try to reconnect
                addSystemMessage("Network connection restored. Reconnecting...");
                reconnectAttempts = 0; // Reset attempts on new connectivity
                verifyServerConnectivity();
            } else if (!hasConnectivity && connectionState != STATE_DISCONNECTED) {
                // Network went offline
                addSystemMessage("Network connection lost. Waiting for connectivity...");
                connectionState = STATE_DISCONNECTED;
                showConnectionStatus(false, "No network connection");
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();

        // Check if we need to reconnect
        if (connectionState == STATE_DISCONNECTED && !isReconnecting) {
            verifyServerConnectivity();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Unregister network receiver
        try {
            unregisterReceiver(networkReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering network receiver", e);
        }

        // Close WebSockets
        if (testClient != null) {
            testClient.closePermanently();
            testClient = null;
        }

        if (chatWebSocket != null) {
            chatWebSocket.closePermanently();
            chatWebSocket = null;
        }

        // Shutdown executor
        executor.shutdownNow();
    }
}

/**
 * Chat Adapter for RecyclerView
 */
class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {
    private List<ChatMessage> messages;

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
        setHasStableIds(true); // Add this line
    }

    @Override
    public long getItemId(int position) {
        // Use message ID as stable ID if available, otherwise use position
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

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType();
    }

    @Override
    public ChatViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
        View view;
        if (viewType == ChatMessage.TYPE_ADMIN) {
            view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_admin, parent, false);
        } else if (viewType == ChatMessage.TYPE_SYSTEM) {
            view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_system, parent, false);
        } else {
            view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_device, parent, false);
        }
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ChatViewHolder holder, int position) {
        ChatMessage message = messages.get(position);

        // Set the message text - THIS LINE IS MISSING
        holder.messageText.setText(message.getMessage());

        // Set the background and alignment based on message type
        switch (message.getType()) {
            case ChatMessage.TYPE_ADMIN:
                // Admin messages should appear different from device messages
                holder.messageText.setBackgroundResource(R.drawable.bg_message_admin);
                String adminInfo = message.getSender() + " · " + message.getTimestamp();
                if (message.isHistorical()) {
                    adminInfo += " · Historical";
                }
                holder.infoText.setText(adminInfo);
                break;
            case ChatMessage.TYPE_SYSTEM:
                // System messages are centered informational messages
                holder.messageText.setBackgroundResource(R.drawable.bg_message_system);
                holder.infoText.setText(message.getTimestamp());
                break;
            default:
                // Device messages
                holder.messageText.setBackgroundResource(R.drawable.bg_message_device);
                String deviceInfo = "You · " + message.getTimestamp();
                if (message.isHistorical()) {
                    deviceInfo += " · Offline";
                }
                holder.infoText.setText(deviceInfo);
                break;
        }

        // Add message ID indicator if available (for debugging only - can be removed in production)
        if (message.getMessageId() != null) {
            String currentInfo = holder.infoText.getText().toString();
            holder.infoText.setText(currentInfo + " · ID:" + message.getMessageId());
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

class ChatMessage {
    public static final int TYPE_ADMIN = 2;
    public static final int TYPE_DEVICE = 1;
    public static final int TYPE_SYSTEM = 3;

    private String message;
    private int type;
    private String sender;
    private String timestamp;
    private String messageId; // Added field
    private boolean isHistorical; // Added field

    // Main constructor with all fields
    public ChatMessage(String message, int type, String sender, String timestamp, String messageId, boolean isHistorical) {
        this.message = message;
        this.type = type;
        this.sender = sender;
        this.timestamp = timestamp;
        this.messageId = messageId;
        this.isHistorical = isHistorical;
    }

    // Simplified constructor for backward compatibility
    public ChatMessage(String message, int type, String sender, String timestamp) {
        this(message, type, sender, timestamp, null, false);
    }

    // Getters for all fields
    public String getMessage() {
        return message;
    }

    public int getType() {
        return type;
    }

    public String getSender() {
        return sender;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getMessageId() {
        return messageId;
    }

    public boolean isHistorical() {
        return isHistorical;
    }
}