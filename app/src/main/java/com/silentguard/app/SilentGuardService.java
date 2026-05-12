package com.silentguard.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telephony.SmsManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;

public class SilentGuardService extends Service {

    private static final String TAG = "SilentGuardService";
    private static final String CHANNEL_ID = "SilentGuardChannel";
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private FusedLocationProviderClient fusedLocationClient;
    private String emergencyPhone, emergencyEmail;
    private DatabaseReference mDatabase;
    private boolean isListening = false;

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        loadEmergencyContacts();
        createNotificationChannel();
        initSpeechRecognizer();
    }

    private void loadEmergencyContacts() {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId != null) {
            mDatabase = FirebaseDatabase.getInstance().getReference("Users")
                    .child(userId).child("EmergencyContacts");
            mDatabase.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        emergencyPhone = snapshot.child("phone").getValue(String.class);
                        emergencyEmail = snapshot.child("email").getValue(String.class);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
        }
    }

    private void initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) { Log.d(TAG, "Ready for speech"); }
            @Override
            public void onBeginningOfSpeech() {}
            @Override
            public void onRmsChanged(float rmsdB) {}
            @Override
            public void onBufferReceived(byte[] buffer) {}
            @Override
            public void onEndOfSpeech() {}
            @Override
            public void onError(int error) {
                Log.e(TAG, "Speech Error: " + error);
                // Restart listening on error
                if (isListening) {
                    speechRecognizer.startListening(recognizerIntent);
                }
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null) {
                    for (String match : matches) {
                        if (match.toLowerCase().contains("help me")) {
                            triggerEmergencyAlert();
                            break;
                        }
                    }
                }
                // Continue listening
                if (isListening) {
                    speechRecognizer.startListening(recognizerIntent);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}
            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void triggerEmergencyAlert() {
        if (emergencyPhone == null) return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            String locationLink = "https://www.google.com/maps/search/?api=1&query=";
            if (location != null) {
                locationLink += location.getLatitude() + "," + location.getLongitude();
            } else {
                locationLink += "Unknown+Location";
            }

            String message = "EMERGENCY! I need help. My live location: " + locationLink;
            
            // Send SMS
            try {
                SmsManager.getDefault().sendTextMessage(emergencyPhone, null, message, null, null);
            } catch (Exception e) {
                Log.e(TAG, "SMS failed", e);
            }

            // Note: Email in background requires a custom SMTP implementation or Firebase Trigger.
            // For this implementation, we focus on SMS as the primary background alert.
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Silent Guard Protection",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Silent Guard")
                .setContentText("Silent Guard is actively protecting you")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
                .build();

        startForeground(1, notification);
        
        isListening = true;
        speechRecognizer.startListening(recognizerIntent);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isListening = false;
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}