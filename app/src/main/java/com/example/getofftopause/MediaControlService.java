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
import com.google.android.gms.tasks.Task;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class MediaControlService extends Service {

    private static final String TAG = "MediaControlService";

    private static final String ACTION = BuildConfig.APPLICATION_ID + ".ACTION";
    private static final String CHANNEL_ID = "default";
    private static final int NOTIFICATION_ID = 1;
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
    private boolean usesLocation;
    private boolean usesActivityRecognition;

    private SharedPreferences sharedPreferences;
    private List<Integer> activities;
    private final BroadcastReceiver transitionsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ActivityTransitionResult.hasResult(intent)) {

                ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);

                for (ActivityTransitionEvent event : result.getTransitionEvents()) {
                    if (activities.contains(event.getActivityType()) && event.getTransitionType() == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
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
    private FusedLocationProviderClient fusedLocationProviderClient;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    private PendingIntent pendingIntent;
    private ActivityRecognitionClient activityRecognitionClient;

    private void requestAudioFocus() {
        audioManager.requestAudioFocus(focusRequest);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.setContentText(getText(R.string.paused_media)).build());

        Log.d(TAG, "requestAudioFocus()");
    }

    private void abandonAudioFocusRequest() {
        audioManager.abandonAudioFocusRequest(focusRequest);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.setContentText(getText(R.string.playing_media)).build());

        Log.d(TAG, "abandonAudioFocusRequest()");
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
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
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

        List<ActivityTransition> transitions = Arrays.asList(
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.STILL)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build(),
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.IN_VEHICLE)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build(),
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.ON_BICYCLE)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build(),
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.RUNNING)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build(),
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.WALKING)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build());

        ActivityTransitionRequest request = new ActivityTransitionRequest(transitions);

        Task<Void> task = activityRecognitionClient.requestActivityTransitionUpdates(request, pendingIntent);
        task.addOnSuccessListener(unused -> Log.d(TAG, "onSuccess()"));
        task.addOnFailureListener(e -> Log.d(TAG, "onFailure(): " + e));
    }

    private void removeActivityTransitionUpdates() {
        if (!usesActivityRecognition) {
            return;
        }

        Task<Void> task = activityRecognitionClient.removeActivityTransitionUpdates(pendingIntent);
        task.addOnSuccessListener(unused -> Log.d(TAG, "onSuccess()"));
        task.addOnFailureListener(e -> Log.d(TAG, "onFailure(): " + e));
    }

    @Override
    public void onCreate() {
        super.onCreate();

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        audioManager = getSystemService(AudioManager.class);
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).build();

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        notificationManager = getSystemService(NotificationManager.class);

        Intent intent = new Intent(ACTION);
        pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        activityRecognitionClient = ActivityRecognition.getClient(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        usesLocation = sharedPreferences.getBoolean(getString(R.string.location_key), true);
        usesActivityRecognition = sharedPreferences.getBoolean(getString(R.string.activity_recognition_key), true);

        createNotificationChannel();
        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_pedal_bike_24)
                .setContentTitle(getString(R.string.enabled_media_control))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        startForeground(NOTIFICATION_ID, notificationBuilder.build());

        requestLocationUpdates();

        registerReceiver(transitionsReceiver, new IntentFilter(ACTION));
        requestActivityTransitionUpdates();

        activities = new LinkedList<>();
        if (sharedPreferences.getBoolean(getString(R.string.in_vehicle_key), true)) {
            activities.add(DetectedActivity.IN_VEHICLE);
        }
        if (sharedPreferences.getBoolean(getString(R.string.on_bicycle_key), true)) {
            activities.add(DetectedActivity.ON_BICYCLE);
        }
        if (sharedPreferences.getBoolean(getString(R.string.running_key), false)) {
            activities.add(DetectedActivity.RUNNING);
        }
        if (sharedPreferences.getBoolean(getString(R.string.walking_key), false)) {
            activities.add(DetectedActivity.WALKING);
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        removeLocationUpdates();

        unregisterReceiver(transitionsReceiver);
        removeActivityTransitionUpdates();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}