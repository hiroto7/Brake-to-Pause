package com.example.getofftopause;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button enableButton = findViewById(R.id.button_enable);
        Button disableButton = findViewById(R.id.button_disable);
        TextInputLayout textInputLayout = findViewById(R.id.text_input_layout);
        TextInputEditText textInputEditText = findViewById(R.id.edit_text);

        FusedLocationProviderClient fusedLocationProviderClient = new FusedLocationProviderClient(this);
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000);

        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).build();

        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                Location location = locationResult.getLastLocation();

                if (!location.hasSpeed()) {
                    return;
                }

                float speedThresholdKph = textInputEditText.getText().toString().equals("") ? 8 : Float.parseFloat(textInputEditText.getText().toString());

                float lastSpeedMps = location.getSpeed();
                float lastSpeedKph = 3.6f * lastSpeedMps;

                Log.d(getString(R.string.app_name), "hasSpeed: " + location.hasSpeed() + ", getSpeed: " + location.getSpeed());
                if (lastSpeedKph < speedThresholdKph) {
                    audioManager.requestAudioFocus(focusRequest);
                    Log.d(getString(R.string.app_name), "requestAudioFocus");
                } else {
                    audioManager.abandonAudioFocusRequest(focusRequest);
                    Log.d(getString(R.string.app_name), "abandonAudioFocusRequest");
                }
            }
        };

        enableButton.setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }

            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());

            textInputLayout.setEnabled(false);
            enableButton.setEnabled(false);
            disableButton.setEnabled(true);
        });

        disableButton.setOnClickListener(v -> {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);

            textInputLayout.setEnabled(true);
            enableButton.setEnabled(true);
            disableButton.setEnabled(false);
        });
    }
}