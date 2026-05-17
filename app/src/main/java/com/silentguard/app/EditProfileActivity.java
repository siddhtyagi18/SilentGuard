package com.silentguard.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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

public class EditProfileActivity extends AppCompatActivity {

    private EditText etName, etEmail;
    private Button btnSave;
    private ImageView btnBack;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private SharedPreferences prefs;
    private static final String TAG = "EditProfile";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        mAuth = FirebaseAuth.getInstance();
        prefs = getSharedPreferences("SilentGuardPrefs", Context.MODE_PRIVATE);
        FirebaseUser user = mAuth.getCurrentUser();

        if (user == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mDatabase = FirebaseDatabase.getInstance().getReference("Users").child(user.getUid());

        etName = findViewById(R.id.et_edit_name);
        etEmail = findViewById(R.id.et_edit_email);
        btnSave = findViewById(R.id.btn_save_profile);
        btnBack = findViewById(R.id.btn_back);

        etEmail.setText(user.getEmail());

        loadCurrentData();

        btnBack.setOnClickListener(v -> finish());
        
        btnSave.setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            if (TextUtils.isEmpty(newName)) {
                Toast.makeText(EditProfileActivity.this, "Please enter your name", Toast.LENGTH_SHORT).show();
                return;
            }
            saveProfile(newName);
        });
    }

    private void loadCurrentData() {
        String localName = prefs.getString("user_name", "");
        if (!TextUtils.isEmpty(localName)) {
            etName.setText(localName);
        }

        mDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    if (name != null && !TextUtils.isEmpty(name)) {
                        etName.setText(name);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load profile", error.toException());
            }
        });
    }

    private void saveProfile(String newName) {
        Log.d(TAG, "Starting save process for name: " + newName);
        Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT).show();

        prefs.edit().putString("user_name", newName).apply();
        Log.d(TAG, "Saved locally to SharedPreferences");

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Profile saved locally!", Toast.LENGTH_SHORT).show();
            finishAfterDelay();
            return;
        }

        mDatabase.child("name").setValue(newName)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Firebase save successful!");
                    Toast.makeText(EditProfileActivity.this, "Profile saved successfully!", Toast.LENGTH_SHORT).show();
                    finishAfterDelay();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firebase save failed", e);
                    Toast.makeText(EditProfileActivity.this, "Profile saved locally! Will sync later.", Toast.LENGTH_LONG).show();
                    finishAfterDelay();
                });
    }

    private void finishAfterDelay() {
        btnSave.postDelayed(() -> {
            finish();
        }, 1000);
    }
}
