package com.silentguard.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseSettingsActivity extends AppCompatActivity {

    protected SharedPreferences prefs;
    protected static final int PERMISSION_READ_CONTACTS = 2;
    protected static final int PERMISSION_ALL = 3;

    // Contact Picker Launcher
    protected ActivityResultLauncher<Intent> contactPickerLauncher;

    // Contacts data
    protected List<TriggerSettingsActivity.Contact> contactsList = new ArrayList<>();
    protected LinearLayout layoutContactsList;

    private int volumePressCount = 0;
    private long lastVolumePressTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("SilentGuardPrefs", Context.MODE_PRIVATE);
    }

    protected void checkPermissionsAndStartService() {
        String[] permissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE
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
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } else {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_ALL);
        }
    }

    protected void manageService() {
        boolean isVoiceEnabled = prefs.getBoolean("switch_voice", true);
        boolean isVolumeEnabled = prefs.getBoolean("switch_volume", false);
        
        if (isVoiceEnabled || isVolumeEnabled) {
            checkPermissionsAndStartService();
        } else {
            stopService(new Intent(this, SilentGuardService.class));
        }
    }

    protected void initContactPicker() {
        contactPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    handleContactResult(result.getData().getData());
                }
            }
        );

        View importBtn = findViewById(R.id.btn_import_contacts);
        if (importBtn != null) {
            importBtn.setOnClickListener(v -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, PERMISSION_READ_CONTACTS);
                } else {
                    openContactPicker();
                }
            });
        }
    }

    protected void openContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    protected void handleContactResult(Uri contactUri) {
        String[] projection = {
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        try (Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                String number = cursor.getString(1);
                
                contactsList.add(new TriggerSettingsActivity.Contact(name, number, "Imported"));
                saveContacts();
                refreshContactsUI();
                Toast.makeText(this, "Imported: " + name, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to import contact", Toast.LENGTH_SHORT).show();
        }
    }

    protected void saveContacts() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (TriggerSettingsActivity.Contact contact : contactsList) {
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

    protected void loadContacts() {
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
                    contactsList.add(new TriggerSettingsActivity.Contact(name, phone, relation));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            contactsList.add(new TriggerSettingsActivity.Contact("Pratha Varsheny", "+91 82669 94260", "Police"));
            contactsList.add(new TriggerSettingsActivity.Contact("Priya Verma", "+91 99887 76655", "Family"));
            saveContacts();
        }
    }

    protected void refreshContactsUI() {
        if (layoutContactsList == null) return;
        
        if (layoutContactsList.getChildCount() > 0) {
            View addButton = layoutContactsList.getChildAt(layoutContactsList.getChildCount() - 1);
            layoutContactsList.removeAllViews();
            layoutContactsList.addView(addButton);
        }

        for (int i = 0; i < contactsList.size(); i++) {
            TriggerSettingsActivity.Contact contact = contactsList.get(i);
            View contactView = LayoutInflater.from(this).inflate(R.layout.item_emergency_contact, layoutContactsList, false);
            setupContactItem(contactView, contact, i);
            layoutContactsList.addView(contactView, layoutContactsList.getChildCount() - 1);
        }
    }

    protected void setupContactItem(View item, final TriggerSettingsActivity.Contact contact, final int index) {
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

    protected void showContactDialog(final int index) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_contact, null);
        builder.setView(dialogView);

        final EditText etName = dialogView.findViewById(R.id.et_contact_name);
        final EditText etPhone = dialogView.findViewById(R.id.et_contact_phone);
        final EditText etRelation = dialogView.findViewById(R.id.et_contact_relation);
        Button btnSave = dialogView.findViewById(R.id.btn_save_contact);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel_contact);

        if (index >= 0 && index < contactsList.size()) {
            TriggerSettingsActivity.Contact contact = contactsList.get(index);
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
                contactsList.set(index, new TriggerSettingsActivity.Contact(name, phone, relation));
                Toast.makeText(this, "Contact updated", Toast.LENGTH_SHORT).show();
            } else {
                contactsList.add(new TriggerSettingsActivity.Contact(name, phone, relation));
                Toast.makeText(this, "Contact added", Toast.LENGTH_SHORT).show();
            }

            saveContacts();
            refreshContactsUI();
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    protected void setupEmergencyContacts() {
        layoutContactsList = findViewById(R.id.layout_contacts_list);
        loadContacts();
        refreshContactsUI();

        View addBtn = findViewById(R.id.btn_add_contact);
        if (addBtn != null) {
            addBtn.setOnClickListener(v -> showContactDialog(-1));
        }

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

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if ((keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) 
                && prefs.getBoolean("switch_volume", false)) {
            
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastVolumePressTime < 1500) {
                volumePressCount++;
            } else {
                volumePressCount = 1;
            }
            lastVolumePressTime = currentTime;

            if (volumePressCount == 3) {
                Intent intent = new Intent(this, ShareLocationActivity.class);
                intent.putExtra("AUTO_TRIGGER", true);
                startActivity(intent);
                Toast.makeText(this, "3x Volume Trigger: SOS Sent!", Toast.LENGTH_SHORT).show();
            } else if (volumePressCount >= 4) {
                volumePressCount = 0;
                if (prefs.getBoolean("vol_auto_call", false)) {
                    Intent intent = new Intent(this, EmergencyResponseActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    Toast.makeText(this, "4x Volume Trigger: Emergency Call?", Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_READ_CONTACTS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openContactPicker();
            } else {
                Toast.makeText(this, "Permission denied to read contacts", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
