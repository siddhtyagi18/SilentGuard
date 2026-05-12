package com.silentguard.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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

public class EditProfileActivity extends AppCompatActivity {

    private EditText etName, etEmail;
    private Button btnSave;
    private ImageView btnBack;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private TextView tvSaveStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();

        if (user == null) {
            finish();
            return;
        }

        mDatabase = FirebaseDatabase.getInstance().getReference("Users").child(user.getUid());

        etName = findViewById(R.id.et_edit_name);
        etEmail = findViewById(R.id.et_edit_email);
        btnSave = findViewById(R.id.btn_save_profile);
        btnBack = findViewById(R.id.btn_back);
        
        // Hide the save button since we are doing auto-save
        btnSave.setVisibility(View.GONE);

        etEmail.setText(user.getEmail());

        // Load current data
        mDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    etName.setText(name);
                    setupAutoSave();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        btnBack.setOnClickListener(v -> finish());
    }

    private void setupAutoSave() {
        etName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String newName = s.toString().trim();
                if (!TextUtils.isEmpty(newName)) {
                    saveProfileSilently(newName);
                }
            }
        });
    }

    private void saveProfileSilently(String newName) {
        mDatabase.child("name").setValue(newName);
    }
}