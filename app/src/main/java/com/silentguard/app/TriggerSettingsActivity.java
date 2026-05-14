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

public class TriggerSettingsActivity extends AppCompatActivity {

    private MaterialSwitch switchVoice, switchScreen, switchDisplayOffVoice;
    private SharedPreferences prefs;
    private SpeechRecognizer speechRecognizer;
    private static final int PERMISSION_RECORD_AUDIO = 1;
    private static final int PERMISSION_READ_CONTACTS = 2;

    // Custom Commands UI
    private View layoutCustomCommands;

    // Contact Picker Launcher
    private ActivityResultLauncher<Intent> contactPickerLauncher;

    // Contacts data
    private List<Contact> contactsList = new ArrayList<>();
    private LinearLayout layoutContactsList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trigger_settings);

        prefs = getSharedPreferences("SilentGuardPrefs", Context.MODE_PRIVATE);
        layoutContactsList = findViewById(R.id.layout_contacts_list);

        initViews();
        loadSavedStates();
        loadContacts();
        setupListeners();
        initSpeechRecognizer();
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

    private void saveContacts() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (Contact contact : contactsList) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("name", contact.name);
                jsonObject.put("phone", contact.phone);
                jsonObject.put("relation", contact.relation);
                jsonArray.put(jsonObject);
            }
            prefs.edit().putString("contacts", jsonArray.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadContacts() {
        contactsList.clear();
        String contactsJson = prefs.getString("contacts", null);
        if (contactsJson != null) {
            try {
                JSONArray jsonArray = new JSONArray(contactsJson);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    String name = jsonObject.getString("name");
                    String phone = jsonObject.getString("phone");
                    String relation = jsonObject.getString("relation");
                    contactsList.add(new Contact(name, phone, relation));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Add default contacts if none
            contactsList.add(new Contact("Pratha Varsheny", "+91 82669 94260", "Police"));
            contactsList.add(new Contact("Priya Verma", "+91 99887 76655", "Family"));
            saveContacts();
        }
    }

    private void refreshContactsUI() {
        // Remove all child views except the add button (last child)
        if (layoutContactsList.getChildCount() > 0) {
            View addButton = layoutContactsList.getChildAt(layoutContactsList.getChildCount() - 1);
            layoutContactsList.removeAllViews();
            layoutContactsList.addView(addButton);
        }

        // Add contact items dynamically
        for (int i = 0; i < contactsList.size(); i++) {
            Contact contact = contactsList.get(i);
            View contactView = LayoutInflater.from(this).inflate(R.layout.item_emergency_contact, layoutContactsList, false);
            setupContactItem(contactView, contact, i);
            // Insert before add button
            layoutContactsList.addView(contactView, layoutContactsList.getChildCount() - 1);
        }
    }

    private void showContactDialog(final int index) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_contact, null);
        builder.setView(dialogView);

        final EditText etName = dialogView.findViewById(R.id.et_contact_name);
        final EditText etPhone = dialogView.findViewById(R.id.et_contact_phone);
        final EditText etRelation = dialogView.findViewById(R.id.et_contact_relation);
        Button btnSave = dialogView.findViewById(R.id.btn_save_contact);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel_contact);

        // If editing, fill existing data
        if (index >= 0 && index < contactsList.size()) {
            Contact contact = contactsList.get(index);
            etName.setText(contact.name);
            etPhone.setText(contact.phone);
            etRelation.setText(contact.relation);
        }

        final AlertDialog dialog = builder.create();

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String relation = etRelation.getText().toString().trim();

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Name and phone are required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (index >= 0 && index < contactsList.size()) {
                // Edit existing
                contactsList.set(index, new Contact(name, phone, relation));
                Toast.makeText(this, "Contact updated", Toast.LENGTH_SHORT).show();
            } else {
                // Add new
                contactsList.add(new Contact(name, phone, relation));
                Toast.makeText(this, "Contact added", Toast.LENGTH_SHORT).show();
            }

            saveContacts();
            refreshContactsUI();
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
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
                
                // Add to contacts list
                contactsList.add(new Contact(name, number, "Imported"));
                saveContacts();
                refreshContactsUI();
                Toast.makeText(this, "Imported: " + name, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to import contact", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupEmergencyContacts() {
        refreshContactsUI();

        findViewById(R.id.btn_add_contact).setOnClickListener(v -> showContactDialog(-1));

        Button testSos = findViewById(R.id.btn_test_sos);
        if (testSos != null) {
            testSos.setOnClickListener(v -> {
                Toast.makeText(this, "Simulating Emergency Alert sharing...", Toast.LENGTH_LONG).show();
                v.postDelayed(() -> 
                    Toast.makeText(this, "Alert successfully shared with " + contactsList.size() + " contacts!", Toast.LENGTH_SHORT).show(), 
                    2000);
            });
        }
    }

    private void setupContactItem(View item, final Contact contact, final int index) {
        if (item != null) {
            TextView tvName = item.findViewById(R.id.tv_contact_name);
            TextView tvPhone = item.findViewById(R.id.tv_contact_phone);
            TextView tvRelation = item.findViewById(R.id.tv_relation_label);

            if (tvName != null) tvName.setText(contact.name);
            if (tvPhone != null) tvPhone.setText(contact.phone);
            if (tvRelation != null) tvRelation.setText(contact.relation);

            item.findViewById(R.id.iv_edit_contact).setOnClickListener(v -> showContactDialog(index));
            item.findViewById(R.id.iv_delete_contact).setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                    .setTitle("Delete Contact")
                    .setMessage("Are you sure you want to delete " + contact.name + "?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        contactsList.remove(index);
                        saveContacts();
                        refreshContactsUI();
                        Toast.makeText(this, "Removed: " + contact.name, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("No", null)
                    .show();
            });
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
            if (isChecked) {
                checkPermissionsAndStartService();
            } else {
                stopService(new Intent(this, SilentGuardService.class));
            }
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
