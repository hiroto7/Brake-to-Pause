package com.example.getofftopause;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
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

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

public class MediaControlService extends Service {
    private static final String CHANNEL_ID = "default";
    private static final int NOTIFICATION_ID = 1;

    private SharedPreferences sharedPreferences;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult locationResult) {
            super.onLocationResult(locationResult);
            Location location = locationResult.getLastLocation();

            if (!location.hasSpeed()) {
                return;
            }

            float speedThresholdKph = Float.parseFloat(sharedPreferences.getString("speed_threshold", getString(R.string.speed_threshold_default_value)));

            float lastSpeedMps = location.getSpeed();
            float lastSpeedKph = 3.6f * lastSpeedMps;

            Log.d(getString(R.string.app_name), "hasSpeed: " + location.hasSpeed() + ", getSpeed: " + location.getSpeed());
            if (lastSpeedKph < speedThresholdKph) {
                audioManager.requestAudioFocus(focusRequest);
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.setContentText(getText(R.string.paused_media)).build());

                Log.d(getString(R.string.app_name), "requestAudioFocus");
            } else {
                audioManager.abandonAudioFocusRequest(focusRequest);
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.setContentText(getText(R.string.playing_media)).build());

                Log.d(getString(R.string.app_name), "abandonAudioFocusRequest");
            }
        }
    };

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "default", NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(channel);
    }

    private void requestLocationUpdates() {
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

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(getString(R.string.app_name), "onCreate");

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        audioManager = getSystemService(AudioManager.class);
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).build();

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        notificationManager = getSystemService(NotificationManager.class);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        Log.d(getString(R.string.app_name), "onStartCommand");

        // PendingIntent pendingIntent = PendingIntent.getActivity(context);

        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_pedal_bike_24)
                .setContentTitle(getString(R.string.enabled_media_control))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        startForeground(NOTIFICATION_ID, notificationBuilder.build());

        requestLocationUpdates();

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}