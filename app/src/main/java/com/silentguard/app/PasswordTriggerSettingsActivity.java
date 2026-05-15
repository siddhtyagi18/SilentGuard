package com.silentguard.app;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.materialswitch.MaterialSwitch;

public class PasswordTriggerSettingsActivity extends BaseSettingsActivity {

    private MaterialSwitch switchPass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_trigger_settings);

        initViews();
        setupListeners();
        setupEmergencyContacts();
        initContactPicker();
    }

    private void initViews() {
        switchPass = findViewById(R.id.switch_pass);
        switchPass.setChecked(prefs.getBoolean("switch_pass", false));
    }

    private void setupListeners() {
        View backButton = findViewById(R.id.btn_back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        switchPass.setOnCheckedChangeListener((v, isChecked) -> {
            prefs.edit().putBoolean("switch_pass", isChecked).apply();
            String status = isChecked ? "Enabled" : "Disabled";
            Toast.makeText(this, "Password Detection " + status, Toast.LENGTH_SHORT).show();
            
            if (isChecked) {
                requestDeviceAdmin();
            }
        });
    }

    private void requestDeviceAdmin() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminName = new ComponentName(this, MyDeviceAdminReceiver.class);
        if (dpm != null && !dpm.isAdminActive(adminName)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminName);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Silent Guard needs this to detect unauthorized access and lock your screen.");
            startActivity(intent);
        }
    }
}
