package com.silentguard.app;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;
import androidx.annotation.NonNull;

public class MyDeviceAdminReceiver extends DeviceAdminReceiver {

    @Override
    public void onPasswordFailed(@NonNull Context context, @NonNull Intent intent) {
        super.onPasswordFailed(context, intent);
        
        SharedPreferences prefs = context.getSharedPreferences("SilentGuardPrefs", Context.MODE_PRIVATE);
        if (prefs.getBoolean("switch_pass", false)) {
            // Trigger the background service to handle selfie capture and SOS
            Intent serviceIntent = new Intent(context, SilentGuardService.class);
            serviceIntent.setAction("ACTION_WRONG_PASSWORD");
            context.startService(serviceIntent);
        }
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