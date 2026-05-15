package com.silentguard.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvName, tvEmail;
    private Button btnLogout;
    private View btnBack;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        
        if (user == null) {
            startActivity(new Intent(ProfileActivity.this, LoginActivity.class));
            finish();
            return;
        }

        mDatabase = FirebaseDatabase.getInstance().getReference("Users").child(user.getUid());

        tvName = findViewById(R.id.tv_profile_name);
        tvEmail = findViewById(R.id.tv_profile_email);
        btnLogout = findViewById(R.id.btn_logout);
        btnBack = findViewById(R.id.btn_back);

        tvEmail.setText(user.getEmail());

        loadUserData();

        btnBack.setOnClickListener(v -> finish());

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        setupItemClicks();
    }

    private void loadUserData() {
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    tvName.setText(name);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ProfileActivity.this, "Failed to load profile", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupItemClicks() {
        setupRow(R.id.item_edit_profile, "Edit Profile", "Update your personal details", "EDIT_PROFILE");
        setupRow(R.id.item_emergency_contacts, "Emergency Contacts", "Manage your trusted circle", "EMERGENCY_CONTACTS");
        setupRow(R.id.item_security_settings, "Security Settings", "Configure app protection", "SECURITY_SETTINGS");
        setupRow(R.id.item_privacy_policy, "Privacy Policy", "Read our privacy terms", "PRIVACY_POLICY");
    }

    private void setupRow(int id, String title, String desc, String action) {
        View row = findViewById(id);
        if (row != null) {
            TextView tvTitle = row.findViewById(R.id.setting_label);
            TextView tvDesc = row.findViewById(R.id.setting_desc);
            if (tvTitle != null) tvTitle.setText(title);
            if (tvDesc != null) tvDesc.setText(desc);
            
            row.setOnClickListener(v -> {
                if ("EDIT_PROFILE".equals(action)) {
                    startActivity(new Intent(ProfileActivity.this, EditProfileActivity.class));
                } else if ("EMERGENCY_CONTACTS".equals(action)) {
                    startActivity(new Intent(ProfileActivity.this, EmergencyContactsActivity.class));
                } else if ("SECURITY_SETTINGS".equals(action)) {
                    startActivity(new Intent(ProfileActivity.this, SettingsActivity.class));
                } else if ("PRIVACY_POLICY".equals(action)) {
                    startActivity(new Intent(ProfileActivity.this, PrivacyPolicyActivity.class));
                } else if (action != null) {
                    Toast.makeText(this, "Opening " + title + "...", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, title + " feature coming soon!", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
