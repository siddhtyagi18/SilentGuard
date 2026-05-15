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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private LinearLayout historyContainer;
    private SharedPreferences prefs;
    private static final String KEY_HISTORY = "alert_history";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        prefs = getSharedPreferences("SilentGuardPrefs", Context.MODE_PRIVATE);
        historyContainer = findViewById(R.id.history_container);

        View backButton = findViewById(R.id.btn_back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        loadHistory();
    }

    private void loadHistory() {
        historyContainer.removeAllViews();

        try {
            String historyJson = prefs.getString(KEY_HISTORY, "[]");
            JSONArray historyArray = new JSONArray(historyJson);

            if (historyArray.length() == 0) {
                addEmptyState();
                return;
            }

            for (int i = historyArray.length() - 1; i >= 0; i--) {
                JSONObject entry = historyArray.getJSONObject(i);
                addHistoryItem(entry);
            }

        } catch (Exception e) {
            e.printStackTrace();
            addEmptyState();
        }
    }

    private void addEmptyState() {
        View emptyView = LayoutInflater.from(this).inflate(R.layout.item_history, historyContainer, false);

        TextView tvTitle = emptyView.findViewById(R.id.event_title);
        TextView tvTime = emptyView.findViewById(R.id.event_time);
        TextView tvDesc = emptyView.findViewById(R.id.event_desc);

        tvTitle.setText("No alerts yet");
        tvTime.setText("");
        tvDesc.setText("When an emergency alert is triggered, it will appear here.");

        emptyView.setOnClickListener(null);
        historyContainer.addView(emptyView);
    }

    private void addHistoryItem(JSONObject entry) {
        try {
            View itemView = LayoutInflater.from(this).inflate(R.layout.item_history, historyContainer, false);

            TextView tvTitle = itemView.findViewById(R.id.event_title);
            TextView tvTime = itemView.findViewById(R.id.event_time);
            TextView tvDesc = itemView.findViewById(R.id.event_desc);

            String title = entry.optString("title", "Alert");
            long timestamp = entry.optLong("timestamp", 0);
            String desc = entry.optString("description", "Emergency alert triggered");

            tvTitle.setText(title);
            tvTime.setText(formatTimestamp(timestamp));
            tvDesc.setText(desc);

            itemView.setOnClickListener(v -> {
                Toast.makeText(this, "Details for: " + title, Toast.LENGTH_SHORT).show();
            });

            historyContainer.addView(itemView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp == 0) return "";

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public static void addHistoryEntry(Context context, String title, String description) {
        SharedPreferences prefs = context.getSharedPreferences("SilentGuardPrefs", Context.MODE_PRIVATE);
        try {
            String historyJson = prefs.getString(KEY_HISTORY, "[]");
            JSONArray historyArray = new JSONArray(historyJson);

            JSONObject newEntry = new JSONObject();
            newEntry.put("title", title);
            newEntry.put("timestamp", System.currentTimeMillis());
            newEntry.put("description", description);

            historyArray.put(newEntry);

            if (historyArray.length() > 50) {
                historyArray.remove(0);
            }

            prefs.edit().putString(KEY_HISTORY, historyArray.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
