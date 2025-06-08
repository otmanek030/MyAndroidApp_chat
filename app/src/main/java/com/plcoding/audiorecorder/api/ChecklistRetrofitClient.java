package com.plcoding.audiorecorder.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ChecklistRetrofitClient {
    private static final String TAG = "ChecklistRetrofitClient";
    private static final String DEFAULT_BASE_URL = "http://192.168.1.130:8000/checklists/api/";
    private static final String PREF_SERVER_URL = "server_url";

    private static volatile ChecklistRetrofitClient instance;
    private final Context context;
    private Retrofit retrofit;

    private ChecklistRetrofitClient(Context context) {
        this.context = context.getApplicationContext();
        initRetrofit();
    }

    public static ChecklistRetrofitClient getInstance(Context context) {
        if (instance == null) {
            synchronized (ChecklistRetrofitClient.class) {
                if (instance == null) {
                    instance = new ChecklistRetrofitClient(context);
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
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();

        retrofit = new Retrofit.Builder()
                .baseUrl(getChecklistServerUrl())
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    private String getChecklistServerUrl() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String baseUrl = prefs.getString(PREF_SERVER_URL, "http://192.168.1.130:8000");

        // Remove /api/ if present and add checklists/api/
        baseUrl = baseUrl.replace("/api/", "").replace("/api", "");
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return baseUrl + "checklists/api/";
    }

    public TaskChecklistApiService getTaskChecklistApiService() {
        if (retrofit == null) {
            initRetrofit();
        }
        return retrofit.create(TaskChecklistApiService.class);
    }
}