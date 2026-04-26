package com.abhilash.anpr;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "ANPRPrefs";
    private static final String PREF_SERVER_URL = "server_url";
    private static final String DEFAULT_URL = "http://192.168.1.1:5000";

    private OkHttpClient httpClient;
    private SharedPreferences prefs;

    // Views
    private View serverDot;
    private TextInputEditText serverUrlInput;
    private MaterialButton pingBtn;
    private TextView serverLabel;
    private TextView quotaChip;

    private View resultSection;
    private ImageView previewImage;
    private View loadingState;
    private View foundState;
    private View errorState;

    private TextView statusBadge;
    private TextView plateText;
    private View plateConfBar;
    private TextView plateConfVal;
    private View regionConfBar;
    private TextView regionConfVal;
    private TextView metaFile;
    private TextView metaRegion;
    private TextView metaVehicle;
    private MaterialButton tryAnotherBtn;
    private MaterialButton copyPlateBtn;
    private TextView errorText;
    private MaterialButton retryBtn;

    private ImageView githubBtn;
    private ImageView linkedinBtn;

    private Uri cameraImageUri;
    private String currentPlate = "";
    private String currentFileName = "";

    // Activity result launchers
    private final ActivityResultLauncher<Intent> galleryLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri imageUri = result.getData().getData();
                if (imageUri != null) {
                    processImage(imageUri, "gallery_image.jpg");
                }
            }
        });

    private final ActivityResultLauncher<Intent> cameraLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && cameraImageUri != null) {
                processImage(cameraImageUri, "camera_image.jpg");
            }
        });

    private final ActivityResultLauncher<String[]> permissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
            // After permissions granted, user can try again
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

        bindViews();
        setupListeners();
        loadSavedUrl();

        // Auto-ping on start
        pingServer();
    }

    private void bindViews() {
        serverDot = findViewById(R.id.serverDot);
        serverUrlInput = findViewById(R.id.serverUrlInput);
        pingBtn = findViewById(R.id.pingBtn);
        serverLabel = findViewById(R.id.serverLabel);
        quotaChip = findViewById(R.id.quotaChip);

        resultSection = findViewById(R.id.resultSection);
        previewImage = findViewById(R.id.previewImage);
        loadingState = findViewById(R.id.loadingState);
        foundState = findViewById(R.id.foundState);
        errorState = findViewById(R.id.errorState);

        statusBadge = findViewById(R.id.statusBadge);
        plateText = findViewById(R.id.plateText);
        plateConfBar = findViewById(R.id.plateConfBar);
        plateConfVal = findViewById(R.id.plateConfVal);
        regionConfBar = findViewById(R.id.regionConfBar);
        regionConfVal = findViewById(R.id.regionConfVal);
        metaFile = findViewById(R.id.metaFile);
        metaRegion = findViewById(R.id.metaRegion);
        metaVehicle = findViewById(R.id.metaVehicle);
        tryAnotherBtn = findViewById(R.id.tryAnotherBtn);
        copyPlateBtn = findViewById(R.id.copyPlateBtn);
        errorText = findViewById(R.id.errorText);
        retryBtn = findViewById(R.id.retryBtn);

        githubBtn = findViewById(R.id.githubBtn);
        linkedinBtn = findViewById(R.id.linkedinBtn);
    }

    private void setupListeners() {
        pingBtn.setOnClickListener(v -> pingServer());

        MaterialButton galleryBtn = findViewById(R.id.galleryBtn);
        MaterialButton cameraBtn = findViewById(R.id.cameraBtn);

        galleryBtn.setOnClickListener(v -> openGallery());
        cameraBtn.setOnClickListener(v -> openCamera());

        // Upload card tap also opens gallery
        findViewById(R.id.uploadCard).setOnClickListener(v -> openGallery());

        tryAnotherBtn.setOnClickListener(v -> {
            resultSection.setVisibility(View.GONE);
            openGallery();
        });

        copyPlateBtn.setOnClickListener(v -> {
            if (!currentPlate.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("plate", currentPlate);
                clipboard.setPrimaryClip(clip);
                copyPlateBtn.setText("Copied! ✓");
                copyPlateBtn.postDelayed(() -> copyPlateBtn.setText("Copy plate"), 2000);
            }
        });

        retryBtn.setOnClickListener(v -> pingServer());

        githubBtn.setOnClickListener(v -> {
            openUrl("https://github.com/frostbreath123");
        });

        linkedinBtn.setOnClickListener(v -> {
            openUrl("https://www.linkedin.com/in/abhilash-lakshmikanth-83b763255/");
        });

        // Save URL when changed
        serverUrlInput.setOnEditorActionListener((v, actionId, event) -> {
            saveUrl();
            pingServer();
            return false;
        });
    }

    private void loadSavedUrl() {
        String saved = prefs.getString(PREF_SERVER_URL, DEFAULT_URL);
        serverUrlInput.setText(saved);
    }

    private void saveUrl() {
        String url = getServerUrl();
        prefs.edit().putString(PREF_SERVER_URL, url).apply();
    }

    private String getServerUrl() {
        String url = "";
        if (serverUrlInput.getText() != null) {
            url = serverUrlInput.getText().toString().trim();
        }
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url.isEmpty() ? DEFAULT_URL : url;
    }

    private void pingServer() {
        saveUrl();
        runOnUiThread(() -> {
            serverDot.setBackgroundResource(R.drawable.dot_idle);
            serverLabel.setText("pinging…");
            quotaChip.setVisibility(View.GONE);
        });

        String url = getServerUrl() + "/health";
        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    serverDot.setBackgroundResource(R.drawable.dot_offline);
                    serverLabel.setText("offline — run: python server.py");
                    serverLabel.setTextColor(getColor(R.color.muted));
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    boolean tokenSet = json.optBoolean("token_set", false);
                    int callsRemaining = json.optInt("calls_remaining", -1);

                    runOnUiThread(() -> {
                        if (!tokenSet) {
                            serverDot.setBackgroundResource(R.drawable.dot_offline);
                            serverLabel.setText("online · ⚠ no token in .env");
                            serverLabel.setTextColor(getColor(R.color.yellow));
                        } else {
                            serverDot.setBackgroundResource(R.drawable.dot_online);
                            serverLabel.setText("online · token ✓");
                            serverLabel.setTextColor(getColor(R.color.muted));
                            if (callsRemaining >= 0) {
                                quotaChip.setVisibility(View.VISIBLE);
                                quotaChip.setText(callsRemaining + " lookups left");
                            }
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        serverDot.setBackgroundResource(R.drawable.dot_offline);
                        serverLabel.setText("offline — run: python server.py");
                        serverLabel.setTextColor(getColor(R.color.muted));
                    });
                }
            }
        });
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(new String[]{Manifest.permission.CAMERA});
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File photoFile = createImageFile();
            cameraImageUri = FileProvider.getUriForFile(this,
                getApplicationContext().getPackageName() + ".fileprovider",
                photoFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            cameraLauncher.launch(intent);
        } catch (IOException e) {
            Toast.makeText(this, "Could not create image file", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile("ANPR_", ".jpg", dir);
    }

    private void processImage(Uri imageUri, String fileName) {
        // Show result section with loading
        resultSection.setVisibility(View.VISIBLE);
        showLoading();

        // Set preview image
        previewImage.setImageURI(imageUri);

        // Determine filename
        currentFileName = fileName;

        // Convert URI to bytes and upload
        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                if (inputStream == null) throw new IOException("Cannot open image");

                byte[] bytes = inputStream.readAllBytes();
                inputStream.close();

                // Determine MIME type
                String mime = getContentResolver().getType(imageUri);
                if (mime == null) mime = "image/jpeg";

                RequestBody fileBody = RequestBody.create(bytes, MediaType.parse(mime));
                RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("image", "vehicle.jpg", fileBody)
                    .build();

                Request request = new Request.Builder()
                    .url(getServerUrl() + "/api/detect")
                    .post(requestBody)
                    .build();

                httpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runOnUiThread(() -> showError("Connection failed: " + e.getMessage()));
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        try {
                            String body = response.body().string();
                            JSONObject data = new JSONObject(body);
                            if (!response.isSuccessful()) {
                                String err = data.optString("error", response.message());
                                runOnUiThread(() -> showError(err));
                                return;
                            }
                            runOnUiThread(() -> renderResult(data, currentFileName));
                            pingServer();
                        } catch (Exception e) {
                            runOnUiThread(() -> showError("Parse error: " + e.getMessage()));
                        }
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> showError("Image error: " + e.getMessage()));
            }
        }).start();

        // Scroll to result
        findViewById(R.id.scrollView).post(() ->
            ((androidx.core.widget.NestedScrollView) findViewById(R.id.scrollView))
                .smoothScrollTo(0, resultSection.getTop()));
    }

    private void showLoading() {
        loadingState.setVisibility(View.VISIBLE);
        foundState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
    }

    private void showError(String message) {
        loadingState.setVisibility(View.GONE);
        foundState.setVisibility(View.GONE);
        errorState.setVisibility(View.VISIBLE);
        errorText.setText("Error — " + message);
    }

    private void renderResult(JSONObject data, String filename) {
        loadingState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
        foundState.setVisibility(View.VISIBLE);

        boolean detected = data.optBoolean("detected", false);
        String plate = data.optString("plate_text", "");
        double conf = data.optDouble("confidence", 0);
        double regionConf = data.optDouble("region_score", 0);
        String region = data.optString("region", "auto-detected");
        String vehicleType = data.optString("vehicle_type", "");

        currentPlate = plate;

        if (detected && !plate.isEmpty()) {
            statusBadge.setText("PLATE FOUND");
            statusBadge.setBackgroundResource(R.drawable.badge_green);
            statusBadge.setTextColor(getColor(R.color.accent));
            plateText.setVisibility(View.VISIBLE);
            plateText.setText(plate);
            copyPlateBtn.setVisibility(View.VISIBLE);
            copyPlateBtn.setText("Copy plate");
        } else {
            statusBadge.setText("NO PLATE DETECTED");
            statusBadge.setBackgroundResource(R.drawable.badge_red);
            statusBadge.setTextColor(getColor(R.color.red));
            plateText.setVisibility(View.GONE);
            copyPlateBtn.setVisibility(View.GONE);
        }

        // Confidence bars
        setConfBar(plateConfBar, plateConfVal, (int) conf);
        setConfBar(regionConfBar, regionConfVal, (int) regionConf);

        // Meta
        metaFile.setText("File: " + filename);
        metaRegion.setText("Region / Country: " + region);
        if (!vehicleType.isEmpty()) {
            metaVehicle.setVisibility(View.VISIBLE);
            metaVehicle.setText("Vehicle type: " + vehicleType);
        } else {
            metaVehicle.setVisibility(View.GONE);
        }
    }

    private void setConfBar(View bar, TextView val, int pct) {
        // Set bar width as percentage of parent
        ViewGroup.LayoutParams params = bar.getLayoutParams();
        // We'll use a post to get the parent width
        bar.post(() -> {
            int parentWidth = ((View) bar.getParent()).getWidth();
            params.width = (int) (parentWidth * pct / 100.0);
            bar.setLayoutParams(params);
        });

        val.setText(pct + "%");

        // Color the bar
        if (pct >= 80) {
            bar.setBackgroundResource(R.drawable.bar_fill_green);
        } else if (pct >= 55) {
            bar.setBackgroundResource(R.drawable.bar_fill_yellow);
        } else {
            bar.setBackgroundResource(R.drawable.bar_fill_red);
        }
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }
}
