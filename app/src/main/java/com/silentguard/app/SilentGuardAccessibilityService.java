package com.silentguard.app;

import android.accessibilityservice.AccessibilityService;
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
        HistoryActivity.addHistoryEntry(this, "Volume Trigger Activated", "Location shared with emergency contacts");
        
        loadEmergencyContacts();
        if (contactsList.isEmpty()) {
            Log.e(TAG, "No emergency contacts found!");
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                sendAlertWithLocation(location);
            } else {
                requestFreshLocation();
            }
        });
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
