package com.abhilash.anpr;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "ANPRPrefs";
    private static final String PREF_SERVER_URL = "server_url";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        TextInputEditText urlInput = findViewById(R.id.settingsUrlInput);
        MaterialButton saveBtn = findViewById(R.id.saveSettingsBtn);

        String currentUrl = prefs.getString(PREF_SERVER_URL, "http://192.168.1.1:5000");
        urlInput.setText(currentUrl);

        saveBtn.setOnClickListener(v -> {
            String url = urlInput.getText() != null ? urlInput.getText().toString().trim() : "";
            if (url.isEmpty()) {
                Toast.makeText(this, "Please enter a server URL", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString(PREF_SERVER_URL, url).apply();
            Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
