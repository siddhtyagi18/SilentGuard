package com.silentguard.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SilentGuardService extends LifecycleService {

    private static final String TAG = "SilentGuardService";
    private static final String CHANNEL_ID = "SilentGuardChannel";
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private FusedLocationProviderClient fusedLocationClient;
    private SharedPreferences prefs;
    private List<Contact> contactsList = new ArrayList<>();
    private boolean isListening = false;
    private ExecutorService cameraExecutor;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName componentName;

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
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        cameraExecutor = Executors.newSingleThreadExecutor();
        prefs = getSharedPreferences("SilentGuardPrefs", Context.MODE_PRIVATE);
        loadEmergencyContacts();
        createNotificationChannel();
        initSpeechRecognizer();
        
        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        componentName = new ComponentName(this, MyDeviceAdminReceiver.class);
    }
    
    private void lockScreen() {
        if (devicePolicyManager.isAdminActive(componentName)) {
            devicePolicyManager.lockNow();
            Log.d(TAG, "Screen locked via voice command");
        } else {
            Log.e(TAG, "Device admin not enabled - cannot lock screen");
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
            public void onEndOfSpeech() {
                Log.d(TAG, "Speech ended");
                if (isListening) {
                    restartListening();
                }
            }
            @Override
            public void onError(int error) {
                Log.e(TAG, "Speech Error: " + error);
                if (isListening) {
                    restartListening();
                }
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null) {
                    for (String match : matches) {
                        String lowerMatch = match.toLowerCase();
                        Log.d(TAG, "Heard: " + lowerMatch);
                        if (lowerMatch.contains("help me") || lowerMatch.contains("pakdo") || lowerMatch.contains("give me my phone")) {
                            Log.d(TAG, "Emergency command detected!");
                            triggerEmergencyAlert();
                            return;
                        }
                        if (lowerMatch.contains("display off") || lowerMatch.contains("lock screen") || lowerMatch.contains("screen off")) {
                            Log.d(TAG, "Lock screen command detected!");
                            lockScreen();
                            return;
                        }
                    }
                }
                if (isListening) {
                    restartListening();
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}
            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void restartListening() {
        try {
            speechRecognizer.cancel();
            Thread.sleep(200);
            speechRecognizer.startListening(recognizerIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to restart listening", e);
        }
    }

    private void triggerEmergencyAlert() {
        loadEmergencyContacts();
        if (contactsList.isEmpty()) {
            Log.e(TAG, "No emergency contacts found!");
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
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
        });
    }

    private void takeSelfieAndSendSOS() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                
                ImageCapture imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture);
                
                File photoFile = new File(getExternalFilesDir(null), "selfie_" + System.currentTimeMillis() + ".jpg");
                ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

                imageCapture.takePicture(outputOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        uploadSelfieAndSendSOS(photoFile);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Photo capture failed: " + exception.getMessage());
                        triggerEmergencyAlert();
                    }
                });

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void uploadSelfieAndSendSOS(File photoFile) {
        String userId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("selfies").child(userId).child(photoFile.getName());

        storageRef.putFile(android.net.Uri.fromFile(photoFile))
                .addOnSuccessListener(taskSnapshot -> {
                    storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        sendSOSWithImage(uri.toString());
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Upload failed: " + e.getMessage());
                    triggerEmergencyAlert();
                });
    }

    private void sendSOSWithImage(String imageUrl) {
        loadEmergencyContacts();
        if (contactsList.isEmpty()) return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            String locationLink = "https://www.google.com/maps/search/?api=1&query=";
            if (location != null) {
                locationLink += location.getLatitude() + "," + location.getLongitude();
            } else {
                locationLink += "Unknown+Location";
            }

            String message = "WRONG PASSWORD DETECTED! Intruder photo: " + imageUrl + " | Location: " + locationLink;
            
            for (Contact contact : contactsList) {
                try {
                    SmsManager.getDefault().sendTextMessage(contact.phone, null, message, null, null);
                    Log.d(TAG, "SMS sent to " + contact.name + " at " + contact.phone);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to send SMS to " + contact.name, e);
                }
            }
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
        loadEmergencyContacts();
        
        if (intent != null && "ACTION_WRONG_PASSWORD".equals(intent.getAction())) {
            takeSelfieAndSendSOS();
        }

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
