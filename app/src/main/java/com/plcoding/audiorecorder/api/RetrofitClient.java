package com.plcoding.audiorecorder.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    private static final String TAG = "RetrofitClient";
    private static final String DEFAULT_BASE_URL = "http://192.168.1.130:8000/api/";
    private static final String PREF_SERVER_URL = "server_url";
    private static final int CONNECT_TIMEOUT = 15;
    private static final int READ_TIMEOUT = 15;
    private static final int WRITE_TIMEOUT = 15;
    private static final int PING_TIMEOUT = 5; // seconds for socket ping

    private static volatile RetrofitClient instance;
    private final Context context;
    private Retrofit retrofit;
    private Retrofit checklistRetrofit; // Add separate retrofit for checklist API
    private RecordingApiService apiService;
    private final AtomicBoolean isCheckingServer = new AtomicBoolean(false);

    private RetrofitClient(Context context) {
        this.context = context.getApplicationContext();
        initRetrofit();
    }

    public static RetrofitClient getInstance(Context context) {
        if (instance == null) {
            synchronized (RetrofitClient.class) {
                if (instance == null) {
                    instance = new RetrofitClient(context);
                }
            }
        }
        return instance;
    }

    private void initRetrofit() {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(message -> Log.d(TAG, message));
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();

        // Main API retrofit (for recordings)
        retrofit = new Retrofit.Builder()
                .baseUrl(getServerUrl())
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        // Checklist API retrofit
        checklistRetrofit = new Retrofit.Builder()
                .baseUrl(getChecklistServerUrl())
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(RecordingApiService.class);
    }

    // Get checklist-specific server URL
    private String getChecklistServerUrl() {
        String baseUrl = getServerUrl();
        // Remove /api/ from the end and add checklists/api/
        baseUrl = baseUrl.replace("/api/", "/");
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return baseUrl + "checklists/api/";
    }

    public TaskChecklistApiService getTaskChecklistApiService() {
        if (checklistRetrofit == null) {
            initRetrofit();
        }
        return checklistRetrofit.create(TaskChecklistApiService.class);
    }

    // Or if you prefer the createService pattern:
    public <T> T createService(Class<T> serviceClass) {
        if (serviceClass == TaskChecklistApiService.class) {
            if (checklistRetrofit == null) {
                initRetrofit();
            }
            return checklistRetrofit.create(serviceClass);
        } else {
            if (retrofit == null) {
                initRetrofit();
            }
            return retrofit.create(serviceClass);
        }
    }

    public RecordingApiService getApiService() {
        return apiService;
    }

    public boolean isServerReachable() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "Network operation attempted on main thread");
            return false;
        }

        if (isCheckingServer.getAndSet(true)) {
            Log.d(TAG, "Server check already in progress");
            return false;
        }

        try {
            // Skip HTTP check for now, just use socket ping
            Log.d(TAG, "Using socket ping only for reachability check");
            return pingServer();
        } finally {
            isCheckingServer.set(false);
        }
    }

    private boolean httpReachabilityCheck() {
        try {
            URL url = new URL(getServerUrl());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000); // Increased from 5000
            connection.setReadTimeout(10000);    // Increased from 5000
            connection.setRequestMethod("GET");  // Changed from HEAD to GET

            // Important: Follow redirects
            connection.setInstanceFollowRedirects(true);

            // Add headers that Django expects
            connection.setRequestProperty("User-Agent", "AudioRecorder-Android/1.0");
            connection.setRequestProperty("Accept", "application/json");

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            // Accept both 200 and other success codes
            boolean reachable = (responseCode >= 200 && responseCode < 400);
            Log.d(TAG, "HTTP reachability: " + reachable + " (code: " + responseCode + ")");
            return reachable;
        } catch (Exception e) {
            Log.e(TAG, "HTTP reachability check failed", e);
            return false;
        }
    }

    public boolean pingServer() {
        String host = getServerHostname();
        int port = getServerPort();

        Log.d(TAG, "Pinging " + host + ":" + port);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), PING_TIMEOUT * 1000);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Ping failed: " + e.getMessage());
            return false;
        }
    }

    public String getServerUrl() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String url = prefs.getString(PREF_SERVER_URL, DEFAULT_BASE_URL);

        // Ensure URL formatting
        if (!url.startsWith("http")) {
            url = "http://" + url;
        }
        if (!url.endsWith("/")) {
            url += "/";
        }
        if (!url.contains("/api/")) {
            url = url.replaceAll("/+$", "") + "/api/";
        }

        return url;
    }

    public String getWebSocketBaseUrl() {
        String httpUrl = getServerUrl();
        String wsUrl = httpUrl
                .replace("http://", "ws://")
                .replace("https://", "wss://")
                .replace("/api/", "/");

        // Remove trailing slash if present
        if (wsUrl.endsWith("/")) {
            wsUrl = wsUrl.substring(0, wsUrl.length() - 1);
        }

        return wsUrl;
    }

    public String getServerHostname() {
        try {
            return new URL(getServerUrl()).getHost();
        } catch (Exception e) {
            // Fallback parsing
            String url = getServerUrl();
            url = url.replace("http://", "").replace("https://", "");
            return url.split("[:/]")[0];
        }
    }

    public int getServerPort() {
        try {
            int port = new URL(getServerUrl()).getPort();
            return port != -1 ? port : (getServerUrl().startsWith("https") ? 443 : 80);
        } catch (Exception e) {
            return 8000; // Default port for your application
        }
    }

    public void setServerUrl(String newUrl) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(PREF_SERVER_URL, newUrl).apply();
        initRetrofit(); // Reinitialize with new URL
    }
}