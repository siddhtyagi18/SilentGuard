package com.silentguard.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SettingsActivity extends AppCompatActivity {
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = getSharedPreferences("SilentGuardPrefs", MODE_PRIVATE);

        View backButton = findViewById(R.id.btn_back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        Button logoutButton = findViewById(R.id.btn_logout);
        if (logoutButton != null) {
            logoutButton.setOnClickListener(v -> {
                new AlertDialog.Builder(SettingsActivity.this)
                    .setTitle("Log Out")
                    .setMessage("Are you sure you want to log out?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        FirebaseAuth.getInstance().signOut();
                        Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                    .show();
            });
        }

        setupSettingsClicks();
    }

    private void setupSettingsClicks() {
        setupSetting(R.id.set_contacts, "Emergency Contacts", "Manage trusted circle", android.R.drawable.ic_menu_myplaces, "EMERGENCY_CONTACTS");
        setupSetting(R.id.set_password, "Change Password", "Update app security", android.R.drawable.ic_lock_idle_lock, "CHANGE_PASSWORD");
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
                } else if ("CHANGE_PASSWORD".equals(action)) {
                    showChangePasswordDialog();
                }
            });
        }
    }

    private void showChangePasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.activity_signup, null);
        
        EditText etEmail = dialogView.findViewById(R.id.et_email);
        EditText etPassword = dialogView.findViewById(R.id.et_password);
        EditText etConfirmPassword = dialogView.findViewById(R.id.et_confirm_password);
        
        if (etEmail != null) etEmail.setVisibility(View.GONE);
        if (etPassword != null) {
            etPassword.setHint("New Password");
        }
        if (etConfirmPassword != null) {
            etConfirmPassword.setHint("Confirm New Password");
        }
        
        builder.setView(dialogView)
               .setTitle("Change Password")
               .setPositiveButton("Update", null)
               .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> {
                String newPassword = etPassword != null ? etPassword.getText().toString().trim() : "";
                String confirmPassword = etConfirmPassword != null ? etConfirmPassword.getText().toString().trim() : "";
                
                if (TextUtils.isEmpty(newPassword)) {
                    Toast.makeText(this, "Please enter new password", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (newPassword.length() < 6) {
                    Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!newPassword.equals(confirmPassword)) {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user != null) {
                    user.updatePassword(newPassword)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            } else {
                                Toast.makeText(this, "Failed to update password: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                } else {
                    Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
                }
            });
        });
        dialog.show();
    }

}

