// ChatActivity.java - VERSION COMPLÈTE ET FONCTIONNELLE

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

import org.json.JSONObject;

import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

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

    // ✅ TIMEZONE CONFIGURATION
    private static final String SERVER_TIMEZONE = "Africa/Casablanca";
    private SimpleDateFormat serverDateFormat;
    private SimpleDateFormat isoDateFormat;
    private SimpleDateFormat displayDateFormat;
    private SimpleDateFormat utcFormat;

    // ✅ ADD: Timezone offset tracking
    private long serverTimeOffset = 0;
    private boolean timezoneSynced = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // ✅ FIXED: Initialize timezone formatters with server timezone
        initializeTimezoneFormatters();

        // Get parameters and initialize
        recordingId = getIntent().getStringExtra("recording_id");
        deviceId = DeviceIdHelper.getDeviceId(this);

        // Initialize other components
        repository = new RecordingRepository(this);
        initializeViews();

        // ✅ IMPORTANT: Sync with server timezone FIRST
        syncServerTimezone(() -> {
            loadRecordingInfo();
        });
    }

    // ✅ TIMEZONE FORMATTERS INITIALIZATION
    private void initializeTimezoneFormatters() {
        try {
            TimeZone serverTimeZone = TimeZone.getTimeZone(SERVER_TIMEZONE);

            // All formatters use server timezone
            serverDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            serverDateFormat.setTimeZone(serverTimeZone);

            isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            isoDateFormat.setTimeZone(serverTimeZone);

            displayDateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
            displayDateFormat.setTimeZone(serverTimeZone);

            utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
            utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            Log.d(TAG, "✅ Timezone formatters initialized for: " + SERVER_TIMEZONE);
            Log.d(TAG, "Server timezone offset: " + serverTimeZone.getRawOffset() / (1000 * 60 * 60) + " hours");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error initializing timezone formatters", e);
        }
    }

    private void syncServerTimezone(Runnable callback) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "🔄 Syncing with server timezone...");

                // Call server time API
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build();

                String serverUrl = RetrofitClient.getInstance(this).getServerUrl();
                String timeApiUrl = serverUrl.replace("/api/", "/api/server-time/");

                Request request = new Request.Builder()
                        .url(timeApiUrl)
                        .addHeader("X-Requested-With", "XMLHttpRequest")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);

                        if ("success".equals(jsonResponse.getString("status"))) {
                            // Parse server time
                            String serverTimeStr = jsonResponse.getString("server_time_local");
                            Date serverTime = parseServerTime(serverTimeStr);
                            Date deviceTime = new Date();

                            if (serverTime != null) {
                                serverTimeOffset = serverTime.getTime() - deviceTime.getTime();
                                timezoneSynced = true;

                                Log.d(TAG, "✅ Timezone sync successful:");
                                Log.d(TAG, "  Server time: " + serverTime);
                                Log.d(TAG, "  Device time: " + deviceTime);
                                Log.d(TAG, "  Offset: " + serverTimeOffset + "ms (" + (serverTimeOffset/1000/60/60) + " hours)");
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error syncing server timezone", e);
                }

                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.run();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "❌ Error in timezone sync", e);
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.run();
                    }
                });
            }
        });
    }

    // ✅ HELPER: Parse server time properly
    private Date parseServerTime(String serverTimeStr) {
        try {
            if (serverTimeStr.contains("T")) {
                return isoDateFormat.parse(serverTimeStr.substring(0, 19));
            } else {
                return serverDateFormat.parse(serverTimeStr);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing server time: " + serverTimeStr, e);
            return null;
        }
    }


    // ✅ COMPLETE initializeViews METHOD
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

    // ✅ COMPLETE loadRecordingInfo METHOD
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

    // ✅ COMPLETE loadLocalMessages METHOD
    // ✅ UPDATED: Load local messages with timezone correction
    private void loadLocalMessages() {
        executor.execute(() -> {
            try {
                List<LocalChatMessage> localMessages = repository.getLocalChatMessages(Long.parseLong(recordingId));

                mainHandler.post(() -> {
                    if (!localMessages.isEmpty()) {
                        addSystemMessage("Loading " + localMessages.size() + " local messages with timezone sync...");

                        for (LocalChatMessage msg : localMessages) {
                            if (TextUtils.isEmpty(msg.getMessage()) ||
                                    isPingPongMessage(msg.getMessage()) ||
                                    processedMessageIds.contains(msg.getMessageId())) {
                                continue;
                            }

                            // ✅ FIXED: Format timestamp with server timezone
                            String formattedTimestamp = formatTimestamp(String.valueOf(msg.getTimestamp()));

                            ChatMessage chatMessage = new ChatMessage(
                                    msg.getMessage(),
                                    msg.isFromDevice() ? ChatMessage.TYPE_DEVICE : ChatMessage.TYPE_ADMIN,
                                    msg.isFromDevice() ? "You" : "Admin",
                                    formattedTimestamp,
                                    msg.getMessageId(),
                                    true
                            );

                            addMessageToChat(chatMessage);

                            if (msg.getMessageId() != null) {
                                processedMessageIds.add(msg.getMessageId());
                            }
                        }

                        addSystemMessage("✅ Local messages loaded with correct timezone");
                        initialHistoryLoaded = true;
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading local messages", e);
                mainHandler.post(() -> addSystemMessage("Error loading local messages"));
            }
        });
    }



    // ✅ COMPLETE connectToServerSafely METHOD
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

    // ✅ COMPLETE startWebSocketConnectionSafely METHOD
    private void startWebSocketConnectionSafely() {
        try {
            closeExistingConnectionAndWait();

            String serverUrl = getWebSocketUrl();
            Log.d(TAG, "Creating WebSocket connection to: " + serverUrl);

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

    // ✅ COMPLETE closeExistingConnectionAndWait METHOD
    private void closeExistingConnectionAndWait() {
        if (chatWebSocket != null) {
            Log.d(TAG, "Closing existing WebSocket connection");

            try {
                ChatWebSocketClient clientToClose = chatWebSocket;
                chatWebSocket = null;
                clientToClose.closePermanently();

                Thread.sleep(100);

            } catch (Exception e) {
                Log.e(TAG, "Error closing existing WebSocket", e);
            }
        }
    }

    // ✅ COMPLETE getWebSocketUrl METHOD
    public String getWebSocketUrl() {
        String baseUrl = RetrofitClient.getInstance(this).getWebSocketBaseUrl();
        baseUrl = baseUrl.replace("http://", "").replace("https://", "")
                .replace("ws://", "").replace("wss://", "");

        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        return String.format("ws://%s/ws/chat/%s/%s/", baseUrl, deviceId, recordingId);
    }

    // ✅ COMPLETE sendMessage METHOD
    private void sendMessage() {
        String message = messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(message)) {
            return;
        }

        if (!isConnected.get() || chatWebSocket == null) {
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
            chatWebSocket.sendMessage(message, deviceId);
            messageInput.setText("");
            saveMessageLocally(message, true);

        } catch (Exception e) {
            Log.e(TAG, "Error sending message", e);
            Toast.makeText(this, "Error sending message", Toast.LENGTH_SHORT).show();
        }
    }

    // ✅ FIXED: Save message with correct server timezone
    private void saveMessageLocally(String message, boolean isFromDevice) {
        executor.execute(() -> {
            try {
                // ✅ CRITICAL: Save with server timezone timestamp
                long timestamp = System.currentTimeMillis();
                if (timezoneSynced) {
                    timestamp += serverTimeOffset; // Adjust to server time
                }

                // Create message with server timestamp
                repository.saveLocalChatMessage(
                        Long.parseLong(recordingId),
                        message,
                        isFromDevice,
                        null,
                        timestamp // Pass server-adjusted timestamp
                );

                Log.d(TAG, "✅ Message saved with server timestamp: " + new Date(timestamp));

            } catch (Exception e) {
                Log.e(TAG, "Error saving message locally", e);
            }
        });
    }

    // ✅ COMPLETE retryConnection METHOD
    private void retryConnection() {
        Log.d(TAG, "Manual retry connection requested");
        reconnectAttempts = 0;
        isConnecting.set(false);
        isConnected.set(false);
        connectToServerSafely();
    }

    // ✅ COMPLETE scheduleReconnect METHOD
    private void scheduleReconnect() {
        if (isDestroyed || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            showConnectionStatus("Max retries reached", false);
            return;
        }

        reconnectAttempts++;
        long delay = Math.min(30000, 2000L * reconnectAttempts);

        showConnectionStatus("Retrying in " + (delay / 1000) + "s...", true);

        mainHandler.postDelayed(() -> {
            if (!isDestroyed) {
                connectToServerSafely();
            }
        }, delay);
    }

    // ✅ FIXED: Format timestamp with server timezone
    private String formatTimestamp(String timestamp) {
        if (TextUtils.isEmpty(timestamp)) {
            return getCurrentTimestamp();
        }

        try {
            Date date = parseTimestamp(timestamp);
            if (date != null) {
                // ✅ FIXED: Apply server timezone offset if synced
                if (timezoneSynced && serverTimeOffset != 0) {
                    date = new Date(date.getTime() + serverTimeOffset);
                }

                // Format using server timezone formatter
                return displayDateFormat.format(date);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error formatting timestamp: " + timestamp, e);
        }

        return getCurrentTimestamp();
    }

    // ✅ FIXED: Parse timestamp with proper timezone handling
    private Date parseTimestamp(String timestamp) {
        Date date = null;

        try {
            if (timestamp.endsWith("Z")) {
                date = utcFormat.parse(timestamp);
            } else if (timestamp.contains("T") && (timestamp.contains("+") || timestamp.lastIndexOf("-") > 10)) {
                // Handle timezone-aware ISO format
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    try {
                        java.time.ZonedDateTime zonedDateTime = java.time.ZonedDateTime.parse(timestamp);
                        date = Date.from(zonedDateTime.toInstant());
                    } catch (Exception e) {
                        String simplifiedTimestamp = timestamp.substring(0, 19);
                        date = isoDateFormat.parse(simplifiedTimestamp);
                    }
                } else {
                    String simplifiedTimestamp = timestamp.substring(0, 19);
                    date = isoDateFormat.parse(simplifiedTimestamp);
                }
            } else if (timestamp.contains("T")) {
                date = isoDateFormat.parse(timestamp);
            } else if (timestamp.contains("-") && timestamp.contains(":")) {
                date = serverDateFormat.parse(timestamp);
            } else {
                // Unix timestamp
                long unixTimestamp = Long.parseLong(timestamp);
                if (unixTimestamp < 10000000000L) {
                    unixTimestamp *= 1000;
                }
                date = new Date(unixTimestamp);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing timestamp: " + timestamp, e);
        }

        return date;
    }

    // ✅ FIXED: Get current timestamp in server timezone
    private String getCurrentTimestamp() {
        Date now = new Date();
        if (timezoneSynced && serverTimeOffset != 0) {
            now = new Date(now.getTime() + serverTimeOffset);
        }
        return displayDateFormat.format(now);
    }

    // ✅ UI HELPER METHODS
    private void showConnectionStatus(String status, boolean showProgress) {
        if (connectionStatus != null) {
            connectionStatus.setText(status);
        }
        if (connectionProgress != null) {
            connectionProgress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        }
        if (connectionStatusContainer != null) {
            connectionStatusContainer.setVisibility(View.VISIBLE);
        }

        if (!showProgress && status.contains("Connected")) {
            mainHandler.postDelayed(() -> {
                if (connectionStatusContainer != null) {
                    connectionStatusContainer.setVisibility(View.GONE);
                }
            }, 2000);
        }
    }

    private void updateInputState(boolean enabled) {
        if (messageInput != null) {
            messageInput.setEnabled(enabled);
        }
        if (sendButton != null) {
            sendButton.setEnabled(enabled);
        }

        if (messageInput != null) {
            if (enabled) {
                messageInput.setHint("Type a message...");
            } else {
                messageInput.setHint("Connecting...");
            }
        }
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
        if (TextUtils.isEmpty(message.getMessage())) {
            return true;
        }

        if (isPingPongMessage(message.getMessage())) {
            return true;
        }

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

    private void showErrorAndFinish(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    // ✅ OVERRIDE: WebSocket message handler with timezone fix
    @Override
    public void onMessageReceived(String sender, String message, String timestamp, String messageId, boolean isHistorical) {
        if (isDestroyed || TextUtils.isEmpty(message) || isPingPongMessage(message)) {
            return;
        }

        if (messageId != null && processedMessageIds.contains(messageId)) {
            Log.d(TAG, "Skipping duplicate message: " + messageId);
            return;
        }

        mainHandler.post(() -> {
            int messageType;
            String displaySender;

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
                    displaySender = deviceId.equals(sender) ? "You" : sender;
                    break;
            }

            // ✅ FIXED: Format timestamp with server timezone
            String formattedTimestamp = formatTimestamp(timestamp);

            ChatMessage chatMessage = new ChatMessage(
                    message, messageType, displaySender, formattedTimestamp, messageId, isHistorical
            );

            addMessageToChat(chatMessage);

            if (messageId != null) {
                processedMessageIds.add(messageId);
            }

            // Save message locally with server timezone
            if (messageType == ChatMessage.TYPE_ADMIN && !isHistorical) {
                saveMessageLocally(message, false);
            }

            Log.d(TAG, "✅ Message displayed with server timezone - " + sender + ": " + message + " at " + formattedTimestamp);
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
            isConnecting.set(false);

            if (connected) {
                reconnectAttempts = 0;
                showConnectionStatus("Connected", false);
                updateInputState(true);
                addSystemMessage("Connected to chat server");
            } else {
                showConnectionStatus(message, false);
                updateInputState(false);
                addSystemMessage("Disconnected: " + message);

                if (!isDestroyed && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    scheduleReconnect();
                }
            }
        });
    }

    @Override
    public void onError(String errorMessage) {
        mainHandler.post(() -> {
            isConnecting.set(false);
            addSystemMessage("Error: " + errorMessage);
            Log.e(TAG, "WebSocket error: " + errorMessage);
        });
    }

    // ✅ NETWORK RECEIVER
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

    @Override
    protected void onResume() {
        super.onResume();

        if (!isConnected.get() && !isConnecting.get() && !isDestroyed) {
            connectToServerSafely();
        }
    }

    @Override
    protected void onDestroy() {
        isDestroyed = true;

        closeExistingConnectionAndWait();

        try {
            unregisterReceiver(networkReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering network receiver", e);
        }

        executor.shutdownNow();
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
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view;

        switch (viewType) {
            case ChatMessage.TYPE_ADMIN:
                view = inflater.inflate(R.layout.item_message_admin, parent, false);
                break;
            case ChatMessage.TYPE_SYSTEM:
                view = inflater.inflate(R.layout.item_message_system, parent, false);
                break;
            default: // TYPE_DEVICE
                view = inflater.inflate(R.layout.item_message_device, parent, false);
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