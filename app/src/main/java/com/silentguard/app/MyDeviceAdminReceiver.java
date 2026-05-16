package com.silentguard.app;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;
import androidx.annotation.NonNull;

public class MyDeviceAdminReceiver extends DeviceAdminReceiver {

    private static final String PREFS_NAME = "SilentGuardPrefs";
    private static final String KEY_FAILED_ATTEMPTS = "failed_password_attempts";
    private static final String KEY_LAST_FAILED_TIME = "last_failed_time";
    private static final long ATTEMPT_TIMEOUT = 30000; // 30 seconds

    @Override
    public void onPasswordFailed(@NonNull Context context, @NonNull Intent intent) {
        super.onPasswordFailed(context, intent);
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("switch_pass", false)) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        long lastFailedTime = prefs.getLong(KEY_LAST_FAILED_TIME, 0);
        int failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0);
        int triggerLimit = prefs.getInt("pass_trigger_limit", 3); // Default to 3 attempts as per user request

        if (currentTime - lastFailedTime > ATTEMPT_TIMEOUT) {
            failedAttempts = 0;
        }

        failedAttempts++;
        lastFailedTime = currentTime;

        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, failedAttempts)
            .putLong(KEY_LAST_FAILED_TIME, lastFailedTime)
            .apply();

        if (failedAttempts >= triggerLimit) {
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .apply();
            
            HistoryActivity.addHistoryEntry(context, "Wrong Password Detected", "Intruder alert triggered");
            
            Intent serviceIntent = new Intent(context, SilentGuardService.class);
            serviceIntent.setAction("ACTION_WRONG_PASSWORD");
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }

    @Override
    public void onPasswordSucceeded(@NonNull Context context, @NonNull Intent intent) {
        super.onPasswordSucceeded(context, intent);
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .apply();
    }

    @Override
    public void onEnabled(@NonNull Context context, @NonNull Intent intent) {
        super.onEnabled(context, intent);
        Toast.makeText(context, "Device Admin Enabled", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisabled(@NonNull Context context, @NonNull Intent intent) {
        super.onDisabled(context, intent);
        Toast.makeText(context, "Device Admin Disabled", Toast.LENGTH_SHORT).show();
    }
}