package com.silentguard.app;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class HistoryActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        View backButton = findViewById(R.id.btn_back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        setupHistoryItems();
    }

    private void setupHistoryItems() {
        setupHistoryItem(R.id.item1, "Alert Sent", "10:45 AM", "Emergency SOS triggered via voice command");
        setupHistoryItem(R.id.item2, "Location Shared", "Yesterday", "Live location shared with 3 contacts");
        setupHistoryItem(R.id.item3, "System Check", "2 days ago", "All security modules verified and active");
        setupHistoryItem(R.id.item4, "Login Alert", "May 08", "New login detected from Pixel 6 Pro");
    }

    private void setupHistoryItem(int id, String title, String time, String desc) {
        View item = findViewById(id);
        if (item != null) {
            TextView tvTitle = item.findViewById(R.id.event_title);
            TextView tvTime = item.findViewById(R.id.event_time);
            TextView tvDesc = item.findViewById(R.id.event_desc);
            
            if (tvTitle != null) tvTitle.setText(title);
            if (tvTime != null) tvTime.setText(time);
            if (tvDesc != null) tvDesc.setText(desc);
            
            item.setOnClickListener(v -> {
                Toast.makeText(this, "Details for: " + title, Toast.LENGTH_SHORT).show();
            });
        }
    }
}
