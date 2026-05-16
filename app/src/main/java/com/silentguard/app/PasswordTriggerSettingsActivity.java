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
    private MaterialSwitch switchCaptureSelfie, switchShareLocation, switchSendSms, switchAttachSelfie;
    private android.widget.TextView txtTriggerLimit;
    private View btnMinus, btnPlus;
    private int triggerLimit = 3;

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
        switchCaptureSelfie = findViewById(R.id.switch_capture_selfie);
        switchShareLocation = findViewById(R.id.switch_share_location);
        switchSendSms = findViewById(R.id.switch_send_sms);
        switchAttachSelfie = findViewById(R.id.switch_attach_selfie);
        
        txtTriggerLimit = findViewById(R.id.txt_trigger_limit);
        btnMinus = findViewById(R.id.btn_minus);
        btnPlus = findViewById(R.id.btn_plus);

        switchPass.setChecked(prefs.getBoolean("switch_pass", true));
        switchCaptureSelfie.setChecked(prefs.getBoolean("pass_capture_selfie", true));
        switchShareLocation.setChecked(prefs.getBoolean("pass_share_loc", true));
        switchSendSms.setChecked(prefs.getBoolean("pass_send_sms", true));
        switchAttachSelfie.setChecked(prefs.getBoolean("pass_attach_selfie", true));
        
        triggerLimit = prefs.getInt("pass_trigger_limit", 3);
        txtTriggerLimit.setText(String.valueOf(triggerLimit));
    }

    private void setupListeners() {
        View backButton = findViewById(R.id.btn_back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        switchPass.setOnCheckedChangeListener((v, isChecked) -> {
            prefs.edit().putBoolean("switch_pass", isChecked).apply();
            if (isChecked) requestDeviceAdmin();
        });

        switchCaptureSelfie.setOnCheckedChangeListener((v, isChecked) -> prefs.edit().putBoolean("pass_capture_selfie", isChecked).apply());
        switchShareLocation.setOnCheckedChangeListener((v, isChecked) -> prefs.edit().putBoolean("pass_share_loc", isChecked).apply());
        switchSendSms.setOnCheckedChangeListener((v, isChecked) -> prefs.edit().putBoolean("pass_send_sms", isChecked).apply());
        switchAttachSelfie.setOnCheckedChangeListener((v, isChecked) -> prefs.edit().putBoolean("pass_attach_selfie", isChecked).apply());

        btnMinus.setOnClickListener(v -> {
            if (triggerLimit > 1) {
                triggerLimit--;
                updateTriggerLimit();
            }
        });

        btnPlus.setOnClickListener(v -> {
            if (triggerLimit < 10) {
                triggerLimit++;
                updateTriggerLimit();
            }
        });
    }

    private void updateTriggerLimit() {
        txtTriggerLimit.setText(String.valueOf(triggerLimit));
        prefs.edit().putInt("pass_trigger_limit", triggerLimit).apply();
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
