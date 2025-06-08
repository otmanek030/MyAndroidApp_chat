package com.plcoding.audiorecorder;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.plcoding.audiorecorder.api.RetrofitClient;

public class ServerConfigActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_config);

        EditText serverUrlEditText = findViewById(R.id.server_url_edit_text);
        Button saveButton = findViewById(R.id.save_button);

        // Load current URL
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String currentUrl = prefs.getString("server_url","http://192.168.1.130:8000");
        serverUrlEditText.setText(currentUrl);

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = serverUrlEditText.getText().toString();

                // Save to preferences
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("server_url", url);
                editor.apply();

                // Update retrofit client
                RetrofitClient.getInstance(ServerConfigActivity.this).setServerUrl(url);

                Toast.makeText(ServerConfigActivity.this, "Server URL updated", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
}