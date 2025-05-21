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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatWebSocketClient extends WebSocketClient {
    private static final String TAG = "ChatWebSocketClient";
    private static final int RECONNECT_DELAY = 5000; // 5 seconds

    // Ping/pong timings - optimized
    private final Handler pingHandler = new Handler(Looper.getMainLooper());
    private boolean waitingForPong = false;
    private long lastPongTime = 0;
    private final AtomicBoolean isPinging = new AtomicBoolean(false);

    // Add these constants for improved reliability
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final int CONNECTION_TIMEOUT = 15000; // 15 seconds
    private static final long PING_INTERVAL = 15000; // 15 seconds
    private static final long PING_TIMEOUT = 5000; // 5 seconds

    private final Set<String> recentMessageIds = new HashSet<>();



    // Add this to track connection state better
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private volatile boolean manualClose = false;

    private final Runnable pingRunnable = new Runnable() {
        @Override
        public void run() {
            if (isOpen() && !waitingForPong && !isClosing) {
                try {
                    JSONObject pingMessage = new JSONObject();
                    pingMessage.put("message", "ping");
                    pingMessage.put("ping", true);
                    pingMessage.put("sender_type", "device");
                    pingMessage.put("device_id", deviceId);
                    send(pingMessage.toString());

                    Log.d(TAG, "Ping sent to server");
                    waitingForPong = true;

                    // Set timeout for pong response
                    pingHandler.postDelayed(() -> {
                        if (waitingForPong && !isClosing) {
                            Log.w(TAG, "Pong timeout - closing connection");
                            reconnectAfterError("Ping timeout");
                        }
                    }, PING_TIMEOUT);

                } catch (Exception e) {
                    Log.e(TAG, "Error sending ping", e);
                }
            }

            // Continue ping cycle only if connection is still alive
            if (isOpen() && !isClosing && !isClosingPermanently) {
                pingHandler.postDelayed(this, PING_INTERVAL);
            }
        }
    };

    private boolean isDuplicateMessage(JSONObject jsonMessage) {
        try {
            String messageId = jsonMessage.optString("message_id", null);
            if (messageId != null && recentMessageIds.contains(messageId)) {
                return true;
            }

            // Keep a small cache of recent message IDs
            if (messageId != null) {
                recentMessageIds.add(messageId);
                if (recentMessageIds.size() > 100) {
                    recentMessageIds.remove(0);  // Keep the set from growing too large
                }
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public interface MessageListener {
        void onMessageReceived(String sender, String message, String timestamp,
                               String messageId, boolean isHistorical);
        void onTypingStarted();
        void onConnectionStateChange(boolean connected, String message);
        void onError(String errorMessage);
        String getWebSocketUrl();
    }

    private final MessageListener listener;
    private boolean reconnectOnClose = true;
    private int reconnectAttempts = 0;
    private boolean isClosing = false;
    private final String deviceId;
    private final String recordingId;
    private boolean isTestConnection = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<String> queuedMessages = new ArrayList<>();
    private final Map<String, String> additionalHeaders = new HashMap<>();
    private boolean isClosingPermanently = false;

    public ChatWebSocketClient(String serverUri, MessageListener listener,
                               String deviceId, String recordingId) throws URISyntaxException {
        super(new URI(serverUri));

        this.listener = listener;
        this.deviceId = deviceId;
        this.recordingId = recordingId;
        this.isTestConnection = false;

        // Configure connection parameters
        this.setConnectionLostTimeout(30); // in seconds

        Log.d(TAG, "Created WebSocket client for device: " + deviceId +
                ", recording: " + recordingId + ", URI: " + serverUri);

        enableVerboseLogging();
    }

    private void enableVerboseLogging() {
        try {
            java.util.logging.Logger logger = java.util.logging.Logger.getLogger(WebSocketClient.class.getName());
            logger.setLevel(java.util.logging.Level.ALL);

            java.util.logging.ConsoleHandler handler = new java.util.logging.ConsoleHandler();
            handler.setLevel(java.util.logging.Level.ALL);
            logger.addHandler(handler);

            Log.d(TAG, "Enabled verbose WebSocket logging");
        } catch (Exception e) {
            Log.e(TAG, "Error enabling verbose logging", e);
        }
    }

    @Override
    public void connect() {
        Log.d(TAG, "Starting connection to: " + uri.toString());

        try {
            // Create a clean URL with device_id in query params - this is key for reliable connections
            String uriStr = uri.toString();
            String updatedUriStr;

            if (!uriStr.contains("?")) {
                updatedUriStr = uriStr + "?device_id=" + deviceId;
            } else if (!uriStr.contains("device_id=")) {
                updatedUriStr = uriStr + "&device_id=" + deviceId;
            } else {
                updatedUriStr = uriStr; // Already has device_id parameter
            }

            // Update the URI with the device_id parameter
            uri = new URI(updatedUriStr);
            Log.d(TAG, "Updated URI with device_id: " + uri.toString());

            // Add standard headers
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "AndroidVoiceRecorder");
            headers.put("X-Client-Type", "android_mobile");
            headers.put("X-Device-ID", deviceId);
            headers.put("x-device-id", deviceId); // Lowercase version for compatibility

            // Add recording ID if available
            if (recordingId != null) {
                headers.put("X-Recording-ID", recordingId);
            }

            // Add all headers
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                addHeader(entry.getKey(), entry.getValue());
            }

            // Log all headers being sent for debugging
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                Log.d(TAG, "Header: " + entry.getKey() + " = " + entry.getValue());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up connection: " + e.getMessage(), e);
            if (listener != null) {
                mainHandler.post(() -> listener.onError("Connection setup error: " + e.getMessage()));
            }
        }

        // Call parent connect method to actually make the connection
        isClosing = false;
        super.connect();

        // Set connection timeout
        mainHandler.postDelayed(() -> {
            if (!isOpen() && !isClosing) {
                Log.e(TAG, "Connection timed out");
                if (listener != null) {
                    listener.onConnectionStateChange(false, "Connection timed out. Server might be unreachable.");
                }
                reconnectAfterError("Connection timeout");
            }
        }, CONNECTION_TIMEOUT);
    }

    protected Map<String, String> getCustomHeaders() {
        Map<String, String> headers = new HashMap<>();

        // Add standard headers
        headers.put("User-Agent", "AndroidVoiceRecorder");
        headers.put("X-Client-Type", "android_mobile");

        // Add device ID headers
        if (deviceId != null) {
            headers.put("X-Device-ID", deviceId);
            headers.put("x-device-id", deviceId); // Also lowercase version
        }

        // Add recording ID header
        if (recordingId != null) {
            headers.put("X-Recording-ID", recordingId);
        }

        // Add headers from additionalHeaders
        headers.putAll(additionalHeaders);

        return headers;
    }

    public void addHeader(String key, String value) {
        additionalHeaders.put(key, value);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        Log.d(TAG, "WebSocket connection opened to: " + uri.toString());

        // Additional detailed logging
        Log.d(TAG, "Connection details:");
        Log.d(TAG, "  - isTestConnection: " + isTestConnection);
        Log.d(TAG, "  - deviceId: " + deviceId);
        Log.d(TAG, "  - recordingId: " + recordingId);

        reconnectAttempts = 0;
        isClosing = false;
        lastPongTime = System.currentTimeMillis();

        // Start sending periodic pings to keep connection alive
        startPingPong();

        if (listener != null) {
            mainHandler.post(() -> listener.onConnectionStateChange(true, "Connected"));
        }

        // Send any queued messages
        if (!queuedMessages.isEmpty()) {
            Log.d(TAG, "Sending " + queuedMessages.size() + " queued messages");
            mainHandler.post(() -> {
                List<String> messagesToSend = new ArrayList<>(queuedMessages);
                queuedMessages.clear();

                for (String message : messagesToSend) {
                    sendMessage(message, deviceId);
                }
            });
        }
    }

    private void startPingPong() {
        // Stop any existing ping task
        pingHandler.removeCallbacks(pingRunnable);
        waitingForPong = false;

        // Start new ping cycle after a short delay to allow connection to stabilize
        if (isPinging.compareAndSet(false, true)) {
            pingHandler.postDelayed(pingRunnable, 2000);
            Log.d(TAG, "Started ping/pong mechanism");
        }
    }

    private void stopPingPong() {
        pingHandler.removeCallbacks(pingRunnable);
        waitingForPong = false;
        isPinging.set(false);
        Log.d(TAG, "Stopped ping/pong mechanism");
    }

    @Override
    public void onMessage(String message) {
        Log.d(TAG, "Raw message received: " + message);

        try {
            JSONObject jsonMessage = new JSONObject(message);

            // Handle ping/pong messages
            if (jsonMessage.has("ping") && jsonMessage.getBoolean("ping")) {
                // Respond to ping with pong immediately
                if (isOpen()) {
                    JSONObject pong = new JSONObject();
                    pong.put("message", "pong");
                    pong.put("pong", true);
                    pong.put("device_id", deviceId);
                    send(pong.toString());
                    Log.d(TAG, "Sent pong response to server ping");
                }
                return;
            }

            if (jsonMessage.has("pong") && jsonMessage.getBoolean("pong")) {
                // Reset waitingForPong flag
                waitingForPong = false;
                Log.d(TAG, "Received pong response from server");
                return;
            }

            // Check for duplicate messages
            if (isDuplicateMessage(jsonMessage)) {
                Log.d(TAG, "Skipping duplicate WebSocket message");
                return;
            }

            // Process normal messages
            String messageContent = jsonMessage.optString("message", "");
            String sender = jsonMessage.optString("sender_type", "system");
            String timestamp = jsonMessage.optString("timestamp", "");
            String messageId = jsonMessage.optString("message_id", null);
            boolean isHistorical = jsonMessage.optBoolean("is_historical", false);

            // Skip empty messages
            if (messageContent.trim().isEmpty()) {
                Log.d(TAG, "Skipping empty message from " + sender);
                return;
            }

            // Skip ping/pong messages
            if (messageContent.equalsIgnoreCase("ping") || messageContent.equalsIgnoreCase("pong")) {
                Log.d(TAG, "Skipping ping/pong message");
                return;
            }

            Log.d(TAG, "Parsed message: sender=" + sender + ", content=" + messageContent);

            // Only notify if we have valid content and a listener
            if (listener != null) {
                listener.onMessageReceived(sender, messageContent, timestamp, messageId, isHistorical);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing message: " + e.getMessage(), e);
            // Try to treat as plain text if JSON parsing fails
            if (listener != null && message != null && !message.isEmpty()) {
                listener.onMessageReceived("system", message, getCurrentTimestamp(), null, false);
            }
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        Log.d(TAG, "WebSocket closed: code=" + code + ", reason=" + reason + ", remote=" + remote);

        // Cancel ping handler
        stopPingPong();

        if (listener != null) {
            mainHandler.post(() -> {
                listener.onConnectionStateChange(false, "Disconnected: " + reason + " (code " + code + ")");
            });
        }

        // Attempt to reconnect for abnormal closures or network errors
        if ((code == 1006 || code == 1001) && !isClosing && !isClosingPermanently && reconnectOnClose) {
            Log.d(TAG, "Abnormal closure, attempting reconnect");
            attemptReconnect();
        }
    }

    @Override
    public void onError(Exception ex) {
        Log.e(TAG, "WebSocket error: " + ex.getMessage(), ex);

        if (listener != null) {
            final String errorMsg = ex.getMessage();
            mainHandler.post(() -> {
                listener.onConnectionStateChange(false, "Error: " + errorMsg);
                listener.onError("Connection error: " + errorMsg);
            });
        }

        // Attempt reconnect for certain errors
        if (!isClosing && !isClosingPermanently && reconnectOnClose) {
            reconnectAfterError(ex.getMessage());
        }
    }

    private void reconnectAfterError(String reason) {
        Log.d(TAG, "Reconnect after error: " + reason);

        // If it's a manual close, don't reconnect
        if (isClosingPermanently || manualClose) {
            return;
        }

        // Close current connection if it's still open
        if (isOpen()) {
            try {
                isClosing = true;
                close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing existing connection", e);
            }
        }

        // Attempt reconnect if under max attempts
        attemptReconnect();
    }

    private void attemptReconnect() {
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS && !isClosingPermanently) {
            reconnectAttempts++;
            long delay = RECONNECT_DELAY * (long) Math.pow(2, reconnectAttempts - 1);

            Log.d(TAG, "Scheduling reconnect attempt #" + reconnectAttempts + " in " + delay + "ms");

            mainHandler.postDelayed(() -> {
                if (!isClosingPermanently) {
                    Log.d(TAG, "Attempting reconnect #" + reconnectAttempts);
                    reconnect();
                }
            }, delay);
        } else {
            Log.d(TAG, "Maximum reconnect attempts reached or closing permanently");
        }
    }

    /**
     * Send a message to the server
     */
    public void sendMessage(String message, String deviceId) {
        if (message == null || message.trim().isEmpty()) {
            Log.d(TAG, "Skipping empty message");
            return;
        }

        if (!isOpen()) {
            Log.e(TAG, "WebSocket not connected, cannot send message");
            if (listener != null) {
                mainHandler.post(() -> {
                    listener.onError("Cannot send message: not connected to server");
                    // Queue message for later delivery
                    queueMessageForLaterDelivery(message);
                });
            }
            return;
        }

        try {
            JSONObject jsonMessage = new JSONObject();
            jsonMessage.put("message", message);
            jsonMessage.put("sender_type", "device");
            jsonMessage.put("device_id", deviceId);

            send(jsonMessage.toString());
            Log.d(TAG, "Message sent: " + message);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating message JSON: " + e.getMessage());
            if (listener != null) {
                final String errorMsg = "Failed to send message: " + e.getMessage();
                mainHandler.post(() -> listener.onError(errorMsg));
            }
        }
    }

    private void queueMessageForLaterDelivery(String message) {
        if (queuedMessages.size() < 50) { // Limit queue size
            queuedMessages.add(message);
            Log.d(TAG, "Message queued for later delivery: " + message);
        }
    }

    /**
     * Properly close the WebSocket connection
     */
    @Override
    public void close() {
        isClosing = true;
        manualClose = true;
        stopPingPong();
        super.close();
    }

    public void closePermanently() {
        isClosingPermanently = true;
        manualClose = true;
        close();
    }

    @Override
    public void send(String text) {
        if (isOpen()) {
            super.send(text);
        } else {
            Log.e(TAG, "Attempted to send message while connection is closed");
            if (listener != null) {
                mainHandler.post(() -> listener.onError("Cannot send message: connection is closed"));
            }
        }
    }

    /**
     * Enable or disable automatic reconnection
     */
    public void setReconnectOnClose(boolean reconnect) {
        this.reconnectOnClose = reconnect;
    }

    /**
     * Get current timestamp as string
     */
    private String getCurrentTimestamp() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date());
    }

    /**
     * Check if the WebSocket is connected
     */
    public boolean isConnected() {
        return isOpen();
    }

    /**
     * Helper method to get the URI
     */
    public URI getURI() {
        return this.uri;
    }
}