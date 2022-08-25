package io.github.hiroto7.braketopause;

import android.Manifest;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.NumberPicker;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import io.github.hiroto7.braketopause.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int MIN_SPEED = 1;
    private static final int MAX_SPEED = 30;

    private ActivityMainBinding binding;
    private MainViewModel model;
    private Intent intent;
    private SharedPreferences sharedPreferences;
    private final Runnable onMediaControlStartedListener = () -> model.isControllingPlaybackState().setValue(true);
    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (result.isEmpty()) {
                    return;
                }
                if (result.containsValue(false)) {
                    Snackbar.make(binding.buttonStart, getString(R.string.permission_denied), Snackbar.LENGTH_SHORT).setAnchorView(binding.buttonStart)
                            .setAction(R.string.settings, view -> startActivity(new Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", getPackageName(), null)
                            ))).show();
                    return;
                }

                startForegroundService(intent);
            });
    private final Runnable onMediaControlStoppedListener = () -> model.isControllingPlaybackState().setValue(false);
    private PlaybackControlService playbackControlService;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PlaybackControlService.MediaControlBinder binder = (PlaybackControlService.MediaControlBinder) service;
            playbackControlService = binder.getService();

            playbackControlService.addOnMediaControlStartedListener(onMediaControlStartedListener);
            playbackControlService.addOnMediaControlStoppedListener(onMediaControlStoppedListener);

            model.isControllingPlaybackState().setValue(playbackControlService.isEnabled());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            playbackControlService.removeOnMediaControlStartedListener(onMediaControlStartedListener);
            playbackControlService.removeOnMediaControlStoppedListener(onMediaControlStoppedListener);
            playbackControlService = null;
        }
    };

    @Override
    protected void onPause() {
        super.onPause();

        unbindService(connection);
    }

    @Override
    protected void onResume() {
        super.onResume();

        bindService(intent, connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        binding.setLifecycleOwner(this);

        View view = binding.getRoot();
        setContentView(view);

        model = new ViewModelProvider(this).get(MainViewModel.class);
        binding.setViewModel(model);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        intent = new Intent(getApplication(), PlaybackControlService.class);

        binding.buttonStart.setOnClickListener(this::onStartButtonClicked);
        binding.buttonStop.setOnClickListener(this::onStopButtonClicked);

        binding.textSpeedThreshold.setOnClickListener(v -> {
            NumberPicker numberPicker = new NumberPicker(this);
            numberPicker.setMinValue(MIN_SPEED);
            numberPicker.setMaxValue(MAX_SPEED);
            numberPicker.setWrapSelectorWheel(false);
            numberPicker.setValue(model.getSpeedThreshold().getValue());
            numberPicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
            numberPicker.setDisplayedValues(IntStream.rangeClosed(MIN_SPEED, MAX_SPEED).mapToObj(speed -> getString(R.string.n_kph, speed)).toArray(String[]::new));

            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_baseline_speed_24)
                    .setTitle(R.string.speed_threshold_title)
                    .setView(numberPicker)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> sharedPreferences
                            .edit()
                            .putInt(getString(R.string.speed_threshold_key), numberPicker.getValue())
                            .apply())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });

        binding.switchActivityRecognition.setOnCheckedChangeListener((buttonView, isChecked) -> sharedPreferences
                .edit()
                .putBoolean(getString(R.string.activity_recognition_key), isChecked)
                .apply());

        List<Activity> activities = Arrays.asList(
                new Activity(R.string.in_vehicle_key, R.string.in_vehicle_title),
                new Activity(R.string.on_bicycle_key, R.string.on_bicycle_title),
                new Activity(R.string.running_key, R.string.running_title),
                new Activity(R.string.walking_key, R.string.walking_title));

        binding.layout.setOnClickListener(v -> {
            Map<Activity, Boolean> map = new HashMap<>();
            activities.forEach(activity -> map.put(activity, sharedPreferences.getBoolean(activity.key, true)));

            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.activity_recognition_header)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        activities.forEach(activity -> editor.putBoolean(activity.key, map.get(activity)));
                        editor.apply();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .setMultiChoiceItems(
                            activities.stream().map(activity -> activity.title).toArray(String[]::new),
                            ArrayUtils.toPrimitive(activities.stream().map(activity -> sharedPreferences.getBoolean(activity.key, true)).<Boolean>toArray(Boolean[]::new)),
                            (dialog, which, isChecked) -> {
                                map.put(activities.get(which), isChecked);
                                ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(map.values().stream().anyMatch(value -> value));
                            })
                    .show();
        });

        binding.button.setOnClickListener(v ->
                getSystemService(StatusBarManager.class).requestAddTileService(
                        new ComponentName(this, PlaybackControlTileService.class),
                        getString(R.string.playback_control),
                        Icon.createWithResource(this, R.drawable.ic_baseline_pause_circle_outline_24),
                        runnable -> {
                        },
                        integer -> {
                        }));
    }

    private void onStopButtonClicked(View v) {
        playbackControlService.stopMediaControl();
        stopService(intent);
    }

    private void onStartButtonClicked(View v) {
        List<String> requestedPermissions = new ArrayList<>();

        boolean locationPermissionGranted =
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (sharedPreferences.getBoolean(getString(R.string.location_key), true) && !locationPermissionGranted) {
            requestedPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            requestedPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        if (sharedPreferences.getBoolean(getString(R.string.activity_recognition_key), true) &&
                checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestedPermissions.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }

        if (!requestedPermissions.isEmpty()) {
            requestPermissionsLauncher.launch(requestedPermissions.toArray(new String[0]));
            return;
        }

        startForegroundService(intent);
    }

    private class Activity {
        final public String key;
        final public String title;

        Activity(int keyResId, int titleResId) {
            this.key = getString(keyResId);
            this.title = getString(titleResId);
        }
    }
}