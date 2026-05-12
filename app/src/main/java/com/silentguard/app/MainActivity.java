package com.silentguard.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    private View sosButton;
    private View cardSnatches, cardVolume, cardPassword;
    private TextView statusSnatches, statusVolume, statusPassword;
    private TextView appTitle;
    private View navHistory, navSecurity, navProfile;
    private Handler longPressHandler = new Handler();
    private boolean isLongPressing = false;
    private SharedPreferences prefs;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        
        if (user == null) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }

        mDatabase = FirebaseDatabase.getInstance().getReference("Users").child(user.getUid());
        prefs = getSharedPreferences("SilentGuardPrefs", Context.MODE_PRIVATE);

        // Initialize Views
        sosButton = findViewById(R.id.sos_button);
        cardSnatches = findViewById(R.id.card_snatches);
        cardVolume = findViewById(R.id.card_volume);
        cardPassword = findViewById(R.id.card_password);
        appTitle = findViewById(R.id.app_title);
        
        statusSnatches = findViewById(R.id.txt_status_snatches);
        statusVolume = findViewById(R.id.txt_status_volume);
        statusPassword = findViewById(R.id.txt_status_password);
        
        navHistory = findViewById(R.id.nav_history);
        navSecurity = findViewById(R.id.nav_security);
        navProfile = findViewById(R.id.nav_profile);

        setupClickListeners();
        setupSOSInteraction();
        loadUserData();
        checkBatteryOptimizations();
    }

    private void checkBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    private void loadUserData() {
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    if (name != null) {
                        appTitle.setText("Hello, " + name.split(" ")[0]);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCardStatuses();
    }

    private void updateCardStatuses() {
        updateStatus(statusSnatches, cardSnatches, prefs.getBoolean("switch_power", true)); // Snatches tied to power for now
        updateStatus(statusVolume, cardVolume, prefs.getBoolean("switch_volume", false));
        updateStatus(statusPassword, cardPassword, prefs.getBoolean("switch_pass", false));
    }

    private void updateStatus(TextView statusTxt, View card, boolean isActive) {
        if (isActive) {
            statusTxt.setText("ACTIVE");
            statusTxt.setTextColor(ContextCompat.getColor(this, R.color.neon_violet));
            card.setBackgroundResource(R.drawable.bg_glass_card_active);
        } else {
            statusTxt.setText("INACTIVE");
            statusTxt.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            card.setBackgroundResource(R.drawable.bg_glass_card);
        }
    }

    private void setupClickListeners() {
        cardSnatches.setOnClickListener(v -> startActivity(new Intent(this, TriggerSettingsActivity.class)));
        cardVolume.setOnClickListener(v -> startActivity(new Intent(this, TriggerSettingsActivity.class)));
        cardPassword.setOnClickListener(v -> startActivity(new Intent(this, TriggerSettingsActivity.class)));

        navHistory.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        navSecurity.setOnClickListener(v -> startActivity(new Intent(this, HowItWorksActivity.class)));
        navProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
    }

    private void setupSOSInteraction() {
        sosButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isLongPressing = true;
                    longPressHandler.postDelayed(sosAction, 2000);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isLongPressing = false;
                    longPressHandler.removeCallbacks(sosAction);
                    return true;
            }
            return false;
        });
    }

    private Runnable sosAction = new Runnable() {
        @Override
        public void run() {
            if (isLongPressing) {
                startActivity(new Intent(MainActivity.this, ShareLocationActivity.class));
                Toast.makeText(MainActivity.this, "Emergency SOS Triggered!", Toast.LENGTH_LONG).show();
            }
        }
    };
}
