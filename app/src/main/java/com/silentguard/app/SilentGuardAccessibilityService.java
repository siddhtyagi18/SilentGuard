package com.silentguard.app;

import android.accessibilityservice.AccessibilityService;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.PowerManager;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.NonNull;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class SilentGuardAccessibilityService extends AccessibilityService {

    private static final String TAG = "SilentGuardAccessibility";
    private static final String WAKE_LOCK_TAG = "SilentGuard:AccessibilityWakeLock";
    private int volumePressCount = 0;
    private long lastVolumePressTime = 0;
    private SharedPreferences prefs;
    private FusedLocationProviderClient fusedLocationClient;
    private List<Contact> contactsList = new ArrayList<>();
    private PowerManager.WakeLock wakeLock;

    static class Contact {
        String name;
        String phone;
        String relation;

        Contact(String name, String phone, String relation) {
            this.name = name;
            this.phone = phone;
            this.relation = relation;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("SilentGuardPrefs", MODE_PRIVATE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        acquireWakeLock();
        Log.d(TAG, "Accessibility Service created");
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            );
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(10 * 60 * 1000L); // 10 minutes timeout
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not needed for key detection
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        try {
            if (prefs.getBoolean("switch_volume", false)) {
                int keyCode = event.getKeyCode();
                if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)
                        && event.getAction() == KeyEvent.ACTION_DOWN) {

                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastVolumePressTime < 1500) {
                        volumePressCount++;
                    } else {
                        volumePressCount = 1;
                    }
                    lastVolumePressTime = currentTime;

                    if (volumePressCount == 3) {
                        volumePressCount = 0;
                        Log.d(TAG, "3x Volume Trigger (Accessibility): Sending SOS...");
                        
                        // Show a toast immediately to confirm detection
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                            android.widget.Toast.makeText(SilentGuardAccessibilityService.this, "SOS Triggered! Sending Alert...", android.widget.Toast.LENGTH_SHORT).show()
                        );

                        // 1. First send SMS and Location (Popup will be triggered inside sendAlertWithLocation)
                        triggerEmergencyAlert();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in accessibility service key event: " + e.getMessage(), e);
        }
        return false;
    }

    private void triggerEmergencyAlert() {
        boolean sendSms = prefs.getBoolean("vol_send_sms", true);
        boolean shareLoc = prefs.getBoolean("vol_share_loc", true);

        if (!sendSms && !shareLoc) {
            Log.d(TAG, "SOS Actions disabled in settings (SMS and Location)");
            return;
        }

        HistoryActivity.addHistoryEntry(this, "Volume Trigger Activated", "SOS alert initiated via Accessibility Service");
        
        loadEmergencyContacts();
        if (contactsList.isEmpty()) {
            Log.e(TAG, "No emergency contacts found!");
            return;
        }

        if (shareLoc) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    sendAlertWithLocation(location);
                } else {
                    requestFreshLocation();
                }
            });
        } else if (sendSms) {
            sendAlertWithLocation(null);
        }
    }

    private void requestFreshLocation() {
        try {
            com.google.android.gms.location.LocationRequest locationRequest = com.google.android.gms.location.LocationRequest.create()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setInterval(1000)
                    .setNumUpdates(1);

            fusedLocationClient.requestLocationUpdates(locationRequest, new com.google.android.gms.location.LocationCallback() {
                @Override
                public void onLocationResult(@NonNull com.google.android.gms.location.LocationResult locationResult) {
                    Location location = locationResult.getLastLocation();
                    if (location != null) {
                        sendAlertWithLocation(location);
                    } else {
                        sendAlertWithLocation(null);
                    }
                    fusedLocationClient.removeLocationUpdates(this);
                }
            }, android.os.Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission missing", e);
            sendAlertWithLocation(null);
        }
    }

    private void sendAlertWithLocation(Location location) {
        String locationLink = "https://www.google.com/maps/search/?api=1&query=";
        if (location != null) {
            locationLink += location.getLatitude() + "," + location.getLongitude();
        } else {
            locationLink += "Unknown+Location";
        }

        String message = "EMERGENCY! I need help. My live location: " + locationLink;
        
        for (Contact contact : contactsList) {
            try {
                SmsManager.getDefault().sendTextMessage(contact.phone, null, message, null, null);
                Log.d(TAG, "SMS sent to " + contact.name + " at " + contact.phone);
            } catch (Exception e) {
                Log.e(TAG, "Failed to send SMS to " + contact.name, e);
            }
        }

        // 2. Launch Call Popup AFTER sending alerts
        if (prefs.getBoolean("vol_auto_call", true)) {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::showEmergencyPopup, 500);
        }
    }

    private void loadEmergencyContacts() {
        contactsList.clear();
        String contactsJson = prefs.getString("contacts", null);
        if (contactsJson != null) {
            try {
                JSONArray jsonArray = new JSONArray(contactsJson);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    String name = jsonObject.getString("name");
                    String phone = jsonObject.getString("phone");
                    String relation = jsonObject.getString("relation");
                    contactsList.add(new Contact(name, phone, relation));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void showEmergencyPopup() {
        Log.d(TAG, "Launching EmergencyResponseActivity popup via Accessibility...");
        
        Intent intent = new Intent(this, EmergencyResponseActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK 
                | Intent.FLAG_ACTIVITY_CLEAR_TOP 
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        
        // Notification channel ID should match SilentGuardService
        String CHANNEL_ID = "SilentGuardChannel";
        int NOTIFICATION_ID = 1001;

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(this, 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        androidx.core.app.NotificationCompat.Builder notificationBuilder =
                new androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("Emergency SOS Triggered!")
                        .setContentText("Tap to manage emergency call")
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                        .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
                        .setFullScreenIntent(fullScreenPendingIntent, true)
                        .setAutoCancel(true)
                        .setOngoing(true);

        android.app.NotificationManager notificationManager = (android.app.NotificationManager) getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }

        try {
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Accessibility startActivity failed: " + e.getMessage());
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted");
        releaseWakeLock();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseWakeLock();
    }
}
