package com.silentguard.app;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class FakeCallActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fake_call);

        TextView tvCallerName = findViewById(R.id.tv_caller_name);
        tvCallerName.setText("Dad"); // Default fallback

        findViewById(R.id.btn_decline).setOnClickListener(v -> finish());
        findViewById(R.id.btn_accept).setOnClickListener(v -> finish());
    }
}
