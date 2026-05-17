package com.silentguard.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationCenterActivity extends AppCompatActivity {

    private LinearLayout notificationsContainer;
    private SharedPreferences prefs;
    private static final String KEY_APP_NOTIFICATIONS = "app_notifications";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_center);

        prefs = getSharedPreferences("SilentGuardPrefs", Context.MODE_PRIVATE);
        notificationsContainer = findViewById(R.id.notifications_container);

        View backButton = findViewById(R.id.btn_back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        View clearAllButton = findViewById(R.id.btn_clear_all);
        if (clearAllButton != null) {
            clearAllButton.setOnClickListener(v -> clearAllNotifications());
        }

        loadNotifications();
    }

    private void loadNotifications() {
        notificationsContainer.removeAllViews();

        try {
            String notificationsJson = prefs.getString(KEY_APP_NOTIFICATIONS, "[]");
            JSONArray notificationsArray = new JSONArray(notificationsJson);

            if (notificationsArray.length() == 0) {
                addEmptyState();
                return;
            }

            for (int i = notificationsArray.length() - 1; i >= 0; i--) {
                JSONObject notification = notificationsArray.getJSONObject(i);
                addNotificationItem(notification);
            }

        } catch (Exception e) {
            e.printStackTrace();
            addEmptyState();
        }
    }

    private void addEmptyState() {
        View emptyView = LayoutInflater.from(this).inflate(R.layout.item_notification, notificationsContainer, false);

        TextView tvTitle = emptyView.findViewById(R.id.notification_title);
        TextView tvTime = emptyView.findViewById(R.id.notification_time);
        TextView tvDesc = emptyView.findViewById(R.id.notification_desc);

        tvTitle.setText("No notifications yet");
        tvTime.setText("");
        tvDesc.setText("When Silent Guard detects something, you'll see it here.");

        emptyView.setOnClickListener(null);
        notificationsContainer.addView(emptyView);
    }

    private void addNotificationItem(JSONObject notification) {
        try {
            View itemView = LayoutInflater.from(this).inflate(R.layout.item_notification, notificationsContainer, false);

            TextView tvTitle = itemView.findViewById(R.id.notification_title);
            TextView tvTime = itemView.findViewById(R.id.notification_time);
            TextView tvDesc = itemView.findViewById(R.id.notification_desc);

            String title = notification.optString("title", "Notification");
            String desc = notification.optString("description", "");
            long timestamp = notification.optLong("timestamp", System.currentTimeMillis());

            tvTitle.setText(title);
            tvDesc.setText(desc);
            tvTime.setText(formatTimestamp(timestamp));

            notificationsContainer.addView(itemView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    private void clearAllNotifications() {
        prefs.edit().putString(KEY_APP_NOTIFICATIONS, "[]").apply();
        loadNotifications();
        Toast.makeText(this, "All notifications cleared", Toast.LENGTH_SHORT).show();
    }

    public static void addNotification(Context context, String title, String description) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("SilentGuardPrefs", Context.MODE_PRIVATE);
            String notificationsJson = prefs.getString(KEY_APP_NOTIFICATIONS, "[]");
            JSONArray notificationsArray = new JSONArray(notificationsJson);

            JSONObject newNotification = new JSONObject();
            newNotification.put("title", title);
            newNotification.put("description", description);
            newNotification.put("timestamp", System.currentTimeMillis());

            notificationsArray.put(newNotification);

            if (notificationsArray.length() > 50) {
                notificationsArray.remove(0);
            }

            prefs.edit().putString(KEY_APP_NOTIFICATIONS, notificationsArray.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
