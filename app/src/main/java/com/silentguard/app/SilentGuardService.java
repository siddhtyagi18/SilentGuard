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
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
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
    private static final String ACCESSIBILITY_CHANNEL_ID = "SilentGuardAccessibility";
    private static final int NOTIFICATION_ID = 1001;
    private static final int ACCESSIBILITY_NOTIFICATION_ID = 1002;
    private static final String WAKE_LOCK_TAG = "SilentGuard:WakeLock";
    private static final String EMERGENCY_WAKE_LOCK_TAG = "SilentGuard:EmergencyWakeLock";
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
    private PowerManager.WakeLock wakeLock;
    private PowerManager.WakeLock emergencyWakeLock;
    private AudioManager audioManager;
    private int lastVolume = -1;
    private Handler accessibilityCheckHandler = new Handler(Looper.getMainLooper());
    private Runnable accessibilityCheckRunnable;

    
    // Siren Logic
    private MediaPlayer mediaPlayer;
    private boolean isSirenPlaying = false;
    
    // Volume Trigger Logic
    private int volumePressCount = 0;
    private long lastVolumePressTime = 0;
    private ContentObserver volumeObserver;
    
    // Emergency Overlay
    private View overlayView = null;
    private WindowManager windowManager = null;
    private Handler overlayHandler = null;
    private Runnable overlayCountdownRunnable = null;
    private int overlayCountdownSeconds = 10;
    private boolean isOverlayShowing = false;

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
        createAccessibilityNotificationChannel();
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Silent Guard")
                .setContentText("Silent Guard is actively protecting you")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
                .build();

        startForeground(1, notification);
        
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        overlayHandler = new Handler(Looper.getMainLooper());
        
        boolean isVoiceEnabled = prefs.getBoolean("switch_voice", true);
        if (isVoiceEnabled) {
            initSpeechRecognizer();
            startListening();
        }
        
        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        componentName = new ComponentName(this, MyDeviceAdminReceiver.class);
        
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        lastVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING);
        
        acquireWakeLock();
        setupVolumeObserver();
        startAccessibilityServiceCheck();
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            );
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(30 * 60 * 1000L); // 30 minutes timeout
        }
    }

    private void setupVolumeObserver() {
        boolean isVolumeEnabled = prefs.getBoolean("switch_volume", false);
        if (!isVolumeEnabled) {
            return;
        }
        
        volumeObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                try {
                    if (prefs.getBoolean("switch_volume", false)) {
                        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING);
                        
                        if (currentVolume != lastVolume) {
                            lastVolume = currentVolume;
                            
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
                                handleVolumeSOS();
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in volume observer: " + e.getMessage(), e);
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
        Log.d(TAG, "=== handleVolumeSOS() STARTED ===");
        
        // 0. WAKE SCREEN FIRST - even when screen OFF
        Log.d(TAG, "Step 0: Waking up device screen...");
        acquireEmergencyWakeLock();
        
        // 1. Basic SMS/Location Alert (send FIRST)
        Log.d(TAG, "Step 1: Sending SOS SMS + Location...");
        triggerEmergencyAlert();

        // 2. Show SYSTEM OVERLAY popup
        Log.d(TAG, "Step 2: Showing emergency overlay popup...");
        showEmergencyOverlay();
        
        Log.d(TAG, "=== handleVolumeSOS() COMPLETED ===");
    }
    
    private void acquireEmergencyWakeLock() {
        Log.d(TAG, "Acquiring EMERGENCY WAKE LOCK to wake screen...");
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                emergencyWakeLock = powerManager.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK |
                    PowerManager.ACQUIRE_CAUSES_WAKEUP |
                    PowerManager.ON_AFTER_RELEASE,
                    EMERGENCY_WAKE_LOCK_TAG
                );
                emergencyWakeLock.setReferenceCounted(false);
                emergencyWakeLock.acquire(10000);
                Log.d(TAG, "Emergency WakeLock ACQUIRED - screen should wake!");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to acquire emergency wake lock: " + e.getMessage(), e);
        }
    }
    
    private void releaseEmergencyWakeLock() {
        try {
            if (emergencyWakeLock != null && emergencyWakeLock.isHeld()) {
                emergencyWakeLock.release();
                emergencyWakeLock = null;
                Log.d(TAG, "Emergency WakeLock RELEASED safely");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to release emergency wake lock: " + e.getMessage(), e);
        }
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

    private static final int RESTART_DELAY_MS = 1000; // 1 second - FAST restart
    private boolean isRestartPending = false;
    private Handler speechRestartHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingRestartRunnable;

    private void initSpeechRecognizer() {
        Log.d(TAG, "Initializing speech recognizer with HIGH RESPONSIVENESS settings");
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true); // Enable partial results for faster detection
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L); // 1 second minimum
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L); // 1.5 seconds silence
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L); // 0.8 seconds possible silence
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10); // More results for better detection

        speechRecognizer.setRecognitionListener(new SpeechRecognitionListener());
    }

    private class SpeechRecognitionListener implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "Ready for speech");
        }

        @Override
        public void onBeginningOfSpeech() {
            Log.d(TAG, "Beginning of speech");
        }

        @Override
        public void onRmsChanged(float rmsdB) {}

        @Override
        public void onBufferReceived(byte[] buffer) {}

        @Override
        public void onEndOfSpeech() {
            Log.d(TAG, "Speech ended - scheduling delayed restart");
            if (isListening) {
                scheduleDelayedRestart();
            }
        }

        @Override
        public void onError(int error) {
            Log.e(TAG, "Speech Error: " + error);
            
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || 
                error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                Log.d(TAG, "Common error - using normal delay");
                if (isListening) {
                    scheduleDelayedRestart();
                }
                return;
            }

            if (isListening) {
                scheduleDelayedRestart();
            }
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            boolean commandFound = false;
            
            if (matches != null) {
                for (String match : matches) {
                    String lowerMatch = match.toLowerCase().trim();
                    Log.d(TAG, "Heard: " + lowerMatch);
                    
                    if (lowerMatch.contains("help") || lowerMatch.contains("bachao")) {
                        Log.d(TAG, "Help command detected!");
                        handleHelpCommand();
                        commandFound = true;
                        break;
                    } else if (lowerMatch.contains("pakdo") || lowerMatch.contains("pakado") || lowerMatch.contains("catch")) {
                        Log.d(TAG, "Pakdo command detected!");
                        handlePakdoCommand();
                        commandFound = true;
                        break;
                    } else if (lowerMatch.contains("give me my phone") || lowerMatch.contains("phone de")) {
                        Log.d(TAG, "Give me my phone command detected!");
                        handleGivePhoneCommand();
                        commandFound = true;
                        break;
                    }
                    
                    if (lowerMatch.contains("display off") || lowerMatch.contains("lock screen") || lowerMatch.contains("screen off")) {
                        Log.d(TAG, "LOCK SCREEN COMMAND DETECTED!");
                        boolean isEnabled = prefs.getBoolean("switch_display_off_voice", true);
                        if (isEnabled) {
                            lockScreen();
                        } else {
                            Log.d(TAG, "Lock screen command disabled in settings");
                        }
                        commandFound = true;
                        break;
                    }
                }
            }
            
            if (!commandFound && isListening) {
                scheduleDelayedRestart();
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            Log.d(TAG, "Partial results received - checking for commands early!");
            ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null) {
                for (String match : matches) {
                    String lowerMatch = match.toLowerCase().trim();
                    Log.d(TAG, "Partial heard: " + lowerMatch);
                    
                    if (lowerMatch.contains("help") || lowerMatch.contains("bachao")) {
                        Log.d(TAG, "Help command detected EARLY in partial results!");
                        handleHelpCommand();
                        return;
                    } else if (lowerMatch.contains("pakdo") || lowerMatch.contains("pakado") || lowerMatch.contains("catch")) {
                        Log.d(TAG, "Pakdo command detected EARLY in partial results!");
                        handlePakdoCommand();
                        return;
                    } else if (lowerMatch.contains("give me my phone") || lowerMatch.contains("phone de")) {
                        Log.d(TAG, "Give me my phone command detected EARLY in partial results!");
                        handleGivePhoneCommand();
                        return;
                    }
                }
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params) {}
    }

    private void scheduleDelayedRestart() {
        if (isRestartPending) {
            Log.d(TAG, "Restart already pending - skipping");
            return;
        }

        isRestartPending = true;
        pendingRestartRunnable = () -> {
            isRestartPending = false;
            if (isListening) {
                restartListening();
            }
        };
        speechRestartHandler.postDelayed(pendingRestartRunnable, RESTART_DELAY_MS);
        Log.d(TAG, "Scheduled restart in " + RESTART_DELAY_MS + "ms");
    }

    private void cancelPendingRestart() {
        if (pendingRestartRunnable != null) {
            try {
                if (speechRestartHandler != null) {
                    speechRestartHandler.removeCallbacks(pendingRestartRunnable);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error canceling pending restart: " + e.getMessage());
            }
            pendingRestartRunnable = null;
        }
        isRestartPending = false;
    }

    private void startListening() {
        if (speechRecognizer == null) {
            Log.e(TAG, "Speech recognizer not initialized!");
            return;
        }
        
        if (audioManager == null) {
            Log.e(TAG, "AudioManager is null!");
            return;
        }
        
        if (recognizerIntent == null) {
            Log.e(TAG, "Recognizer intent is null!");
            return;
        }
        
        cancelPendingRestart();
        
        try {
            Log.d(TAG, "Starting speech recognition...");
            
            int originalMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0);
            
            isListening = true;
            speechRecognizer.startListening(recognizerIntent);
            
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalMusicVolume, 0);
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to restore volume: " + e.getMessage());
                }
            }, 500);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start listening: " + e.getMessage(), e);
            isListening = false;
        }
    }

    private void restartListening() {
        if (speechRecognizer == null) {
            Log.e(TAG, "Speech recognizer is null, cannot restart");
            return;
        }
        
        if (audioManager == null) {
            Log.e(TAG, "AudioManager is null, cannot restart");
            return;
        }
        
        if (recognizerIntent == null) {
            Log.e(TAG, "Recognizer intent is null, cannot restart");
            return;
        }
        
        try {
            Log.d(TAG, "Restarting listening...");
            speechRecognizer.cancel();
            
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (isListening && speechRecognizer != null) {
                    try {
                        int originalMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                        int originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0);
                        
                        speechRecognizer.startListening(recognizerIntent);
                        
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            try {
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalMusicVolume, 0);
                                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to restore volume: " + e.getMessage());
                            }
                        }, 500);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to restart listening: " + e.getMessage(), e);
                    }
                }
            }, 500);
        } catch (Exception e) {
            Log.e(TAG, "Failed to restart listening", e);
        }
    }

    private void triggerEmergencyAlert() {
        triggerEmergencyAlert(true);
    }

    private void triggerEmergencyAlert(boolean showToast) {
        Log.d(TAG, "=== triggerEmergencyAlert() STARTED - Requesting FRESH LOCATION ===");
        HistoryActivity.addHistoryEntry(this, "Emergency SOS Triggered", "Location shared with emergency contacts");
        NotificationCenterActivity.addNotification(this, "SOS Triggered", "Emergency alert sent to your contacts");
        
        loadEmergencyContacts();
        if (contactsList.isEmpty()) {
            Log.e(TAG, "No emergency contacts found!");
            return;
        }

        Log.d(TAG, "Always requesting fresh high-accuracy location for reliability...");
        requestFreshLocation(showToast);
    }

    private void requestFreshLocation() {
        requestFreshLocation(true);
    }

    private void requestFreshLocation(boolean showToast) {
        Log.d(TAG, "=== requestFreshLocation() STARTED - HIGH ACCURACY ===");
        try {
            com.google.android.gms.location.LocationRequest locationRequest = com.google.android.gms.location.LocationRequest.create()
                    .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
                    .setInterval(1000)
                    .setNumUpdates(1);

            Log.d(TAG, "Requesting fresh high-accuracy location update...");
            
            fusedLocationClient.requestLocationUpdates(locationRequest, new com.google.android.gms.location.LocationCallback() {
                @Override
                public void onLocationResult(@NonNull com.google.android.gms.location.LocationResult locationResult) {
                    Location location = locationResult.getLastLocation();
                    if (location != null) {
                        Log.d(TAG, "Fresh location received successfully!");
                        sendAlertWithLocation(location, showToast);
                    } else {
                        Log.e(TAG, "Fresh location request returned null - using fallback");
                        sendAlertWithLocation(null, showToast);
                    }
                    fusedLocationClient.removeLocationUpdates(this);
                }
            }, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission missing", e);
            sendAlertWithLocation(null, showToast);
        }
    }

    private void sendAlertWithLocation(android.location.Location location, boolean showToast) {
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
                if (showToast) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        Toast.makeText(SilentGuardService.this, "Successfully Sent Alert Msg", Toast.LENGTH_LONG).show()
                    );
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to send SMS to " + contact.name, e);
            }
        }
    }

    private void sendAlertWithLocation(android.location.Location location) {
        sendAlertWithLocation(location, true);
    }

    private void takeSelfieAndSendSOS() {
        Log.d(TAG, "=== takeSelfieAndSendSOS() STARTED - Capturing 3 selfies! ===");
        NotificationCenterActivity.addNotification(this, "Wrong Password Detected", "Intruder selfie captured and alert sent");
        
        try {
            ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
            cameraProviderFuture.addListener(() -> {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    
                    ImageCapture imageCapture = new ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build();

                    CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                    Log.d(TAG, "Camera initialized - binding to dummy lifecycle");
                    
                    androidx.camera.lifecycle.ProcessCameraProvider finalCameraProvider = cameraProvider;
                    
                    cameraProvider.unbindAll();
                    
                    androidx.lifecycle.LifecycleOwner dummyLifecycleOwner = new androidx.lifecycle.LifecycleOwner() {
                        private final androidx.lifecycle.LifecycleRegistry registry = 
                            new androidx.lifecycle.LifecycleRegistry(this);
                        
                        {
                            registry.setCurrentState(androidx.lifecycle.Lifecycle.State.RESUMED);
                        }
                        
                        @NonNull
                        @Override
                        public androidx.lifecycle.Lifecycle getLifecycle() {
                            return registry;
                        }
                    };
                    
                    try {
                        finalCameraProvider.bindToLifecycle(dummyLifecycleOwner, cameraSelector, imageCapture);
                    } catch (Exception e) {
                        Log.w(TAG, "Camera binding failed, but trying capture anyway: " + e.getMessage());
                    }

                    final int[] selfieCount = {0};
                    final int MAX_SELFIES = 3;
                    final long[] lastUploadTime = {0};

                    Runnable captureRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (selfieCount[0] >= MAX_SELFIES) {
                                Log.d(TAG, "All " + MAX_SELFIES + " selfies captured!");
                                try {
                                    finalCameraProvider.unbindAll();
                                } catch (Exception e) {
                                    Log.w(TAG, "Failed to unbind camera: " + e.getMessage());
                                }
                                return;
                            }

                            Log.d(TAG, "Taking selfie " + (selfieCount[0] + 1) + " of " + MAX_SELFIES);
                            
                            File photoFile = new File(getExternalFilesDir(null), 
                                "selfie_" + System.currentTimeMillis() + "_" + (selfieCount[0] + 1) + ".jpg");
                            ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

                            final int currentSelfieNum = selfieCount[0] + 1;
                            
                            imageCapture.takePicture(outputOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
                                @Override
                                public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                                    Log.d(TAG, "Selfie " + currentSelfieNum + " captured successfully!");
                                    
                                    long currentTime = System.currentTimeMillis();
                                    if (currentTime - lastUploadTime[0] > 3000 || currentSelfieNum == MAX_SELFIES) {
                                        Log.d(TAG, "Uploading selfie " + currentSelfieNum + " to Firebase...");
                                        uploadSelfieOnly(photoFile);
                                        lastUploadTime[0] = currentTime;
                                    }
                                    
                                    if (currentSelfieNum == MAX_SELFIES) {
                                        Log.d(TAG, "All selfies captured - sending final SOS SMS...");
                                        triggerEmergencyAlert(false);
                                        try {
                                            finalCameraProvider.unbindAll();
                                        } catch (Exception e) {
                                            Log.w(TAG, "Failed to unbind camera: " + e.getMessage());
                                        }
                                    }
                                }

                                @Override
                                public void onError(@NonNull ImageCaptureException exception) {
                                    Log.e(TAG, "Selfie " + currentSelfieNum + " capture failed: " + exception.getMessage(), exception);
                                    if (currentSelfieNum == MAX_SELFIES) {
                                        triggerEmergencyAlert(false);
                                        try {
                                            finalCameraProvider.unbindAll();
                                        } catch (Exception e) {
                                            Log.w(TAG, "Failed to unbind camera: " + e.getMessage());
                                        }
                                    }
                                }
                            });
                        }
                    };

                    for (int i = 0; i < MAX_SELFIES; i++) {
                        final int delay = i * 500;
                        overlayHandler.postDelayed(() -> {
                            selfieCount[0]++;
                            captureRunnable.run();
                        }, delay);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Camera initialization failed: " + e.getMessage(), e);
                    triggerEmergencyAlert(false);
                }
            }, ContextCompat.getMainExecutor(this));
        } catch (Exception e) {
            Log.e(TAG, "Fatal error in takeSelfieAndSendSOS: " + e.getMessage(), e);
            triggerEmergencyAlert(false);
        }
    }
    
    private void uploadSelfieOnly(File photoFile) {
        Log.d(TAG, "=== uploadSelfieOnly() STARTED ===");
        String userId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("selfies").child(userId).child(photoFile.getName());

        storageRef.putFile(android.net.Uri.fromFile(photoFile))
                .addOnSuccessListener(taskSnapshot -> {
                    Log.d(TAG, "Selfie uploaded to Firebase successfully: " + photoFile.getName());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Selfie upload failed: " + e.getMessage(), e);
                });
    }

    private void uploadSelfieAndSendSOS(File photoFile) {
        Log.d(TAG, "=== uploadSelfieAndSendSOS() STARTED ===");
        String userId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("selfies").child(userId).child(photoFile.getName());

        storageRef.putFile(android.net.Uri.fromFile(photoFile))
                .addOnSuccessListener(taskSnapshot -> {
                    Log.d(TAG, "Selfie uploaded to Firebase! Getting download URL...");
                    storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        Log.d(TAG, "Download URL obtained: " + uri.toString());
                        sendSOSWithImage(uri.toString());
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Upload failed: " + e.getMessage(), e);
                    triggerEmergencyAlert();
                });
    }

    private void sendSOSWithImage(String imageUrl) {
        Log.d(TAG, "=== sendSOSWithImage() STARTED - Requesting FRESH LOCATION ===");
        loadEmergencyContacts();
        if (contactsList.isEmpty()) {
            Log.e(TAG, "No emergency contacts found!");
            return;
        }

        Log.d(TAG, "Requesting fresh location for selfie SOS...");
        
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
                        Log.d(TAG, "Fresh location received for selfie SOS!");
                    } else {
                        Log.e(TAG, "Fresh location request returned null for selfie SOS");
                    }
                    
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
                            Log.d(TAG, "Selfie SOS SMS sent to " + contact.name + " at " + contact.phone);
                        } catch (Exception ex) {
                            Log.e(TAG, "Failed to send SOS SMS to " + contact.name, ex);
                        }
                    }
                    
                    fusedLocationClient.removeLocationUpdates(this);
                }
            }, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission missing for selfie SOS", e);
            
            String locationLink = "https://www.google.com/maps/search/?api=1&query=Unknown+Location";
            String message = "WRONG PASSWORD DETECTED! Intruder photo: " + imageUrl + " | Location: " + locationLink;
            
            for (Contact contact : contactsList) {
                try {
                    SmsManager.getDefault().sendTextMessage(contact.phone, null, message, null, null);
                    Log.d(TAG, "Selfie SOS SMS sent (without location) to " + contact.name);
                } catch (Exception ex) {
                    Log.e(TAG, "Failed to send SOS SMS to " + contact.name, ex);
                }
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Silent Guard Protection",
                    NotificationManager.IMPORTANCE_HIGH
            );
            serviceChannel.setDescription("Emergency alerts and notifications");
            serviceChannel.enableVibration(true);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void createAccessibilityNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel accessibilityChannel = new NotificationChannel(
                    ACCESSIBILITY_CHANNEL_ID,
                    "Accessibility Reminder",
                    NotificationManager.IMPORTANCE_LOW
            );
            accessibilityChannel.setDescription("Reminds you to enable Accessibility Service");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(accessibilityChannel);
            }
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + SilentGuardAccessibilityService.class.getName();
        android.content.ContentResolver contentResolver = getContentResolver();
        String enabledServices = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        return enabledServices != null && enabledServices.contains(service);
    }

    private void showAccessibilityReminderNotification() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ACCESSIBILITY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Enable Volume Trigger")
            .setContentText("Tap to enable Accessibility Service for screen-off volume trigger")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setColor(ContextCompat.getColor(this, R.color.neon_violet));

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(ACCESSIBILITY_NOTIFICATION_ID, builder.build());
        }
    }

    private void cancelAccessibilityReminderNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(ACCESSIBILITY_NOTIFICATION_ID);
        }
    }

    private void startAccessibilityServiceCheck() {
        createAccessibilityNotificationChannel();

        accessibilityCheckRunnable = new Runnable() {
            @Override
            public void run() {
                boolean isVolumeEnabled = prefs.getBoolean("switch_volume", false);
                if (isVolumeEnabled) {
                    if (!isAccessibilityServiceEnabled()) {
                        showAccessibilityReminderNotification();
                    } else {
                        cancelAccessibilityReminderNotification();
                    }
                } else {
                    cancelAccessibilityReminderNotification();
                }
                accessibilityCheckHandler.postDelayed(this, 30000); // Check every 30 seconds
            }
        };

        accessibilityCheckHandler.post(accessibilityCheckRunnable);
    }

    private void stopAccessibilityServiceCheck() {
        if (accessibilityCheckHandler != null && accessibilityCheckRunnable != null) {
            accessibilityCheckHandler.removeCallbacks(accessibilityCheckRunnable);
        }
        cancelAccessibilityReminderNotification();
    }

    private void handleHelpCommand() {
        Log.d(TAG, "Executing Help Command - Sending SOS Alert...");
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
                Log.d(TAG, "Received ACTION_VOLUME_SOS - calling handleVolumeSOS()");
                handleVolumeSOS();
            } else if ("ACTION_CALL_CONTACT".equals(intent.getAction())) {
                autoCallEmergencyContact();
            } else if ("ACTION_STOP_SIREN".equals(intent.getAction())) {
                stopSiren();
            }
        }
        
        boolean isVoiceEnabled = prefs.getBoolean("switch_voice", true);
        if (isVoiceEnabled) {
            if (speechRecognizer == null) {
                initSpeechRecognizer();
            }
            startListening();
        }

        return START_STICKY;
    }



    private void showEmergencyOverlay() {
        Log.d(TAG, "=== showEmergencyOverlay() STARTED ===");
        
        if (isOverlayShowing) {
            Log.d(TAG, "Overlay already showing - skipping duplicate");
            return;
        }

        try {
            if (!Settings.canDrawOverlays(this)) {
                Log.e(TAG, "Overlay permission not granted!");
                return;
            }

            Log.d(TAG, "Inflating emergency overlay layout...");
            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            overlayView = inflater.inflate(R.layout.activity_emergency_response, null);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? 
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : 
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                android.graphics.PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;

            Button btnPositive = overlayView.findViewById(R.id.btn_positive);
            Button btnNegative = overlayView.findViewById(R.id.btn_negative);
            TextView tvSubMessage = overlayView.findViewById(R.id.tv_sub_message);
            View popupContainer = overlayView.findViewById(R.id.popup_container);
            View pulseRingOuter = overlayView.findViewById(R.id.pulse_ring_outer);
            View pulseRingInner = overlayView.findViewById(R.id.pulse_ring_inner);

            overlayCountdownSeconds = 10;
            tvSubMessage.setVisibility(View.VISIBLE);
            tvSubMessage.setText("Calling in " + overlayCountdownSeconds + " seconds...");

            btnPositive.setOnClickListener(v -> {
                Log.d(TAG, "CALL NOW clicked");
                cancelOverlayCountdown();
                hideEmergencyOverlay();
                autoCallEmergencyContact();
            });

            btnNegative.setOnClickListener(v -> {
                Log.d(TAG, "CANCEL clicked");
                cancelOverlayCountdown();
                hideEmergencyOverlay();
            });

            Log.d(TAG, "Adding overlay view to WindowManager...");
            windowManager.addView(overlayView, params);
            isOverlayShowing = true;
            Log.d(TAG, "=== Emergency overlay SHOWN SUCCESSFULLY (even when screen OFF) ===");

            startOverlayAnimations(popupContainer, pulseRingOuter, pulseRingInner);
            startOverlayCountdown(tvSubMessage);

        } catch (Exception e) {
            Log.e(TAG, "Error showing emergency overlay: " + e.getMessage(), e);
        }
    }

    private void startOverlayAnimations(View container, View outerRing, View innerRing) {
        container.setScaleX(0.7f);
        container.setScaleY(0.7f);
        container.setAlpha(0f);
        
        container.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(500)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                .start();

        startPulseAnimation(outerRing, 1.0f, 1.5f, 0.3f, 2000);
        startPulseAnimation(innerRing, 1.0f, 1.3f, 0.5f, 1600);
    }

    private void startPulseAnimation(View view, float fromScale, float toScale, float toAlpha, long duration) {
        view.setScaleX(fromScale);
        view.setScaleY(fromScale);
        view.setAlpha(0.5f);

        view.animate()
                .scaleX(toScale)
                .scaleY(toScale)
                .alpha(toAlpha)
                .setDuration(duration)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> {
                    if (isOverlayShowing && view != null) {
                        view.setScaleX(fromScale);
                        view.setScaleY(fromScale);
                        view.setAlpha(0.5f);
                        startPulseAnimation(view, fromScale, toScale, toAlpha, duration);
                    }
                })
                .start();
    }

    private void startOverlayCountdown(TextView countdownText) {
        cancelOverlayCountdown();

        overlayCountdownRunnable = new Runnable() {
            @Override
            public void run() {
                overlayCountdownSeconds--;
                if (overlayCountdownSeconds <= 0) {
                    hideEmergencyOverlay();
                    autoCallEmergencyContact();
                } else {
                    countdownText.setText("Calling in " + overlayCountdownSeconds + " seconds...");
                    overlayHandler.postDelayed(this, 1000);
                }
            }
        };
        overlayHandler.postDelayed(overlayCountdownRunnable, 1000);
    }

    private void cancelOverlayCountdown() {
        if (overlayCountdownRunnable != null) {
            overlayHandler.removeCallbacks(overlayCountdownRunnable);
            overlayCountdownRunnable = null;
        }
    }

    private void hideEmergencyOverlay() {
        Log.d(TAG, "hideEmergencyOverlay() called");
        try {
            if (isOverlayShowing && overlayView != null && windowManager != null) {
                windowManager.removeView(overlayView);
                overlayView = null;
                isOverlayShowing = false;
                cancelOverlayCountdown();
                releaseEmergencyWakeLock();
                Log.d(TAG, "Emergency overlay hidden and wake lock released");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error hiding emergency overlay: " + e.getMessage(), e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service onDestroy() called - cleaning up resources");
        hideEmergencyOverlay();
        releaseEmergencyWakeLock();
        isListening = false;
        cancelPendingRestart();
        stopSiren();
        
        if (speechRecognizer != null) {
            try {
                speechRecognizer.destroy();
            } catch (Exception e) {
                Log.e(TAG, "Error destroying speech recognizer: " + e.getMessage());
            }
            speechRecognizer = null;
        }
        
        if (volumeObserver != null) {
            try {
                getContentResolver().unregisterContentObserver(volumeObserver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering volume observer: " + e.getMessage());
            }
            volumeObserver = null;
        }
        
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing wake lock: " + e.getMessage());
            }
            wakeLock = null;
        }
        
        if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
            try {
                cameraExecutor.shutdown();
            } catch (Exception e) {
                Log.e(TAG, "Error shutting down camera executor: " + e.getMessage());
            }
            cameraExecutor = null;
        }
        
        stopAccessibilityServiceCheck();
        
        if (overlayHandler != null) {
            overlayHandler.removeCallbacksAndMessages(null);
            overlayHandler = null;
        }
        
        if (accessibilityCheckHandler != null) {
            accessibilityCheckHandler.removeCallbacksAndMessages(null);
            accessibilityCheckHandler = null;
        }
        
        Log.d(TAG, "Service cleanup completed");
    }
    
    public static void checkPermissionsOnAppOpen(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("SilentGuardPrefs", Context.MODE_PRIVATE);
            
            DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName componentName = new ComponentName(context, MyDeviceAdminReceiver.class);
            
            boolean adminActive = devicePolicyManager.isAdminActive(componentName);
            boolean overlayGranted = Settings.canDrawOverlays(context);
            
            boolean hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            boolean hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
            boolean hasSms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
            boolean hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
            boolean hasRecord = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            
            StringBuilder missingPermissions = new StringBuilder();
            if (!adminActive) missingPermissions.append("Device Admin, ");
            if (!overlayGranted) missingPermissions.append("Overlay, ");
            if (!hasLocation) missingPermissions.append("Location, ");
            if (!hasCamera) missingPermissions.append("Camera, ");
            if (!hasSms) missingPermissions.append("SMS, ");
            if (!hasContacts) missingPermissions.append("Contacts, ");
            if (!hasRecord) missingPermissions.append("Microphone, ");
            
            if (missingPermissions.length() > 0) {
                String missing = missingPermissions.toString();
                if (missing.endsWith(", ")) {
                    missing = missing.substring(0, missing.length() - 2);
                }
                NotificationCenterActivity.addNotification(context, "Permissions Required", "Missing permissions: " + missing);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
