package com.silentguard.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class ShareLocationActivity extends AppCompatActivity {

    private EditText etLocation;
    private TextView txtLastUpdated;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int SMS_PERMISSION_REQUEST_CODE = 1002;
    
    private String emergencyPhone, emergencyEmail;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_location);

        etLocation = findViewById(R.id.et_location);
        txtLastUpdated = findViewById(R.id.txt_last_updated);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId != null) {
            mDatabase = FirebaseDatabase.getInstance().getReference("Users").child(userId).child("EmergencyContacts");
            loadEmergencyContacts();
        }

        View backButton = findViewById(R.id.btn_back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        Button sendButton = findViewById(R.id.btn_send_alert);
        if (sendButton != null) {
            sendButton.setOnClickListener(v -> triggerSOS());
        }

        requestLocationPermissions();
        setupAutoSave();
    }

    private void loadEmergencyContacts() {
        mDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    emergencyPhone = snapshot.child("phone").getValue(String.class);
                    emergencyEmail = snapshot.child("email").getValue(String.class);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void triggerSOS() {
        if (emergencyPhone == null || emergencyEmail == null) {
            Toast.makeText(this, "Please set emergency contacts in Profile first!", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, EmergencyContactsActivity.class));
            return;
        }

        String locationText = etLocation.getText().toString();
        String message = "EMERGENCY SOS! I need help. My current location: " + locationText;

        // 1. Send SMS
        sendSMS(emergencyPhone, message);

        // 2. Send Email (via Intent)
        sendEmail(emergencyEmail, "EMERGENCY SOS ALERT", message);
    }

    private void sendSMS(String phone, String message) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, SMS_PERMISSION_REQUEST_CODE);
        } else {
            try {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(phone, null, message, null, null);
                Toast.makeText(this, "SMS Alert Sent!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Failed to send SMS: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void sendEmail(String email, String subject, String body) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, body);
        
        try {
            startActivity(Intent.createChooser(intent, "Send Email..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "No email clients installed.", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getLastLocation();
        }
    }

    private void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                updateLocationUI(location);
            } else {
                etLocation.setText("Unable to fetch real-time location");
            }
        });
    }

    private void updateLocationUI(Location location) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                String address = addresses.get(0).getAddressLine(0);
                etLocation.setText(address);
            } else {
                etLocation.setText(location.getLatitude() + ", " + location.getLongitude());
            }
        } catch (IOException e) {
            etLocation.setText(location.getLatitude() + ", " + location.getLongitude());
        }
    }

    private void setupAutoSave() {
        etLocation.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                txtLastUpdated.setText("Last updated: Manually edited");
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLastLocation();
        }
    }
}
