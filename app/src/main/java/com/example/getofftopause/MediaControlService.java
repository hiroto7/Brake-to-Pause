package com.example.getofftopause;

import android.Manifest;
import android.app.Notification;
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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

public class MediaControlService extends Service implements AudioManager.OnAudioFocusChangeListener {

    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "MediaControlService";
    private static final String ACTION_TRANSITION = MediaControlService.class.getCanonicalName() + ".ACTION_TRANSITION";
    private static final String ACTION_STOP_MEDIA_CONTROL = MediaControlService.class.getCanonicalName() + ".ACTION_STOP_MEDIA_CONTROL";
    private static final String MEDIA_CONTROL_CHANNEL_ID = "media_control";
    private static final String AUTOMATIC_STOP_CHANNEL_ID = "automatic_stop";
    private final AudioFocusRequest focusRequest =
            new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setOnAudioFocusChangeListener(this)
                    .build();
    private final IBinder binder = new MediaControlBinder();
    private SharedPreferences sharedPreferences;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private AudioManager audioManager;
    private NotificationManager notificationManager;
    private ActivityRecognitionClient activityRecognitionClient;
    private boolean enabled;
    private final Handler handler = new Handler();
    private boolean usesLocation;
    private boolean usesActivityRecognition;
    private boolean hasAudioFocus;
    private List<Integer> selectedActivities;
    private NotificationCompat.Builder notificationBuilder;
    private PendingIntent mainPendingIntent;
    Runnable stopMediaControlWithNotification = () -> {
        stopMediaControl();

        Notification notification = new NotificationCompat.Builder(this, AUTOMATIC_STOP_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_stop_24)
                .setContentTitle(getString(R.string.media_control_automatically_ended))
                .setContentText(getString(R.string.time_has_passed_with_media_paused))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(mainPendingIntent)
                .setAutoCancel(true)
                .build();

        notificationManager.notify(NOTIFICATION_ID, notification);
    };
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
                    } else {
                        removeLocationUpdates();
                        requestAudioFocus();
                    }
                }
            }
        }
    };
    private PendingIntent transitionPendingIntent;
    @Nullable
    private Runnable onMediaControlStartedListener;
    @Nullable
    private Runnable onMediaControlStoppedListener;
    private final BroadcastReceiver stopReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopMediaControl();
            stopSelf();
        }
    };

    public boolean isEnabled() {
        return enabled;
    }

    private void requestAudioFocus() {
        if (hasAudioFocus) {
            return;
        }

        handler.postDelayed(stopMediaControlWithNotification, 60 * 30 * 1000);

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder
                .setContentTitle(getText(R.string.paused_media))
                .setSmallIcon(R.drawable.ic_baseline_pause_24)
                .build());

        audioManager.requestAudioFocus(focusRequest);
        hasAudioFocus = true;
    }

    private void abandonAudioFocusRequest() {
        if (!hasAudioFocus) {
            return;
        }

        handler.removeCallbacks(stopMediaControlWithNotification);

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder
                .setContentTitle(getText(R.string.playing_media))
                .setSmallIcon(R.drawable.ic_baseline_play_arrow_24)
                .build());

        audioManager.abandonAudioFocusRequest(focusRequest);
        hasAudioFocus = false;
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
                .setInterval(1500);
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void removeLocationUpdates() {
        if (!usesLocation) {
            return;
        }

        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }

    private void createNotificationChannel() {
        List<NotificationChannel> channels = Arrays.asList(
                new NotificationChannel(MEDIA_CONTROL_CHANNEL_ID, getString(R.string.media_control), NotificationManager.IMPORTANCE_LOW),
                new NotificationChannel(AUTOMATIC_STOP_CHANNEL_ID, getString(R.string.automatic_exit), NotificationManager.IMPORTANCE_DEFAULT));
        notificationManager.createNotificationChannels(channels);
    }

    private void requestActivityTransitionUpdates() {
        if (!usesActivityRecognition) {
            return;
        }

        registerReceiver(transitionsReceiver, new IntentFilter(ACTION_TRANSITION));

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
        activityRecognitionClient.requestActivityTransitionUpdates(request, transitionPendingIntent);
    }

    private void removeActivityTransitionUpdates() {
        if (!usesActivityRecognition) {
            return;
        }

        unregisterReceiver(transitionsReceiver);
        activityRecognitionClient.removeActivityTransitionUpdates(transitionPendingIntent);
    }

    public void stopMediaControl() {
        unregisterReceiver(stopReceiver);
        handler.removeCallbacks(stopMediaControlWithNotification);

        removeLocationUpdates();
        removeActivityTransitionUpdates();

        stopForeground(true);

        enabled = false;
        if (onMediaControlStoppedListener != null) {
            onMediaControlStoppedListener.run();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        audioManager = getSystemService(AudioManager.class);
        notificationManager = getSystemService(NotificationManager.class);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        activityRecognitionClient = ActivityRecognition.getClient(this);

        Intent intent = new Intent(ACTION_TRANSITION);
        transitionPendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        enabled = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        createNotificationChannel();

        Intent mainIntent = new Intent(this, MainActivity.class);
        mainPendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(ACTION_STOP_MEDIA_CONTROL);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        notificationBuilder = new NotificationCompat.Builder(this, MEDIA_CONTROL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_music_note_24)
                .setContentTitle(getString(R.string.enabled_media_control))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(mainPendingIntent)
                .addAction(R.drawable.ic_baseline_stop_24, getString(R.string.exit_media_control), stopPendingIntent);
        startForeground(NOTIFICATION_ID, notificationBuilder.build());

        registerReceiver(stopReceiver, new IntentFilter(ACTION_STOP_MEDIA_CONTROL));

        usesLocation = sharedPreferences.getBoolean(getString(R.string.location_key), true);
        usesActivityRecognition = sharedPreferences.getBoolean(getString(R.string.activity_recognition_key), true);

        hasAudioFocus = false;

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

        enabled = true;
        if (onMediaControlStartedListener != null) {
            onMediaControlStartedListener.run();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (hasAudioFocus) {
            AudioFocusRequest lastingFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).build();
            audioManager.requestAudioFocus(lastingFocusRequest);
        }

        if (enabled) {
            stopMediaControl();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (focusChange != AudioManager.AUDIOFOCUS_LOSS) {
            return;
        }

        handler.removeCallbacks(stopMediaControlWithNotification);
        hasAudioFocus = false;

        if (!enabled) {
            return;
        }

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder
                .setContentTitle(getText(R.string.enabled_media_control))
                .setSmallIcon(R.drawable.ic_baseline_music_note_24)
                .build());
    }

    public void setOnMediaControlStartedListener(@Nullable Runnable onMediaControlStartedListener) {
        this.onMediaControlStartedListener = onMediaControlStartedListener;
    }

    public void setOnMediaControlStoppedListener(@Nullable Runnable onMediaControlStoppedListener) {
        this.onMediaControlStoppedListener = onMediaControlStoppedListener;
    }

    public class MediaControlBinder extends Binder {
        public MediaControlService getService() {
            return MediaControlService.this;
        }
    }
}