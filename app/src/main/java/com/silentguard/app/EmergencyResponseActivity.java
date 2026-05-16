package com.silentguard.app;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import android.os.Build;
import android.view.WindowManager;

public class EmergencyResponseActivity extends AppCompatActivity {

    private View popupContainer, iconGlow;
    private ImageView ivWarning;
    private TextView tvTitle, tvMessage;
    private Button btnPositive, btnNegative;
    
    private List<SilentGuardService.Contact> contactsList = new ArrayList<>();
    private int currentContactIndex = 0;
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;
    private boolean isCallInProgress = false;
    private Handler autoCallHandler = new Handler(Looper.getMainLooper());
    private Runnable autoCallRunnable;
    private int countdownSeconds = 5;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Essential flags to show over lock screen - must be set BEFORE super.onCreate or setContentView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) {
                km.requestDismissKeyguard(this, null);
            }
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        super.onCreate(savedInstanceState);
        Log.d("EmergencyResponse", "onCreate: Popup activity starting...");
        
        setContentView(R.layout.activity_emergency_response);
        Log.d("EmergencyResponse", "onCreate: Layout set");

        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        setupPhoneStateListener();
        
        loadEmergencyContacts();
        initViews();
        startEntranceAnimation();
        provideHapticFeedback();
        
        // Start auto-call countdown if enabled
        startAutoCallCountdown();
    }

    private void startAutoCallCountdown() {
        android.content.SharedPreferences prefs = getSharedPreferences("SilentGuardPrefs", Context.MODE_PRIVATE);
        if (contactsList.isEmpty()) {
            tvTitle.setText("No Emergency Contacts");
            tvMessage.setText("Please add contacts in settings to enable calling.");
            btnPositive.setVisibility(View.GONE);
            btnNegative.setText("CLOSE");
            return;
        }

        if (prefs.getBoolean("vol_auto_call", true)) {
            autoCallRunnable = new Runnable() {
                @Override
                public void run() {
                    if (countdownSeconds > 0) {
                        btnPositive.setText("CALL NOW (" + countdownSeconds + "s)");
                        countdownSeconds--;
                        autoCallHandler.postDelayed(this, 1000);
                    } else {
                        btnPositive.setText("CALLING...");
                        startSequentialCalling();
                    }
                }
            };
            autoCallHandler.post(autoCallRunnable);
        }
    }

    private void cancelAutoCall() {
        if (autoCallHandler != null && autoCallRunnable != null) {
            autoCallHandler.removeCallbacks(autoCallRunnable);
        }
        btnPositive.setText("CALL NOW");
    }

    private void setupPhoneStateListener() {
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                super.onCallStateChanged(state, phoneNumber);
                if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    isCallInProgress = true;
                } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                    if (isCallInProgress) {
                        isCallInProgress = false;
                        // Call ended, try next contact if available
                        handleCallEnded();
                    }
                }
            }
        };
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    private void handleCallEnded() {
        if (currentContactIndex < contactsList.size()) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                tvMessage.setText("Previous call ended. Calling next: " + contactsList.get(currentContactIndex).name);
                startSequentialCalling();
            }, 2000); // 2 second delay before next call
        } else {
            Toast.makeText(this, "All emergency contacts have been tried.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void loadEmergencyContacts() {
        android.content.SharedPreferences prefs = getSharedPreferences("SilentGuardPrefs", Context.MODE_PRIVATE);
        String contactsJson = prefs.getString("contacts", null);
        contactsList.clear();
        if (contactsJson != null) {
            try {
                org.json.JSONArray array = new org.json.JSONArray(contactsJson);
                for (int i = 0; i < array.length(); i++) {
                    org.json.JSONObject obj = array.getJSONObject(i);
                    contactsList.add(new SilentGuardService.Contact(
                        obj.getString("name"),
                        obj.getString("phone"),
                        obj.getString("relation")
                    ));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void initViews() {
        popupContainer = findViewById(R.id.popup_container);
        iconGlow = findViewById(R.id.icon_glow);
        ivWarning = findViewById(R.id.iv_warning);
        tvTitle = findViewById(R.id.tv_title);
        tvMessage = findViewById(R.id.tv_message);
        btnPositive = findViewById(R.id.btn_positive);
        btnNegative = findViewById(R.id.btn_negative);

        btnPositive.setOnClickListener(v -> {
            cancelAutoCall();
            startSequentialCalling();
        });
        btnNegative.setOnClickListener(v -> {
            cancelAutoCall();
            finish();
        });
    }

    private void provideHapticFeedback() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                v.vibrate(android.os.VibrationEffect.createWaveform(new long[]{0, 200, 100, 200}, -1));
            } else {
                v.vibrate(new long[]{0, 200, 100, 200}, -1);
            }
        }
    }

    private void startEntranceAnimation() {
        popupContainer.setAlpha(0f);
        popupContainer.setScaleX(0.8f);
        popupContainer.setScaleY(0.8f);

        popupContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(500)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
        
        // Pulse animation for glow
        ValueAnimator pulse = ValueAnimator.ofFloat(0.3f, 0.7f);
        pulse.setDuration(1200);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.addUpdateListener(anim -> iconGlow.setAlpha((float) anim.getAnimatedValue()));
        pulse.start();
    }

    private void startSequentialCalling() {
        if (contactsList.isEmpty()) {
            Toast.makeText(this, "No emergency contacts available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (currentContactIndex < contactsList.size()) {
            makeCall(contactsList.get(currentContactIndex).phone);
        } else {
            finish();
        }
    }

    private void makeCall(String phoneNumber) {
        try {
            Log.d("EmergencyResponse", "Initiating call to: " + phoneNumber);
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + phoneNumber));
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                currentContactIndex++; // Prepare next index for the next call
                startActivity(callIntent);
                
                // Monitor call state to trigger next call if this one isn't answered
                // We use a timeout fallback in case the listener doesn't catch all states
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (!isCallInProgress && currentContactIndex < contactsList.size()) {
                        Log.d("EmergencyResponse", "Call not picked up or ended quickly, trying next...");
                        handleCallEnded();
                    }
                }, 15000); // 15 second timeout per contact
            } else {
                Toast.makeText(this, "Call permission not granted", Toast.LENGTH_SHORT).show();
                finish();
            }
        } catch (Exception e) {
            Log.e("EmergencyResponse", "Error making call: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onBackPressed() {
        // Disable back button during emergency flow
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (telephonyManager != null && phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }
}
