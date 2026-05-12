package com.silentguard.app;

import android.os.Bundle;
import android.text.TextUtils;
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
import java.util.HashMap;
import java.util.Map;

public class EmergencyContactsActivity extends AppCompatActivity {

    private EditText etPhone, etEmail;
    private Button btnSave;
    private ImageView btnBack;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_contacts);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();

        if (user == null) {
            finish();
            return;
        }

        mDatabase = FirebaseDatabase.getInstance().getReference("Users").child(user.getUid()).child("EmergencyContacts");

        etPhone = findViewById(R.id.et_emergency_phone);
        etEmail = findViewById(R.id.et_emergency_email);
        btnSave = findViewById(R.id.btn_save_contacts);
        btnBack = findViewById(R.id.btn_back);

        // Load current data
        mDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String phone = snapshot.child("phone").getValue(String.class);
                    String email = snapshot.child("email").getValue(String.class);
                    etPhone.setText(phone);
                    etEmail.setText(email);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        btnBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveContacts());
    }

    private void saveContacts() {
        String phone = etPhone.getText().toString().trim();
        String email = etEmail.getText().toString().trim();

        if (TextUtils.isEmpty(phone) || TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Please fill in both fields", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> contacts = new HashMap<>();
        contacts.put("phone", phone);
        contacts.put("email", email);

        mDatabase.setValue(contacts)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(EmergencyContactsActivity.this, "Contacts updated!", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(EmergencyContactsActivity.this, "Update failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}