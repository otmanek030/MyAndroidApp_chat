// ChatWebSocketClient.java - FIXED VERSION to prevent race conditions

package com.plcoding.audiorecorder.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatWebSocketClient extends WebSocketClient {
    private static final String TAG = "ChatWebSocketClient";

    // Connection constants
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final int CONNECTION_TIMEOUT = 15000;
    private static final long PING_INTERVAL = 30000; // 30 seconds
    private static final long PING_TIMEOUT = 10000; // 10 seconds

    // FIXED: Add connection state management
    private static final AtomicInteger instanceCounter = new AtomicInteger(0);
    private final int instanceId;

    // Ping/pong management
    private final Handler pingHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean waitingForPong = new AtomicBoolean(false);
    private final AtomicBoolean isPinging = new AtomicBoolean(false);

    // Message deduplication
    private final Set<String> recentMessageIds = new HashSet<>();
    private final Set<String> recentMessageContent = new HashSet<>();

    // Connection state - FIXED: Better state management
    private volatile boolean isClosingPermanently = false;
    private volatile boolean manualClose = false;
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final AtomicBoolean hasConnected = new AtomicBoolean(false);
    private int reconnectAttempts = 0;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Client info
    private final MessageListener listener;
    private final String deviceId;
    private final String recordingId;

    // Ping runnable
    private final Runnable pingRunnable = new Runnable() {
        @Override
        public void run() {
            if (isOpen() && !waitingForPong.get() && !isClosingPermanently) {
                try {
                    JSONObject pingMessage = new JSONObject();
                    pingMessage.put("message", "ping");
                    pingMessage.put("ping", true);
                    pingMessage.put("sender_type", "device");
                    pingMessage.put("device_id", deviceId);
                    pingMessage.put("timestamp", getCurrentTimestamp());

                    send(pingMessage.toString());
                    waitingForPong.set(true);

                    Log.d(TAG, "[" + instanceId + "] Ping sent to server");

                    // Set timeout for pong response
                    pingHandler.postDelayed(() -> {
                        if (waitingForPong.get() && !isClosingPermanently) {
                            Log.w(TAG, "[" + instanceId + "] Pong timeout - connection may be dead");
                            waitingForPong.set(false);
                            reconnectAfterError("Ping timeout");
                        }
                    }, PING_TIMEOUT);

                } catch (Exception e) {
                    Log.e(TAG, "[" + instanceId + "] Error sending ping", e);
                }
            }

            // Schedule next ping
            if (isOpen() && !isClosingPermanently) {
                pingHandler.postDelayed(this, PING_INTERVAL);
            }
        }
    };

    public interface MessageListener {
        void onMessageReceived(String sender, String message, String timestamp, String messageId, boolean isHistorical);
        void onTypingStarted();
        void onConnectionStateChange(boolean connected, String message);
        void onError(String errorMessage);
        String getWebSocketUrl();
    }

    public ChatWebSocketClient(String serverUri, MessageListener listener, String deviceId, String recordingId) throws URISyntaxException {
        super(new URI(serverUri));

        // FIXED: Assign unique instance ID
        this.instanceId = instanceCounter.incrementAndGet();

        this.listener = listener;
        this.deviceId = deviceId;
        this.recordingId = recordingId;

        // Configure connection
        this.setConnectionLostTimeout(60); // 60 seconds

        Log.d(TAG, "[" + instanceId + "] Created WebSocket client for device: " + deviceId + ", recording: " + recordingId);
        Log.d(TAG, "[" + instanceId + "] WebSocket URI: " + serverUri);
    }

    @Override
    public void connect() {
        Log.d(TAG, "[" + instanceId + "] Starting connection to: " + uri.toString());

        // FIXED: Prevent multiple simultaneous connections
        if (isConnecting.getAndSet(true)) {
            Log.w(TAG, "[" + instanceId + "] Already connecting, skipping connection attempt");
            return;
        }

        if (isOpen()) {
            Log.w(TAG, "[" + instanceId + "] Already connected, skipping connection attempt");
            isConnecting.set(false);
            return;
        }

        try {
            // Add query parameter for device ID if not present
            String uriStr = uri.toString();
            if (!uriStr.contains("device_id=")) {
                String separator = uriStr.contains("?") ? "&" : "?";
                uriStr = uriStr + separator + "device_id=" + deviceId;
                uri = new URI(uriStr);
                Log.d(TAG, "[" + instanceId + "] Updated URI with device_id: " + uri.toString());
            }

            // Add headers for identification
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "AndroidVoiceRecorder/1.0");
            headers.put("X-Client-Type", "android_mobile");
            headers.put("X-Device-ID", deviceId);
            headers.put("X-Instance-ID", String.valueOf(instanceId)); // FIXED: Add instance tracking
            if (recordingId != null) {
                headers.put("X-Recording-ID", recordingId);
            }

            // Add headers to connection
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                addHeader(entry.getKey(), entry.getValue());
            }

        } catch (Exception e) {
            Log.e(TAG, "[" + instanceId + "] Error setting up connection", e);
            isConnecting.set(false);
            if (listener != null) {
                mainHandler.post(() -> listener.onError("Connection setup error: " + e.getMessage()));
            }
            return;
        }

        // Reset state
        isClosingPermanently = false;
        manualClose = false;

        // Start connection
        try {
            super.connect();
        } catch (Exception e) {
            Log.e(TAG, "[" + instanceId + "] Error during super.connect()", e);
            isConnecting.set(false);
            if (listener != null) {
                mainHandler.post(() -> listener.onError("Connection failed: " + e.getMessage()));
            }
            return;
        }

        // Set connection timeout
        mainHandler.postDelayed(() -> {
            if (!isOpen() && !isClosingPermanently && isConnecting.get()) {
                Log.e(TAG, "[" + instanceId + "] Connection timeout");
                isConnecting.set(false);
                if (listener != null) {
                    listener.onConnectionStateChange(false, "Connection timeout");
                }
                reconnectAfterError("Connection timeout");
            }
        }, CONNECTION_TIMEOUT);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        Log.d(TAG, "[" + instanceId + "] WebSocket connection opened successfully");
        Log.d(TAG, "[" + instanceId + "] Server handshake: " + handshakedata.getHttpStatus() + " " + handshakedata.getHttpStatusMessage());

        // FIXED: Update connection state atomically
        isConnecting.set(false);
        hasConnected.set(true);

        // Reset reconnection attempts
        reconnectAttempts = 0;
        manualClose = false;

        // Start ping/pong mechanism
        startPingPong();

        // Notify listener
        if (listener != null) {
            mainHandler.post(() -> listener.onConnectionStateChange(true, "Connected to chat server"));
        }
    }

    @Override
    public void onMessage(String message) {
        Log.d(TAG, "[" + instanceId + "] Received message: " + message);

        try {
            JSONObject jsonMessage = new JSONObject(message);

            // Handle ping/pong messages
            if (jsonMessage.optBoolean("ping", false)) {
                handleServerPing();
                return;
            }

            if (jsonMessage.optBoolean("pong", false)) {
                handleServerPong();
                return;
            }

            // Handle connection confirmation messages
            if ("connection".equals(jsonMessage.optString("type", ""))) {
                Log.d(TAG, "[" + instanceId + "] Received connection confirmation");
                return; // Don't pass to listener as regular message
            }

            // Handle regular messages
            String messageContent = jsonMessage.optString("message", "");
            String sender = jsonMessage.optString("sender_type", "system");
            String timestamp = jsonMessage.optString("timestamp", getCurrentTimestamp());
            String messageId = jsonMessage.optString("message_id", null);
            boolean isHistorical = jsonMessage.optBoolean("is_historical", false);

            // Skip empty messages and ping/pong
            if (messageContent.trim().isEmpty() ||
                    messageContent.equalsIgnoreCase("ping") ||
                    messageContent.equalsIgnoreCase("pong")) {
                return;
            }

            // Check for duplicates
            if (isDuplicateMessage(messageId, messageContent)) {
                Log.d(TAG, "[" + instanceId + "] Skipping duplicate message");
                return;
            }

            // Process message
            Log.d(TAG, "[" + instanceId + "] Processing message from " + sender + ": " + messageContent.substring(0, Math.min(50, messageContent.length())));

            if (listener != null) {
                mainHandler.post(() ->
                        listener.onMessageReceived(sender, messageContent, timestamp, messageId, isHistorical)
                );
            }

        } catch (JSONException e) {
            Log.e(TAG, "[" + instanceId + "] Error parsing JSON message", e);
            // Try to handle as plain text
            if (!message.trim().isEmpty() && listener != null) {
                mainHandler.post(() ->
                        listener.onMessageReceived("system", message, getCurrentTimestamp(), null, false)
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "[" + instanceId + "] Error handling message", e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        Log.d(TAG, "[" + instanceId + "] WebSocket closed: code=" + code + ", reason=" + reason + ", remote=" + remote);

        // FIXED: Update connection state
        isConnecting.set(false);
        hasConnected.set(false);

        // Stop ping/pong
        stopPingPong();

        // Notify listener
        if (listener != null) {
            final String closeMessage = "Connection closed: " + reason +
                    (code != 1000 && code != 1001 ? " (code " + code + ")" : "");

            mainHandler.post(() -> listener.onConnectionStateChange(false, closeMessage));
        }

        // Attempt reconnection for abnormal closures
        if (!manualClose && !isClosingPermanently && shouldReconnect(code)) {
            Log.d(TAG, "[" + instanceId + "] Attempting reconnection after abnormal closure");
            attemptReconnect();
        }
    }

    @Override
    public void onError(Exception ex) {
        Log.e(TAG, "[" + instanceId + "] WebSocket error: " + ex.getMessage(), ex);

        // FIXED: Update connection state on error
        isConnecting.set(false);

        if (listener != null) {
            String errorMsg = ex.getMessage() != null ? ex.getMessage() : "Unknown connection error";
            mainHandler.post(() -> {
                listener.onConnectionStateChange(false, "Error: " + errorMsg);
                listener.onError("Connection error: " + errorMsg);
            });
        }

        // Attempt reconnection on error
        if (!manualClose && !isClosingPermanently) {
            reconnectAfterError("Connection error: " + ex.getMessage());
        }
    }

    // === PING/PONG MANAGEMENT ===

    private void startPingPong() {
        stopPingPong(); // Stop any existing ping task

        if (isPinging.compareAndSet(false, true)) {
            // Start ping cycle after initial delay
            pingHandler.postDelayed(pingRunnable, 5000); // 5 second initial delay
            Log.d(TAG, "[" + instanceId + "] Started ping/pong mechanism");
        }
    }

    private void stopPingPong() {
        pingHandler.removeCallbacks(pingRunnable);
        waitingForPong.set(false);
        isPinging.set(false);
        Log.d(TAG, "[" + instanceId + "] Stopped ping/pong mechanism");
    }

    private void handleServerPing() {
        Log.d(TAG, "[" + instanceId + "] Received ping from server, sending pong");

        if (isOpen()) {
            try {
                JSONObject pong = new JSONObject();
                pong.put("message", "pong");
                pong.put("pong", true);
                pong.put("device_id", deviceId);
                pong.put("timestamp", getCurrentTimestamp());
                send(pong.toString());
            } catch (Exception e) {
                Log.e(TAG, "[" + instanceId + "] Error sending pong response", e);
            }
        }
    }

    private void handleServerPong() {
        Log.d(TAG, "[" + instanceId + "] Received pong from server");
        waitingForPong.set(false);
    }

    // === MESSAGE SENDING ===

    public void sendMessage(String message, String deviceId) {
        if (message == null || message.trim().isEmpty()) {
            Log.d(TAG, "[" + instanceId + "] Attempting to send empty message, skipping");
            return;
        }

        if (!isOpen()) {
            Log.e(TAG, "[" + instanceId + "] Cannot send message: WebSocket not connected");
            if (listener != null) {
                mainHandler.post(() -> listener.onError("Cannot send message: not connected"));
            }
            return;
        }

        try {
            JSONObject jsonMessage = new JSONObject();
            jsonMessage.put("message", message);
            jsonMessage.put("sender_type", "device");  // Always "device" for mobile app
            jsonMessage.put("device_id", deviceId);
            jsonMessage.put("timestamp", getCurrentTimestamp());

            String jsonString = jsonMessage.toString();
            send(jsonString);

            Log.d(TAG, "[" + instanceId + "] Message sent: " + message);

        } catch (JSONException e) {
            Log.e(TAG, "[" + instanceId + "] Error creating message JSON", e);
            if (listener != null) {
                mainHandler.post(() -> listener.onError("Failed to send message: JSON error"));
            }
        } catch (Exception e) {
            Log.e(TAG, "[" + instanceId + "] Error sending message", e);
            if (listener != null) {
                mainHandler.post(() -> listener.onError("Failed to send message: " + e.getMessage()));
            }
        }
    }

    // === CONNECTION MANAGEMENT ===

    private void reconnectAfterError(String reason) {
        Log.d(TAG, "[" + instanceId + "] Reconnecting after error: " + reason);

        if (isClosingPermanently || manualClose) {
            Log.d(TAG, "[" + instanceId + "] Not reconnecting due to permanent close or manual close");
            return;
        }

        // FIXED: Don't try to reconnect if already connecting
        if (isConnecting.get()) {
            Log.d(TAG, "[" + instanceId + "] Already connecting, skipping reconnect");
            return;
        }

        // Close current connection if still open
        if (isOpen()) {
            try {
                super.close();
            } catch (Exception e) {
                Log.e(TAG, "[" + instanceId + "] Error closing connection for reconnect", e);
            }
        }

        attemptReconnect();
    }

    private void attemptReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS || isClosingPermanently) {
            Log.d(TAG, "[" + instanceId + "] Max reconnect attempts reached or permanently closing");
            if (listener != null) {
                mainHandler.post(() -> listener.onConnectionStateChange(false, "Maximum reconnection attempts reached"));
            }
            return;
        }

        reconnectAttempts++;
        long delay = Math.min(30000, 2000L * (long) Math.pow(2, reconnectAttempts - 1)); // Exponential backoff, max 30s

        Log.d(TAG, "[" + instanceId + "] Scheduling reconnect attempt #" + reconnectAttempts + " in " + (delay / 1000) + " seconds");

        mainHandler.postDelayed(() -> {
            if (!isClosingPermanently && !manualClose && !isConnecting.get()) {
                Log.d(TAG, "[" + instanceId + "] Attempting reconnect #" + reconnectAttempts);
                try {
                    reconnect();
                } catch (Exception e) {
                    Log.e(TAG, "[" + instanceId + "] Error during reconnect", e);
                    if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                        attemptReconnect();
                    }
                }
            }
        }, delay);
    }

    private boolean shouldReconnect(int closeCode) {
        // Don't reconnect on normal closure or authentication failures
        return closeCode != 1000 && closeCode != 1001 && closeCode != 1002 &&
                closeCode != 4003 && closeCode != 4004; // Custom error codes
    }

    // === UTILITY METHODS ===

    private boolean isDuplicateMessage(String messageId, String content) {
        // Check by message ID first
        if (messageId != null && !messageId.isEmpty()) {
            if (recentMessageIds.contains(messageId)) {
                return true;
            }
            recentMessageIds.add(messageId);

            // Keep cache size manageable
            if (recentMessageIds.size() > 100) {
                recentMessageIds.clear();
            }
            return false;
        }

        // Fall back to content-based deduplication
        String contentKey = content.trim().toLowerCase();
        if (recentMessageContent.contains(contentKey)) {
            return true;
        }

        recentMessageContent.add(contentKey);
        if (recentMessageContent.size() > 50) {
            recentMessageContent.clear();
        }

        return false;
    }

    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }

    // === PUBLIC CONTROL METHODS ===

    @Override
    public void close() {
        Log.d(TAG, "[" + instanceId + "] Manually closing WebSocket connection");
        manualClose = true;
        isClosingPermanently = false;
        isConnecting.set(false);
        stopPingPong();
        super.close();
    }

    public void closePermanently() {
        Log.d(TAG, "[" + instanceId + "] Permanently closing WebSocket connection");
        isClosingPermanently = true;
        manualClose = true;
        isConnecting.set(false);
        hasConnected.set(false);
        stopPingPong();
        super.close();
    }

    public boolean isConnected() {
        return isOpen() && !isClosingPermanently && hasConnected.get();
    }

    public void addHeader(String key, String value) {
        // Headers are added via the map in connect() method
        // This is a placeholder for the interface
    }
}