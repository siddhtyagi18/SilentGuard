package com.silentguard.app;

import android.Manifest;
import android.app.PendingIntent;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.location.Location;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;
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
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName componentName;
    
    // Siren Logic
    private MediaPlayer mediaPlayer;
    private boolean isSirenPlaying = false;
    
    // Volume Trigger Logic
    private int volumePressCount = 0;
    private long lastVolumePressTime = 0;
    private ContentObserver volumeObserver;

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
        Log.d(TAG, "Service created");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        cameraExecutor = Executors.newSingleThreadExecutor();
        prefs = getSharedPreferences("SilentGuardPrefs", Context.MODE_PRIVATE);
        loadEmergencyContacts();
        createNotificationChannel();
        initSpeechRecognizer();
        
        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        componentName = new ComponentName(this, MyDeviceAdminReceiver.class);
        
        setupVolumeObserver();
    }

    private void setupVolumeObserver() {
        volumeObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                if (prefs.getBoolean("switch_volume", false)) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastVolumePressTime < 1500) {
                        volumePressCount++;
                    } else {
                        volumePressCount = 1;
                    }
                    lastVolumePressTime = currentTime;

                    if (volumePressCount == 3) {
                        volumePressCount = 0;
                        Log.d(TAG, "3x Volume Trigger: Sending SOS...");
                        triggerEmergencyAlert();
                        
                        if (prefs.getBoolean("vol_auto_call", false)) {
                            Log.d(TAG, "Auto Call enabled: Launching Call Confirmation...");
                            
                            // Check if contacts exist before launching popup
                            loadEmergencyContacts();
                            if (contactsList.isEmpty()) {
                                new Handler(Looper.getMainLooper()).post(() -> 
                                    Toast.makeText(SilentGuardService.this, "No emergency contacts available", Toast.LENGTH_SHORT).show()
                                );
                            } else {
                                Intent responseIntent = new Intent(SilentGuardService.this, EmergencyResponseActivity.class);
                                responseIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                startActivity(responseIntent);
                            }
                        }
                    }
                }
            }
        };
        getContentResolver().registerContentObserver(
                Settings.System.CONTENT_URI, 
                true, 
                volumeObserver
        );
    }
    
    private void handleVolumeSOS() {
        Log.d(TAG, "Handling Volume SOS Actions...");
        
        // 1. Basic SMS/Location Alert
        triggerEmergencyAlert();

        // 2. Launch Interactive Response Flow
        Intent responseIntent = new Intent(this, EmergencyResponseActivity.class);
        responseIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(responseIntent);
    }

    private void playSiren() {
        if (isSirenPlaying) return;
        
        try {
            Uri sirenUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (sirenUri == null) {
                sirenUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }
            
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, sirenUri);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
            isSirenPlaying = true;
            
            updateNotificationWithSiren();
            
            new Handler(Looper.getMainLooper()).post(() -> 
                Toast.makeText(this, "EMERGENCY SIREN ACTIVATED!", Toast.LENGTH_LONG).show()
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to play siren: " + e.getMessage());
        }
    }

    private void updateNotificationWithSiren() {
        Intent stopIntent = new Intent(this, SilentGuardService.class);
        stopIntent.setAction("ACTION_STOP_SIREN");
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("EMERGENCY SIREN PLAYING")
                .setContentText("Click to stop the alert sound")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP SIREN", stopPendingIntent)
                .build();

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(1, notification);
        }
    }

    private void stopSiren() {
        if (mediaPlayer != null && isSirenPlaying) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
            isSirenPlaying = false;
            
            // Reset to normal notification
            resetNotification();
        }
    }

    private void resetNotification() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Silent Guard")
                .setContentText("Silent Guard is actively protecting you")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
                .build();

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(1, notification);
        }
    }

    private void autoCallEmergencyContact() {
        loadEmergencyContacts();
        if (contactsList.isEmpty()) {
            Log.e(TAG, "No contacts to call!");
            return;
        }

        // Call primary (first) contact
        Contact primaryContact = contactsList.get(0);
        makeCall(primaryContact.phone);
    }

    private void makeCall(String phoneNumber) {
        try {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + phoneNumber));
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                startActivity(callIntent);
                Log.d(TAG, "Calling: " + phoneNumber);
            } else {
                Log.e(TAG, "CALL_PHONE permission NOT granted!");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to make call: " + e.getMessage());
        }
    }

    private void lockScreen() {
        try {
            if (devicePolicyManager == null) {
                Log.e(TAG, "DevicePolicyManager is null!");
                return;
            }
            if (componentName == null) {
                Log.e(TAG, "ComponentName is null!");
                return;
            }
            if (devicePolicyManager.isAdminActive(componentName)) {
                Log.d(TAG, "Attempting to lock screen");
                devicePolicyManager.lockNow();
                Log.d(TAG, "Screen locked successfully!");
                new android.os.Handler(getMainLooper()).post(() -> 
                    Toast.makeText(SilentGuardService.this, "Screen locked!", Toast.LENGTH_SHORT).show()
                );
            } else {
                Log.e(TAG, "Device admin NOT enabled!");
                new android.os.Handler(getMainLooper()).post(() -> 
                    Toast.makeText(SilentGuardService.this, "Enable Device Admin first!", Toast.LENGTH_LONG).show()
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "Error locking screen: " + e.getMessage(), e);
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
        Log.d(TAG, "Initializing speech recognizer");
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) { Log.d(TAG, "Ready for speech"); }
            @Override
            public void onBeginningOfSpeech() { Log.d(TAG, "Beginning of speech"); }
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
                // Log the error but don't show to user if we are in the background
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
                        String lowerMatch = match.toLowerCase().trim();
                        Log.d(TAG, "Heard: " + lowerMatch);
                        
                        if (lowerMatch.contains("help") || lowerMatch.contains("bachao")) {
                            Log.d(TAG, "Help command detected!");
                            handleHelpCommand();
                            return;
                        } else if (lowerMatch.contains("pakdo") || lowerMatch.contains("pakado") || lowerMatch.contains("catch")) {
                            Log.d(TAG, "Pakdo command detected!");
                            handlePakdoCommand();
                            return;
                        } else if (lowerMatch.contains("give me my phone") || lowerMatch.contains("phone de")) {
                            Log.d(TAG, "Give me my phone command detected!");
                            handleGivePhoneCommand();
                            return;
                        }
                        
                        // Check for lock screen commands
                        if (lowerMatch.contains("display off") || lowerMatch.contains("lock screen") || lowerMatch.contains("screen off")) {
                            Log.d(TAG, "LOCK SCREEN COMMAND DETECTED!");
                            boolean isEnabled = prefs.getBoolean("switch_display_off_voice", true);
                            if (isEnabled) {
                                lockScreen();
                            } else {
                                Log.d(TAG, "Lock screen command disabled in settings");
                            }
                            return;
                        }
                    }
                }
                if (isListening) {
                    restartListening();
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null) {
                    for (String match : matches) {
                        String lowerMatch = match.toLowerCase();
                        if (lowerMatch.contains("help") || lowerMatch.contains("bachao")) {
                            Log.d(TAG, "Help detected in partial results!");
                            handleHelpCommand();
                            return;
                        } else if (lowerMatch.contains("pakdo") || lowerMatch.contains("pakado") || lowerMatch.contains("catch")) {
                            Log.d(TAG, "Pakdo detected in partial results!");
                            handlePakdoCommand();
                            return;
                        } else if (lowerMatch.contains("give me my phone") || lowerMatch.contains("phone de")) {
                            Log.d(TAG, "Give me my phone detected in partial results!");
                            handleGivePhoneCommand();
                            return;
                        }
                    }
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void restartListening() {
        try {
            Log.d(TAG, "Restarting listening...");
            speechRecognizer.cancel();
            Thread.sleep(300);
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
            if (location != null) {
                sendAlertWithLocation(location);
            } else {
                // Try requesting a fresh location
                Log.d(TAG, "Last location null, requesting fresh location...");
                requestFreshLocation();
            }
        });
    }

    private void requestFreshLocation() {
        try {
            com.google.android.gms.location.LocationRequest locationRequest = com.google.android.gms.location.LocationRequest.create()
                    .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
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
            }, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission missing", e);
            sendAlertWithLocation(null);
        }
    }

    private void sendAlertWithLocation(android.location.Location location) {
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
                new Handler(Looper.getMainLooper()).post(() -> 
                    Toast.makeText(SilentGuardService.this, "Successfully Sent Alert Msg", Toast.LENGTH_LONG).show()
                );
            } catch (Exception e) {
                Log.e(TAG, "Failed to send SMS to " + contact.name, e);
            }
        }
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

    private void handleHelpCommand() {
        Log.d(TAG, "Executing Help Command...");
        lockScreen();
        triggerEmergencyAlert();
    }

    private void handlePakdoCommand() {
        Log.d(TAG, "Executing Pakdo Command...");
        triggerEmergencyAlert();
    }

    private void handleGivePhoneCommand() {
        Log.d(TAG, "Executing Give me my phone Command...");
        triggerEmergencyAlert();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand called");
        loadEmergencyContacts();
        
        if (intent != null) {
            if ("ACTION_WRONG_PASSWORD".equals(intent.getAction())) {
                takeSelfieAndSendSOS();
            } else if ("ACTION_VOLUME_SOS".equals(intent.getAction())) {
                handleVolumeSOS();
            } else if ("ACTION_STOP_SIREN".equals(intent.getAction())) {
                stopSiren();
            }
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Silent Guard")
                .setContentText("Silent Guard is actively protecting you")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
                .build();

        startForeground(1, notification);
        
        isListening = true;
        Log.d(TAG, "Starting speech recognition");
        speechRecognizer.startListening(recognizerIntent);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isListening = false;
        stopSiren();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (volumeObserver != null) {
            getContentResolver().unregisterContentObserver(volumeObserver);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
