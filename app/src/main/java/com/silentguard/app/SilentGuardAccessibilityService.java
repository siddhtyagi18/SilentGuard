package com.silentguard.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class SilentGuardAccessibilityService extends AccessibilityService {

    private static final String TAG = "SilentGuardAccessibility";
    private static final String WAKE_LOCK_TAG = "SilentGuard:AccessibilityWakeLock";
    private int volumePressCount = 0;
    private long lastVolumePressTime = 0;
    private SharedPreferences prefs;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("SilentGuardPrefs", MODE_PRIVATE);
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
                        Log.d(TAG, "3x Volume Trigger (Accessibility): Detected! Forwarding to SilentGuardService...");
                        forwardToSilentGuardService();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in accessibility service key event: " + e.getMessage(), e);
        }
        return false;
    }

    private void forwardToSilentGuardService() {
        Log.d(TAG, "Forwarding ACTION_VOLUME_SOS to SilentGuardService");
        
        Intent intent = new Intent(this, SilentGuardService.class);
        intent.setAction("ACTION_VOLUME_SOS");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        
        Log.d(TAG, "ACTION_VOLUME_SOS intent sent to SilentGuardService");
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
