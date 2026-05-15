package com.silentguard.app;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class TriggerSettingsActivity extends BaseSettingsActivity {

    private MaterialSwitch switchVoice, switchScreen, switchDisplayOffVoice;
    private static final int PERMISSION_RECORD_AUDIO = 1;

    // Custom Commands UI
    private View layoutCustomCommands;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trigger_settings);

        initViews();
        loadSavedStates();
        setupListeners();
        setupCustomCommands();
        setupEmergencyContacts();
        initContactPicker();
    }

    static class Contact {
        String name;
        String phone;
        String relation;

        Contact(String name, String phone, String relation) {
            this.name = name;
            this.phone = phone;
            this.relation = relation;
        }
    }

    private void setupCustomCommands() {
        layoutCustomCommands = findViewById(R.id.layout_custom_commands);

        // Setup mock commands
        setupCommandItem(R.id.cmd_help, "Help");
        setupCommandItem(R.id.cmd_pakdo, "Pakdo");
        setupCommandItem(R.id.cmd_give_phone, "Give me my phone");
    }

    private void setupCommandItem(int id, String phrase) {
        View item = findViewById(id);
        if (item != null) {
            TextView tvPhrase = item.findViewById(R.id.tv_phrase);
            if (tvPhrase != null) tvPhrase.setText(phrase);

            item.setOnClickListener(v -> {
                // Remove focus from others and highlight this
                resetCommandHighlights();
                item.setBackgroundResource(R.drawable.bg_glass_card_active);
                Toast.makeText(this, "Selected: " + phrase, Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void resetCommandHighlights() {
        int[] ids = {R.id.cmd_help, R.id.cmd_pakdo, R.id.cmd_give_phone};
        for (int id : ids) {
            View v = findViewById(id);
            if (v != null) v.setBackgroundResource(R.drawable.bg_glass_card);
        }
    }

    private SpeechRecognizer testSpeechRecognizer;
    private boolean isTestListening = false;

    private void startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_RECORD_AUDIO);
            return;
        }

        if (isTestListening) {
            stopTestListening();
        }

        isTestListening = true;
        testSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);

        testSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
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
                Log.e("TriggerSettings", "Speech error: " + error);
                if (isTestListening) {
                    Toast.makeText(TriggerSettingsActivity.this, "Speech error, please try again", Toast.LENGTH_SHORT).show();
                    stopTestListening();
                }
            }

            @Override
            public void onResults(Bundle results) {
                if (!isTestListening) return;
                
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                boolean emergencyFound = false;
                boolean lockFound = false;
                if (matches != null) {
                    for (String match : matches) {
                        String lowerMatch = match.toLowerCase();
                        Log.d("TriggerSettings", "Heard: " + lowerMatch);
                        if (lowerMatch.contains("help me") || lowerMatch.contains("pakdo") || lowerMatch.contains("give me my phone")) {
                            emergencyFound = true;
                            break;
                        }
                        if (lowerMatch.contains("display off") || lowerMatch.contains("lock screen") || lowerMatch.contains("screen off")) {
                            lockFound = true;
                            break;
                        }
                    }
                }

                if (emergencyFound) {
                    Toast.makeText(TriggerSettingsActivity.this, "Emergency command detected!", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(TriggerSettingsActivity.this, ShareLocationActivity.class));
                } else if (lockFound && switchDisplayOffVoice.isChecked()) {
                    Toast.makeText(TriggerSettingsActivity.this, "Lock screen command detected!", Toast.LENGTH_SHORT).show();
                    // Try to lock screen here too
                    try {
                        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
                        ComponentName cn = new ComponentName(TriggerSettingsActivity.this, MyDeviceAdminReceiver.class);
                        if (dpm != null) {
                            if (dpm.isAdminActive(cn)) {
                                dpm.lockNow();
                            } else {
                                Toast.makeText(TriggerSettingsActivity.this, "Please enable Device Admin first!", Toast.LENGTH_LONG).show();
                                requestDeviceAdmin();
                            }
                        }
                    } catch (Exception e) {
                        Log.e("TriggerSettings", "Error locking screen: " + e.getMessage(), e);
                    }
                } else if (lockFound) {
                    Toast.makeText(TriggerSettingsActivity.this, "Display Off Voice Command is disabled", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(TriggerSettingsActivity.this, "No command detected", Toast.LENGTH_SHORT).show();
                }
                stopTestListening();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}
            @Override
            public void onEvent(int eventType, Bundle params) {}
        });

        testSpeechRecognizer.startListening(intent);
    }

    private void stopTestListening() {
        isTestListening = false;
        if (testSpeechRecognizer != null) {
            try {
                testSpeechRecognizer.cancel();
                testSpeechRecognizer.destroy();
            } catch (Exception e) {
                Log.e("TriggerSettings", "Error stopping test recognizer", e);
            }
            testSpeechRecognizer = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTestListening();
        // Note: We removed the old speechRecognizer since we don't use it anymore
    }

    private void initViews() {
        switchVoice = findViewById(R.id.switch_voice);
        switchScreen = findViewById(R.id.switch_screen);
        switchDisplayOffVoice = findViewById(R.id.switch_display_off_voice);
    }

    private void loadSavedStates() {
        switchVoice.setChecked(prefs.getBoolean("switch_voice", true));
        switchScreen.setChecked(prefs.getBoolean("switch_screen", true));
        switchDisplayOffVoice.setChecked(prefs.getBoolean("switch_display_off_voice", true));
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
        
        // Test Lock Screen Button (Direct)
        Button testLockButton = findViewById(R.id.btn_test_lock_screen);
        if (testLockButton != null) {
            testLockButton.setOnClickListener(v -> lockScreenDirectly());
        }

        // Switch Listeners with auto-save and Service control
        switchVoice.setOnCheckedChangeListener((v, isChecked) -> {
            save("switch_voice", isChecked);
            manageService();
        });
        switchScreen.setOnCheckedChangeListener((v, isChecked) -> save("switch_screen", isChecked));
        switchDisplayOffVoice.setOnCheckedChangeListener((v, isChecked) -> {
            save("switch_display_off_voice", isChecked);
            if (isChecked) {
                requestDeviceAdmin();
            }
        });
    }
    
    private void lockScreenDirectly() {
        try {
            Log.d("TriggerSettings", "lockScreenDirectly() called");
            
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null) {
                Toast.makeText(this, "DevicePolicyManager is null!", Toast.LENGTH_LONG).show();
                Log.e("TriggerSettings", "DevicePolicyManager is null!");
                return;
            }
            
            ComponentName cn = new ComponentName(this, MyDeviceAdminReceiver.class);
            Log.d("TriggerSettings", "ComponentName: " + cn.flattenToString());
            
            boolean isAdminActive = dpm.isAdminActive(cn);
            Log.d("TriggerSettings", "isAdminActive: " + isAdminActive);
            
            if (isAdminActive) {
                Toast.makeText(this, "Locking screen NOW...", Toast.LENGTH_SHORT).show();
                dpm.lockNow();
                Log.d("TriggerSettings", "lockNow() called");
            } else {
                Toast.makeText(this, "Device Admin NOT enabled! Please enable it now.", Toast.LENGTH_LONG).show();
                requestDeviceAdmin();
            }
        } catch (Exception e) {
            Log.e("TriggerSettings", "Error locking screen directly: " + e.getMessage(), e);
            Toast.makeText(this, "Error locking screen: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void requestDeviceAdmin() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminName = new ComponentName(this, MyDeviceAdminReceiver.class);
        if (dpm != null) {
            if (dpm.isAdminActive(adminName)) {
                new AlertDialog.Builder(this)
                    .setTitle("Re-enable Device Admin")
                    .setMessage("To use the screen lock feature, you need to disable and re-enable Device Admin for Silent Guard. Would you like to do this now?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        dpm.removeActiveAdmin(adminName);
                        // Re-open the enable screen after a short delay
                        new android.os.Handler().postDelayed(() -> {
                            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminName);
                            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Silent Guard needs this to detect unauthorized access and lock your screen.");
                            startActivity(intent);
                        }, 500);
                    })
                    .setNegativeButton("No", null)
                    .show();
            } else {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminName);
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Silent Guard needs this to detect unauthorized access and lock your screen.");
                startActivity(intent);
            }
        }
    }

    private void save(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
        String status = value ? "Enabled" : "Disabled";
        Toast.makeText(this, "Feature " + status, Toast.LENGTH_SHORT).show();
    }
}
