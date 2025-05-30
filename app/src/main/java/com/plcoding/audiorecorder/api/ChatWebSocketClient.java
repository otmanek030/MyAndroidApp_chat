// ChatWebSocketClient.java - FIXED VERSION WITH TIMEZONE SUPPORT

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
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatWebSocketClient extends WebSocketClient {
    private static final String TAG = "ChatWebSocketClient";

    // Connection constants
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int CONNECTION_TIMEOUT = 15000;
    private static final long PING_INTERVAL = 30000;
    private static final long PING_TIMEOUT = 10000;

    // ✅ TIMEZONE SUPPORT
    private static final String SERVER_TIMEZONE = "Africa/Casablanca"; // Match your server
    private SimpleDateFormat timestampFormat;

    // Ping/pong management
    private final Handler pingHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean waitingForPong = new AtomicBoolean(false);
    private final AtomicBoolean isPinging = new AtomicBoolean(false);

    // Message deduplication
    private final Set<String> recentMessageIds = new HashSet<>();
    private final Set<String> recentMessageContent = new HashSet<>();

    // Connection state
    private volatile boolean isClosingPermanently = false;
    private volatile boolean manualClose = false;
    private int reconnectAttempts = 0;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Client info
    private final MessageListener listener;
    private final String deviceId;
    private final String recordingId;

    public interface MessageListener {
        void onMessageReceived(String sender, String message, String timestamp, String messageId, boolean isHistorical);
        void onTypingStarted();
        void onConnectionStateChange(boolean connected, String message);
        void onError(String errorMessage);
        String getWebSocketUrl();
    }

    public ChatWebSocketClient(String serverUri, MessageListener listener, String deviceId, String recordingId) throws URISyntaxException {
        super(new URI(serverUri));

        this.listener = listener;
        this.deviceId = deviceId;
        this.recordingId = recordingId;

        // ✅ INITIALIZE TIMESTAMP FORMATTER
        initializeTimestampFormatter();

        // Configure connection
        this.setConnectionLostTimeout(60);

        Log.d(TAG, "✅ Created WebSocket client for device: " + deviceId + ", recording: " + recordingId);
        Log.d(TAG, "   WebSocket URI: " + serverUri);
    }

    // ✅ INITIALIZE TIMESTAMP FORMATTER
    private void initializeTimestampFormatter() {
        timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        timestampFormat.setTimeZone(TimeZone.getTimeZone(SERVER_TIMEZONE));
        Log.d(TAG, "✅ Timestamp formatter initialized for timezone: " + SERVER_TIMEZONE);
    }

    @Override
    public void connect() {
        Log.d(TAG, "🔗 Starting connection to: " + uri.toString());

        try {
            // Add query parameter for device ID if not present
            String uriStr = uri.toString();
            if (!uriStr.contains("device_id=")) {
                String separator = uriStr.contains("?") ? "&" : "?";
                uriStr = uriStr + separator + "device_id=" + deviceId;
                uri = new URI(uriStr);
                Log.d(TAG, "   Updated URI with device_id: " + uri.toString());
            }

            // Add headers for identification
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "AndroidVoiceRecorder/1.0");
            headers.put("X-Client-Type", "android_mobile");
            headers.put("X-Device-ID", deviceId);
            headers.put("X-Timezone", TimeZone.getDefault().getID());

            if (recordingId != null) {
                headers.put("X-Recording-ID", recordingId);
            }

            // Add headers to connection
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                addHeader(entry.getKey(), entry.getValue());
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error setting up connection", e);
            if (listener != null) {
                mainHandler.post(() -> listener.onError("Connection setup error: " + e.getMessage()));
            }
            return;
        }

        // Reset state
        isClosingPermanently = false;
        manualClose = false;

        // Start connection
        super.connect();

        // Set connection timeout
        mainHandler.postDelayed(() -> {
            if (!isOpen() && !isClosingPermanently) {
                Log.e(TAG, "❌ Connection timeout");
                if (listener != null) {
                    listener.onConnectionStateChange(false, "Connection timeout");
                }
                reconnectAfterError("Connection timeout");
            }
        }, CONNECTION_TIMEOUT);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        Log.d(TAG, "✅ WebSocket connection opened successfully");
        Log.d(TAG, "   Server handshake: " + handshakedata.getHttpStatus() + " " + handshakedata.getHttpStatusMessage());

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
        Log.d(TAG, "📨 Received WebSocket message: " + message);

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

            // Handle regular messages
            String messageContent = jsonMessage.optString("message", "");
            String sender = jsonMessage.optString("sender_type", "system");
            String timestamp = jsonMessage.optString("timestamp", getCurrentTimestamp());
            String timestampIso = jsonMessage.optString("timestamp_iso", "");
            String messageId = jsonMessage.optString("message_id", null);
            boolean isHistorical = jsonMessage.optBoolean("is_historical", false);

            // Skip empty messages and ping/pong
            if (messageContent.trim().isEmpty() ||
                    messageContent.equalsIgnoreCase("ping") ||
                    messageContent.equalsIgnoreCase("pong")) {
                return;
            }

            // ✅ CRITICAL: Don't check for duplicates here - let the UI handle it
            Log.d(TAG, "📤 Processing message from " + sender + ": " + messageContent.substring(0, Math.min(50, messageContent.length())));

            if (listener != null) {
                mainHandler.post(() ->
                        listener.onMessageReceived(sender, messageContent, timestampIso.isEmpty() ? timestamp : timestampIso, messageId, isHistorical)
                );
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON message", e);
            // Try to handle as plain text
            if (!message.trim().isEmpty() && listener != null) {
                mainHandler.post(() ->
                        listener.onMessageReceived("system", message, getCurrentTimestamp(), null, false)
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling message", e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        Log.d(TAG, "🔌 WebSocket closed: code=" + code + ", reason=" + reason + ", remote=" + remote);

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
            Log.d(TAG, "🔄 Attempting reconnection after abnormal closure");
            attemptReconnect();
        }
    }

    @Override
    public void onError(Exception ex) {
        Log.e(TAG, "❌ WebSocket error: " + ex.getMessage(), ex);

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

    // ✅ FIXED: Send message method
    public void sendMessage(String message, String deviceId) {
        if (message == null || message.trim().isEmpty()) {
            Log.d(TAG, "Attempting to send empty message, skipping");
            return;
        }

        if (!isOpen()) {
            Log.e(TAG, "Cannot send message: WebSocket not connected");
            if (listener != null) {
                mainHandler.post(() -> listener.onError("Cannot send message: not connected"));
            }
            return;
        }

        try {
            JSONObject jsonMessage = new JSONObject();
            jsonMessage.put("message", message);
            jsonMessage.put("sender_type", "device");  // ✅ FIXED: Always "device" for mobile
            jsonMessage.put("device_id", deviceId);
            jsonMessage.put("timestamp", getCurrentTimestamp());

            String jsonString = jsonMessage.toString();
            send(jsonString);

            Log.d(TAG, "✅ Message sent via WebSocket: " + message);

        } catch (JSONException e) {
            Log.e(TAG, "Error creating message JSON", e);
            if (listener != null) {
                mainHandler.post(() -> listener.onError("Failed to send message: JSON error"));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending message", e);
            if (listener != null) {
                mainHandler.post(() -> listener.onError("Failed to send message: " + e.getMessage()));
            }
        }
    }


    // === PING/PONG MANAGEMENT ===
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

                    Log.d(TAG, "📡 Ping sent to server");

                    // Set timeout for pong response
                    pingHandler.postDelayed(() -> {
                        if (waitingForPong.get() && !isClosingPermanently) {
                            Log.w(TAG, "⚠️ Pong timeout - connection may be dead");
                            waitingForPong.set(false);
                            reconnectAfterError("Ping timeout");
                        }
                    }, PING_TIMEOUT);

                } catch (Exception e) {
                    Log.e(TAG, "❌ Error sending ping", e);
                }
            }

            // Schedule next ping
            if (isOpen() && !isClosingPermanently) {
                pingHandler.postDelayed(this, PING_INTERVAL);
            }
        }
    };

    private void startPingPong() {
        stopPingPong(); // Stop any existing ping task

        if (isPinging.compareAndSet(false, true)) {
            // Start ping cycle after initial delay
            pingHandler.postDelayed(pingRunnable, 5000); // 5 second initial delay
            Log.d(TAG, "✅ Started ping/pong mechanism");
        }
    }

    private void stopPingPong() {
        pingHandler.removeCallbacks(pingRunnable);
        waitingForPong.set(false);
        isPinging.set(false);
        Log.d(TAG, "⏹️ Stopped ping/pong mechanism");
    }

    private void handleServerPing() {
        Log.d(TAG, "📡 Received ping from server, sending pong");

        if (isOpen()) {
            try {
                JSONObject pong = new JSONObject();
                pong.put("message", "pong");
                pong.put("pong", true);
                pong.put("device_id", deviceId);
                pong.put("timestamp", getCurrentTimestamp());
                send(pong.toString());
            } catch (Exception e) {
                Log.e(TAG, "❌ Error sending pong response", e);
            }
        }
    }

    private void handleServerPong() {
        Log.d(TAG, "📡 Received pong from server");
        waitingForPong.set(false);
    }

    // === CONNECTION MANAGEMENT ===
    private void reconnectAfterError(String reason) {
        Log.d(TAG, "🔄 Reconnecting after error: " + reason);

        if (isClosingPermanently || manualClose) {
            Log.d(TAG, "⏹️ Not reconnecting due to permanent close or manual close");
            return;
        }

        // Close current connection if still open
        if (isOpen()) {
            try {
                super.close();
            } catch (Exception e) {
                Log.e(TAG, "❌ Error closing connection for reconnect", e);
            }
        }

        attemptReconnect();
    }

    private void attemptReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS || isClosingPermanently) {
            Log.d(TAG, "⏹️ Max reconnect attempts reached or permanently closing");
            if (listener != null) {
                mainHandler.post(() -> listener.onConnectionStateChange(false, "Maximum reconnection attempts reached"));
            }
            return;
        }

        reconnectAttempts++;
        long delay = Math.min(30000, 2000L * (long) Math.pow(2, reconnectAttempts - 1)); // Exponential backoff, max 30s

        Log.d(TAG, "🔄 Scheduling reconnect attempt #" + reconnectAttempts + " in " + (delay / 1000) + " seconds");

        mainHandler.postDelayed(() -> {
            if (!isClosingPermanently && !manualClose) {
                Log.d(TAG, "🔄 Attempting reconnect #" + reconnectAttempts);
                try {
                    reconnect();
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error during reconnect", e);
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

    // ✅ GET CURRENT TIMESTAMP IN SERVER TIMEZONE
    private String getCurrentTimestamp() {
        return timestampFormat.format(new Date());
    }

    // === PUBLIC CONTROL METHODS ===
    @Override
    public void close() {
        Log.d(TAG, "🔒 Manually closing WebSocket connection");
        manualClose = true;
        isClosingPermanently = false;
        stopPingPong();
        super.close();
    }

    public void closePermanently() {
        Log.d(TAG, "🔒 Permanently closing WebSocket connection");
        isClosingPermanently = true;
        manualClose = true;
        stopPingPong();
        super.close();
    }

    public boolean isConnected() {
        return isOpen() && !isClosingPermanently;
    }

    public void addHeader(String key, String value) {
        // Headers are added via the map in connect() method
        // This is a placeholder for the interface
    }
}