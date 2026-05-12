package com.silentguard.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        View backButton = findViewById(R.id.btn_back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        Button logoutButton = findViewById(R.id.btn_logout);
        if (logoutButton != null) {
            logoutButton.setOnClickListener(v -> {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }

        setupSettingsClicks();
    }

    private void setupSettingsClicks() {
        setupSetting(R.id.set_contacts, "Emergency Contacts", "Manage trusted circle", android.R.drawable.ic_menu_myplaces, "EMERGENCY_CONTACTS");
        setupSetting(R.id.set_voice, "Voice Command", "Change trigger word", android.R.drawable.ic_btn_speak_now, null);
        setupSetting(R.id.set_password, "Change Password", "Update app security", android.R.drawable.ic_lock_idle_lock, null);
        setupSetting(R.id.set_lock, "App Lock", "Secure the application", android.R.drawable.ic_lock_lock, null);
        setupSetting(R.id.set_boot, "Start on Boot", "Run automatically", android.R.drawable.ic_menu_rotate, null);
        setupSetting(R.id.set_dark, "Dark Mode", "Theme settings", android.R.drawable.ic_menu_day, null);
        setupSetting(R.id.set_notif, "Notifications", "Alert preferences", android.R.drawable.ic_popup_reminder, null);
        setupSetting(R.id.set_about, "About", "App information", android.R.drawable.ic_menu_info_details, null);
    }

    private void setupSetting(int id, String title, String desc, int iconRes, String action) {
        View row = findViewById(id);
        if (row != null) {
            TextView tvTitle = row.findViewById(R.id.setting_label);
            TextView tvDesc = row.findViewById(R.id.setting_desc);
            android.widget.ImageView ivIcon = row.findViewById(R.id.setting_icon);
            
            if (tvTitle != null) tvTitle.setText(title);
            if (tvDesc != null) tvDesc.setText(desc);
            if (ivIcon != null) ivIcon.setImageResource(iconRes);
            
            row.setOnClickListener(v -> {
                if ("EMERGENCY_CONTACTS".equals(action)) {
                    startActivity(new Intent(SettingsActivity.this, EmergencyContactsActivity.class));
                } else {
                    Toast.makeText(this, title + " feature coming soon!", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
