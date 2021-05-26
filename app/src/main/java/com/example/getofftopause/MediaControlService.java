package com.example.getofftopause;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class MediaControlService extends Service {

    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "MediaControlService";
    private static final String ACTION = MediaControlService.class.getCanonicalName() + ".ACTION";
    private static final String CHANNEL_ID = "default";
    private final AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).build();
    private final IBinder binder = new MediaControlBinder();
    private SharedPreferences sharedPreferences;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private AudioManager audioManager;
    private NotificationManager notificationManager;
    private ActivityRecognitionClient activityRecognitionClient;
    private boolean enabled;
    private boolean usesLocation;
    private boolean usesActivityRecognition;
    private boolean hasAudioFocus;
    private List<Integer> selectedActivities;
    private NotificationCompat.Builder notificationBuilder;
    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult locationResult) {
            super.onLocationResult(locationResult);
            Location location = locationResult.getLastLocation();

            if (!location.hasSpeed()) {
                return;
            }

            float speedThresholdKph = Float.parseFloat(sharedPreferences.getString(getString(R.string.speed_threshold_key), getString(R.string.speed_threshold_default_value)));

            float lastSpeedMps = location.getSpeed();
            float lastSpeedKph = 3.6f * lastSpeedMps;

            Log.d(TAG, "hasSpeed: " + location.hasSpeed() + ", getSpeed: " + location.getSpeed());
            if (lastSpeedKph < speedThresholdKph) {
                requestAudioFocus();
            } else {
                abandonAudioFocusRequest();
            }
        }
    };
    private final BroadcastReceiver transitionsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ActivityTransitionResult.hasResult(intent)) {

                ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);

                for (ActivityTransitionEvent event : result.getTransitionEvents()) {
                    if (selectedActivities.contains(event.getActivityType()) && event.getTransitionType() == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                        requestLocationUpdates();
                        if (!usesLocation) {
                            abandonAudioFocusRequest();
                        }

                        Log.d(TAG, "requestLocationUpdates()");
                    } else {
                        removeLocationUpdates();
                        requestAudioFocus();

                        Log.d(TAG, "removeLocationUpdates()");
                    }
                }
            }
        }
    };
    private PendingIntent pendingIntent;

    public boolean isEnabled() {
        return enabled;
    }

    private void requestAudioFocus() {
        if (hasAudioFocus) {
            return;
        }

        audioManager.requestAudioFocus(focusRequest);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder
                .setContentTitle(getText(R.string.paused_media))
                .setSmallIcon(R.drawable.ic_baseline_music_off_24)
                .build());

        hasAudioFocus = true;
    }

    private void abandonAudioFocusRequest() {
        if (!hasAudioFocus) {
            return;
        }

        audioManager.abandonAudioFocusRequest(focusRequest);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder
                .setContentTitle(getText(R.string.playing_media))
                .setSmallIcon(R.drawable.ic_baseline_music_note_24)
                .build());

        hasAudioFocus = false;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "default", NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(channel);
    }

    private void requestLocationUpdates() {
        if (!usesLocation) {
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000);
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void removeLocationUpdates() {
        if (!usesLocation) {
            return;
        }

        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }

    private void requestActivityTransitionUpdates() {
        if (!usesActivityRecognition) {
            return;
        }

        registerReceiver(transitionsReceiver, new IntentFilter(ACTION));

        List<Integer> activityTypes = Arrays.asList(
                DetectedActivity.STILL,
                DetectedActivity.IN_VEHICLE,
                DetectedActivity.ON_BICYCLE,
                DetectedActivity.RUNNING,
                DetectedActivity.WALKING);
        List<ActivityTransition> transitions = activityTypes.stream()
                .map(activityType -> new ActivityTransition.Builder()
                        .setActivityType(activityType)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build())
                .collect(Collectors.toList());

        ActivityTransitionRequest request = new ActivityTransitionRequest(transitions);
        activityRecognitionClient.requestActivityTransitionUpdates(request, pendingIntent);
    }

    private void removeActivityTransitionUpdates() {
        if (!usesActivityRecognition) {
            return;
        }

        unregisterReceiver(transitionsReceiver);
        activityRecognitionClient.removeActivityTransitionUpdates(pendingIntent);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        audioManager = getSystemService(AudioManager.class);
        notificationManager = getSystemService(NotificationManager.class);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        activityRecognitionClient = ActivityRecognition.getClient(this);

        Intent intent = new Intent(ACTION);
        pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        enabled = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        usesLocation = sharedPreferences.getBoolean(getString(R.string.location_key), true);
        usesActivityRecognition = sharedPreferences.getBoolean(getString(R.string.activity_recognition_key), true);

        hasAudioFocus = false;
        enabled = true;

        createNotificationChannel();
        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_music_note_24)
                .setContentTitle(getString(R.string.enabled_media_control))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        startForeground(NOTIFICATION_ID, notificationBuilder.build());

        selectedActivities = new LinkedList<>();
        if (sharedPreferences.getBoolean(getString(R.string.in_vehicle_key), true)) {
            selectedActivities.add(DetectedActivity.IN_VEHICLE);
        }
        if (sharedPreferences.getBoolean(getString(R.string.on_bicycle_key), true)) {
            selectedActivities.add(DetectedActivity.ON_BICYCLE);
        }
        if (sharedPreferences.getBoolean(getString(R.string.running_key), false)) {
            selectedActivities.add(DetectedActivity.RUNNING);
        }
        if (sharedPreferences.getBoolean(getString(R.string.walking_key), false)) {
            selectedActivities.add(DetectedActivity.WALKING);
        }

        requestLocationUpdates();
        requestActivityTransitionUpdates();

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        removeLocationUpdates();
        removeActivityTransitionUpdates();

        enabled = false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class MediaControlBinder extends Binder {
        public MediaControlService getService() {
            return MediaControlService.this;
        }
    }
}