package com.silentguard.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.util.ArrayList;

public class TriggerSettingsActivity extends AppCompatActivity {

    private MaterialSwitch switchVoice, switchVolume, switchPower, switchScreen, switchPass;
    private SharedPreferences prefs;
    private SpeechRecognizer speechRecognizer;
    private static final int PERMISSION_RECORD_AUDIO = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trigger_settings);

        prefs = getSharedPreferences("SilentGuardPrefs", Context.MODE_PRIVATE);

        initViews();
        loadSavedStates();
        setupListeners();
        initSpeechRecognizer();
    }

    private void initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    Toast.makeText(TriggerSettingsActivity.this, "Listening...", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onBeginningOfSpeech() {}

                @Override
                public void onRmsChanged(float rmsdB) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {}

                @Override
                public void onError(int error) {
                    Toast.makeText(TriggerSettingsActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null) {
                        for (String phrase : matches) {
                            if (phrase.toLowerCase().contains("help me")) {
                                startActivity(new Intent(TriggerSettingsActivity.this, ShareLocationActivity.class));
                                Toast.makeText(TriggerSettingsActivity.this, "Voice Trigger: SOS Sent!", Toast.LENGTH_LONG).show();
                                return;
                            }
                        }
                        Toast.makeText(TriggerSettingsActivity.this, "No match found", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {}

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });
        }
    }

    private void startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_RECORD_AUDIO);
        } else {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speechRecognizer.startListening(intent);
        }
    }

    private void checkPermissionsAndStartService() {
        String[] permissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS
        };

        boolean allGranted = true;
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            Intent serviceIntent = new Intent(this, SilentGuardService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } else {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_RECORD_AUDIO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_RECORD_AUDIO) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted && switchVoice.isChecked()) {
                checkPermissionsAndStartService();
            } else {
                Toast.makeText(this, "Permissions required for background listening", Toast.LENGTH_SHORT).show();
                switchVoice.setChecked(false);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }

    private void initViews() {
        switchVoice = findViewById(R.id.switch_voice);
        switchVolume = findViewById(R.id.switch_volume);
        switchPower = findViewById(R.id.switch_power);
        switchScreen = findViewById(R.id.switch_screen);
        switchPass = findViewById(R.id.switch_pass);
    }

    private void loadSavedStates() {
        switchVoice.setChecked(prefs.getBoolean("switch_voice", true));
        switchVolume.setChecked(prefs.getBoolean("switch_volume", false));
        switchPower.setChecked(prefs.getBoolean("switch_power", true));
        switchScreen.setChecked(prefs.getBoolean("switch_screen", true));
        switchPass.setChecked(prefs.getBoolean("switch_pass", false));
    }

    private void setupListeners() {
        // Back Button
        View backButton = findViewById(R.id.btn_back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        // Test Voice Command Button
        Button testButton = findViewById(R.id.test_button);
        if (testButton != null) {
            testButton.setOnClickListener(v -> {
                if (switchVoice.isChecked()) {
                    startListening();
                } else {
                    Toast.makeText(this, "Please enable Voice Command first", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Switch Listeners with auto-save and Service control
        switchVoice.setOnCheckedChangeListener((v, isChecked) -> {
            save("switch_voice", isChecked);
            if (isChecked) {
                checkPermissionsAndStartService();
            } else {
                stopService(new Intent(this, SilentGuardService.class));
            }
        });
        switchVolume.setOnCheckedChangeListener((v, isChecked) -> save("switch_volume", isChecked));
        switchPower.setOnCheckedChangeListener((v, isChecked) -> save("switch_power", isChecked));
        switchScreen.setOnCheckedChangeListener((v, isChecked) -> save("switch_screen", isChecked));
        switchPass.setOnCheckedChangeListener((v, isChecked) -> save("switch_pass", isChecked));
    }

    private void save(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
        String status = value ? "Enabled" : "Disabled";
        Toast.makeText(this, "Feature " + status, Toast.LENGTH_SHORT).show();
    }
}
