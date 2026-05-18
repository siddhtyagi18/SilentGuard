package com.silentguard.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import androidx.viewpager2.widget.ViewPager2;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 heroViewPager;
    private HeroCarouselAdapter heroAdapter;
    private View sosButton;
    private View cardSnatches, cardVolume, cardPassword;
    private TextView statusSnatches, statusVolume, statusPassword;
    private TextView appTitle, securitySummaryText;
    private TextView securityStatusText;
    private ImageView securityStatusIcon;
    private View securityStatusAccent;
    private ImageView notificationBellIcon;
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

        setupHeroCarousel();
        
        appTitle = findViewById(R.id.app_title);
        securitySummaryText = findViewById(R.id.security_summary_text);
        
        statusSnatches = findViewById(R.id.txt_status_snatches);
        statusVolume = findViewById(R.id.txt_status_volume);
        statusPassword = findViewById(R.id.txt_status_password);
        
        securityStatusText = findViewById(R.id.security_status_text);
        securityStatusIcon = findViewById(R.id.security_status_icon);
        securityStatusAccent = findViewById(R.id.security_status_accent);
        
        notificationBellIcon = findViewById(R.id.ic_notif);
        
        navHistory = findViewById(R.id.nav_history);
        navSecurity = findViewById(R.id.nav_security);
        navProfile = findViewById(R.id.nav_profile);

        setupClickListeners();
        setupSOSInteraction();
        loadUserData();
        checkBatteryOptimizations();
        checkAccessibilityService();
        checkOverlayPermission();
        SilentGuardService.checkPermissionsOnAppOpen(this);
        syncContactsFromFirebase();
        startSilentGuardService();
        applyAnimations();
    }

    private void setupHeroCarousel() {
        heroViewPager = findViewById(R.id.hero_view_pager);
        List<HeroCarouselAdapter.HeroItem> items = new ArrayList<>();
        
        // Slide 1: Green Shield (Original)
        items.add(new HeroCarouselAdapter.HeroItem(
                "We've got your", "back!", 
                "Our smart protection is\nalways active to keep you safe.",
                R.drawable.ic_shield_checked, 0));
                
        // Slide 2: Purple Shield (Stay Secure Always)
        items.add(new HeroCarouselAdapter.HeroItem(
                "Stay Secure", "Always", 
                "Smart protection working silently to keep you safe 24/7.",
                R.drawable.ic_shield_lock_purple, 2));
                
        // Slide 3: Orange SOS (Quick Response SOS)
        items.add(new HeroCarouselAdapter.HeroItem(
                "Quick Response", "SOS", 
                "Instant SOS alerts and emergency help when you need it most.",
                R.drawable.ic_sos_circle_orange, 1));

        heroAdapter = new HeroCarouselAdapter(items);
        heroViewPager.setAdapter(heroAdapter);
    }

    private void applyAnimations() {
        // Entry Slide-Up for feature cards
        Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up);
        
        View securityStatusCard = findViewById(R.id.security_status_accent).getParent() instanceof View ? 
                (View) findViewById(R.id.security_status_accent).getParent() : null;
        if (securityStatusCard != null) securityStatusCard.startAnimation(slideUp);

        if (cardSnatches != null) cardSnatches.startAnimation(slideUp);
        if (cardVolume != null) cardVolume.startAnimation(slideUp);
        if (cardPassword != null) cardPassword.startAnimation(slideUp);
    }

    private void syncContactsFromFirebase() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            DatabaseReference contactsRef = FirebaseDatabase.getInstance().getReference("Users")
                    .child(user.getUid()).child("EmergencyContacts");
            
            contactsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String phone = snapshot.child("phone").getValue(String.class);
                        if (!TextUtils.isEmpty(phone)) {
                            saveToLocalPrefs(phone);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
        }
    }

    private void saveToLocalPrefs(String phone) {
        try {
            JSONArray array = new JSONArray();
            JSONObject contactObj = new JSONObject();
            contactObj.put("name", "Emergency Contact");
            contactObj.put("phone", phone);
            contactObj.put("relation", "Family");
            array.put(contactObj);
            
            prefs.edit().putString("contacts", array.toString()).apply();
            Log.d("MainActivity", "Synced contacts to local prefs");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startSilentGuardService() {
        Intent serviceIntent = new Intent(this, SilentGuardService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Log.d("MainActivity", "SilentGuardService started successfully");
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Display Over Other Apps")
                    .setMessage("Silent Guard needs permission to show the Emergency Call Popup over other apps and the lock screen.")
                    .setPositiveButton("Grant Permission", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            }
        }
    }

    private void checkAccessibilityService() {
        boolean isVolumeEnabled = prefs.getBoolean("switch_volume", false);
        if (isVolumeEnabled && !isAccessibilityServiceEnabled()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Enable Accessibility Service")
                .setMessage("To use Volume Button Trigger when the screen is off, you need to enable the Accessibility Service for Silent Guard.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + SilentGuardAccessibilityService.class.getName();
        android.content.ContentResolver contentResolver = getContentResolver();
        String enabledServices = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        return enabledServices != null && enabledServices.contains(service);
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
        String localName = prefs.getString("user_name", "");
        if (!TextUtils.isEmpty(localName)) {
            appTitle.setText("Hello, " + localName.split(" ")[0]);
        }

        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    if (name != null) {
                        appTitle.setText("Hello, " + name.split(" ")[0]);
                        prefs.edit().putString("user_name", name).apply();
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
        loadUserData();
        updateCardStatuses();
    }

    private void updateCardStatuses() {
        boolean voiceActive = prefs.getBoolean("switch_voice", true);
        boolean volumeActive = prefs.getBoolean("switch_volume", false);
        boolean passwordActive = prefs.getBoolean("switch_pass", false);
        
        updateStatus(statusSnatches, cardSnatches, voiceActive);
        updateStatus(statusVolume, cardVolume, volumeActive);
        updateStatus(statusPassword, cardPassword, passwordActive);
        
        updateSecurityStatus(voiceActive, volumeActive, passwordActive);
    }
    
    private void updateSecurityStatus(boolean voiceActive, boolean volumeActive, boolean passwordActive) {
        int activeCount = 0;
        if (voiceActive) activeCount++;
        if (volumeActive) activeCount++;
        if (passwordActive) activeCount++;
        
        if (activeCount == 3) {
            securityStatusText.setText("All systems are active");
            securitySummaryText.setText("You're Protected");
            securityStatusIcon.setImageResource(android.R.drawable.checkbox_on_background);
            securityStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.brand_success));
            securityStatusAccent.setBackgroundColor(ContextCompat.getColor(this, R.color.brand_success));
            securityStatusText.setTextColor(ContextCompat.getColor(this, R.color.brand_success));
        } else if (activeCount > 0) {
            securityStatusText.setText(activeCount + " system" + (activeCount > 1 ? "s" : "") + " active");
            securitySummaryText.setText("Partially Protected");
            securityStatusIcon.setImageResource(android.R.drawable.presence_online);
            securityStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.brand_warning));
            securityStatusAccent.setBackgroundColor(ContextCompat.getColor(this, R.color.brand_warning));
            securityStatusText.setTextColor(ContextCompat.getColor(this, R.color.brand_warning));
        } else {
            securityStatusText.setText("No systems active");
            securitySummaryText.setText("You're at risk");
            securityStatusIcon.setImageResource(android.R.drawable.presence_invisible);
            securityStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.brand_danger));
            securityStatusAccent.setBackgroundColor(ContextCompat.getColor(this, R.color.brand_danger));
            securityStatusText.setTextColor(ContextCompat.getColor(this, R.color.brand_danger));
        }
    }

    private void updateStatus(TextView statusTxt, View card, boolean isActive) {
        if (isActive) {
            statusTxt.setText("ACTIVE");
            if (statusTxt == statusSnatches) {
                statusTxt.setTextColor(ContextCompat.getColor(this, R.color.feature_snatch_accent));
                statusTxt.setBackgroundColor(Color.parseColor("#158B5CF6"));
            } else if (statusTxt == statusVolume) {
                statusTxt.setTextColor(ContextCompat.getColor(this, R.color.feature_volume_accent));
                statusTxt.setBackgroundColor(Color.parseColor("#15F97316"));
            } else if (statusTxt == statusPassword) {
                statusTxt.setTextColor(ContextCompat.getColor(this, R.color.feature_pass_accent));
                statusTxt.setBackgroundColor(Color.parseColor("#1522C55E"));
            }
        } else {
            statusTxt.setText("INACTIVE");
            // Match the image where INACTIVE also has the feature's color but maybe slightly more transparent or gray
            // Actually, the image shows "INACTIVE" in purple for Phone Snatches, so we'll use the same accent color
            if (statusTxt == statusSnatches) {
                statusTxt.setTextColor(ContextCompat.getColor(this, R.color.feature_snatch_accent));
                statusTxt.setBackgroundColor(Color.parseColor("#108B5CF6"));
            } else if (statusTxt == statusVolume) {
                statusTxt.setTextColor(ContextCompat.getColor(this, R.color.feature_volume_accent));
                statusTxt.setBackgroundColor(Color.parseColor("#10F97316"));
            } else if (statusTxt == statusPassword) {
                statusTxt.setTextColor(ContextCompat.getColor(this, R.color.feature_pass_accent));
                statusTxt.setBackgroundColor(Color.parseColor("#1022C55E"));
            }
        }
        
        // Ensure padding is kept to match the pill shape in image
        int py = (int) (4 * getResources().getDisplayMetrics().density);
        int px = (int) (10 * getResources().getDisplayMetrics().density);
        statusTxt.setPadding(px, py, px, py);
    }

    private void setupClickListeners() {
        Animation clickAnim = AnimationUtils.loadAnimation(this, R.anim.click_press);

        cardSnatches.setOnClickListener(v -> {
            v.startAnimation(clickAnim);
            startActivity(new Intent(MainActivity.this, TriggerSettingsActivity.class));
        });

        cardVolume.setOnClickListener(v -> {
            v.startAnimation(clickAnim);
            startActivity(new Intent(MainActivity.this, VolumeTriggerSettingsActivity.class));
        });

        cardPassword.setOnClickListener(v -> {
            v.startAnimation(clickAnim);
            startActivity(new Intent(MainActivity.this, PasswordTriggerSettingsActivity.class));
        });

        navHistory.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        navSecurity.setOnClickListener(v -> startActivity(new Intent(this, HowItWorksActivity.class)));
        navProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        
        notificationBellIcon.setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationCenterActivity.class));
        });
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

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        return super.onKeyDown(keyCode, event);
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
