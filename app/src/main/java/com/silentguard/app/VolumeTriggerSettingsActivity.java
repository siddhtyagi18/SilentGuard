package com.silentguard.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.materialswitch.MaterialSwitch;

public class VolumeTriggerSettingsActivity extends BaseSettingsActivity {

    private MaterialSwitch switchVolumeHeader;
    private MaterialSwitch switchShareLocation, switchSendSms, switchAutoCall;
    private Button btnTestTrigger;
    private boolean isInternalChange = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_volume_trigger_settings);

        initViews();
        setupListeners();
        setupEmergencyContacts();
        initContactPicker();
    }

    private void initViews() {
        // Header
        switchVolumeHeader = findViewById(R.id.switch_volume_header);
        
        // Actions
        switchShareLocation = findViewById(R.id.switch_share_location);
        switchSendSms = findViewById(R.id.switch_send_sms);
        switchAutoCall = findViewById(R.id.switch_auto_call);
        
        btnTestTrigger = findViewById(R.id.btn_test_volume_trigger);

        // Load states
        switchVolumeHeader.setChecked(prefs.getBoolean("switch_volume", true));
        
        switchShareLocation.setChecked(prefs.getBoolean("vol_share_loc", true));
        switchSendSms.setChecked(prefs.getBoolean("vol_send_sms", true));
        switchAutoCall.setChecked(prefs.getBoolean("vol_auto_call", false));
    }

    private void setupListeners() {
        View backButton = findViewById(R.id.btn_back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        switchVolumeHeader.setOnCheckedChangeListener((v, isChecked) -> {
            prefs.edit().putBoolean("switch_volume", isChecked).apply();
            manageService();
            Toast.makeText(this, "Volume Trigger " + (isChecked ? "Enabled" : "Disabled"), Toast.LENGTH_SHORT).show();
        });
        
        switchShareLocation.setOnCheckedChangeListener((v, isChecked) -> prefs.edit().putBoolean("vol_share_loc", isChecked).apply());
        switchSendSms.setOnCheckedChangeListener((v, isChecked) -> prefs.edit().putBoolean("vol_send_sms", isChecked).apply());

        switchAutoCall.setOnCheckedChangeListener((v, isChecked) -> {
            if (isInternalChange) return;

            if (isChecked) {
                new AlertDialog.Builder(this)
                    .setTitle("Confirm Emergency Call")
                    .setMessage("Do you want to enable automatic emergency calling? The app will call your primary contact during SOS.")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        prefs.edit().putBoolean("vol_auto_call", true).apply();
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, 101);
                        }
                        Toast.makeText(this, "Auto Call Enabled", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("No", (dialog, which) -> {
                        isInternalChange = true;
                        switchAutoCall.setChecked(false);
                        isInternalChange = false;
                    })
                    .setCancelable(false)
                    .show();
            } else {
                prefs.edit().putBoolean("vol_auto_call", false).apply();
            }
        });

        btnTestTrigger.setOnClickListener(v -> {
            Toast.makeText(this, "Simulating Volume Button Trigger (3x)...", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, ShareLocationActivity.class);
            intent.putExtra("AUTO_TRIGGER", true);
            startActivity(intent);
        });
    }
}
