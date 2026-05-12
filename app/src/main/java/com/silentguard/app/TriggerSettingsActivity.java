package com.silentguard.app;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
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
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
    private static final int PERMISSION_READ_CONTACTS = 2;

    // Custom Commands UI
    private View btnExpandCommands, layoutCustomCommands;
    private ImageView ivExpandArrow;
    private boolean isCommandsExpanded = false;

    // Contact Picker Launcher
    private ActivityResultLauncher<Intent> contactPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trigger_settings);

        prefs = getSharedPreferences("SilentGuardPrefs", Context.MODE_PRIVATE);

        initViews();
        loadSavedStates();
        setupListeners();
        initSpeechRecognizer();
        setupCustomCommands();
        setupEmergencyContacts();
        initContactPicker();
    }

    private void initContactPicker() {
        contactPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    handleContactResult(result.getData().getData());
                }
            }
        );

        findViewById(R.id.btn_import_contacts).setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, PERMISSION_READ_CONTACTS);
            } else {
                openContactPicker();
            }
        });
    }

    private void openContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    private void handleContactResult(Uri contactUri) {
        String[] projection = {
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        try (Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                String number = cursor.getString(1);
                
                // For demonstration, we'll update the first contact card
                setupContactItem(R.id.contact_1, name, number, "Imported");
                Toast.makeText(this, "Imported: " + name, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to import contact", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupEmergencyContacts() {
        // Setup mock contacts
        setupContactItem(R.id.contact_1, "Aman Sharma", "+91 98765 43210", "Guardian");
        setupContactItem(R.id.contact_2, "Priya Verma", "+91 99887 76655", "Family");

        findViewById(R.id.btn_add_contact).setOnClickListener(v -> 
            Toast.makeText(this, "Add contact feature coming soon!", Toast.LENGTH_SHORT).show());

        Button testSos = findViewById(R.id.btn_test_sos);
        if (testSos != null) {
            testSos.setOnClickListener(v -> {
                Toast.makeText(this, "Simulating Emergency Alert sharing...", Toast.LENGTH_LONG).show();
                // Add a small delay then success toast
                v.postDelayed(() -> 
                    Toast.makeText(this, "Alert successfully shared with 2 contacts!", Toast.LENGTH_SHORT).show(), 
                    2000);
            });
        }
    }

    private void setupContactItem(int id, String name, String phone, String relation) {
        View item = findViewById(id);
        if (item != null) {
            TextView tvName = item.findViewById(R.id.tv_contact_name);
            TextView tvPhone = item.findViewById(R.id.tv_contact_phone);
            TextView tvRelation = item.findViewById(R.id.tv_relation_label);

            if (tvName != null) tvName.setText(name);
            if (tvPhone != null) tvPhone.setText(phone);
            if (tvRelation != null) tvRelation.setText(relation);

            item.findViewById(R.id.iv_edit_contact).setOnClickListener(v -> 
                Toast.makeText(this, "Edit: " + name, Toast.LENGTH_SHORT).show());
            item.findViewById(R.id.iv_delete_contact).setOnClickListener(v -> 
                Toast.makeText(this, "Remove: " + name, Toast.LENGTH_SHORT).show());
        }
    }

    private void setupCustomCommands() {
        btnExpandCommands = findViewById(R.id.btn_expand_commands);
        layoutCustomCommands = findViewById(R.id.layout_custom_commands);
        ivExpandArrow = findViewById(R.id.iv_expand_arrow);

        btnExpandCommands.setOnClickListener(v -> {
            isCommandsExpanded = !isCommandsExpanded;
            layoutCustomCommands.setVisibility(isCommandsExpanded ? View.VISIBLE : View.GONE);
            ivExpandArrow.setRotation(isCommandsExpanded ? 180f : 0f);
        });

        // Setup mock commands
        setupCommandItem(R.id.cmd_help, "Help", "Turns screen off instantly and starts silent recording");
        setupCommandItem(R.id.cmd_pakdo, "Pakdo", "Shares live location with saved emergency contacts");
        setupCommandItem(R.id.cmd_give_phone, "Give me my phone", "Activates fake lock mode and hides notifications");

        findViewById(R.id.btn_add_command).setOnClickListener(v -> 
            Toast.makeText(this, "Add new command coming soon!", Toast.LENGTH_SHORT).show());
    }

    private void setupCommandItem(int id, String phrase, String action) {
        View item = findViewById(id);
        if (item != null) {
            TextView tvPhrase = item.findViewById(R.id.tv_phrase);
            TextView tvAction = item.findViewById(R.id.tv_action);
            if (tvPhrase != null) tvPhrase.setText(phrase);
            if (tvAction != null) tvAction.setText(action);

            item.setOnClickListener(v -> {
                // Remove focus from others and highlight this
                resetCommandHighlights();
                item.setBackgroundResource(R.drawable.bg_glass_card_active);
                Toast.makeText(this, "Selected: " + phrase, Toast.LENGTH_SHORT).show();
            });

            item.findViewById(R.id.iv_edit).setOnClickListener(v -> 
                Toast.makeText(this, "Edit: " + phrase, Toast.LENGTH_SHORT).show());
            item.findViewById(R.id.iv_delete).setOnClickListener(v -> 
                Toast.makeText(this, "Delete: " + phrase, Toast.LENGTH_SHORT).show());
        }
    }

    private void resetCommandHighlights() {
        int[] ids = {R.id.cmd_help, R.id.cmd_pakdo, R.id.cmd_give_phone};
        for (int id : ids) {
            View v = findViewById(id);
            if (v != null) v.setBackgroundResource(R.drawable.bg_glass_card);
        }
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
        switchPass.setOnCheckedChangeListener((v, isChecked) -> {
            save("switch_pass", isChecked);
            if (isChecked) {
                requestDeviceAdmin();
                requestCameraPermission();
            }
        });
    }

    private void requestDeviceAdmin() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminName = new ComponentName(this, MyDeviceAdminReceiver.class);
        if (dpm != null && !dpm.isAdminActive(adminName)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminName);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Silent Guard needs this to detect unauthorized access attempts.");
            startActivity(intent);
        }
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        }
    }

    private void save(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
        String status = value ? "Enabled" : "Disabled";
        Toast.makeText(this, "Feature " + status, Toast.LENGTH_SHORT).show();
    }
}
