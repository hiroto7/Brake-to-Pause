package io.github.hiroto7.braketopause;

import android.Manifest;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageView;
import android.widget.NumberPicker;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
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

import io.github.hiroto7.braketopause.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private boolean enabled;
    private boolean starting;

    private ActivityMainBinding binding;
    private Intent intent;
    private SharedPreferences sharedPreferences;
    private MediaControlService mediaControlService;
    private List<Activity> activities;
    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                setStarting(false);

                if (result.containsValue(false)) {
                    Snackbar.make(binding.buttonStart, getString(R.string.permission_denied), Snackbar.LENGTH_SHORT).setAnchorView(binding.buttonStart).show();
                    return;
                }

                startForegroundService(intent);
            });
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MediaControlService.MediaControlBinder binder = (MediaControlService.MediaControlBinder) service;
            mediaControlService = binder.getService();

            mediaControlService.setOnMediaControlStartedListener(() -> setEnabled(true));
            mediaControlService.setOnMediaControlStoppedListener(() -> setEnabled(false));

            setEnabled(mediaControlService.isEnabled());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mediaControlService.setOnMediaControlStartedListener(null);
            mediaControlService.setOnMediaControlStoppedListener(null);
            mediaControlService = null;
        }
    };

    private void setStarting(boolean starting) {
        this.starting = starting;
        maybeEnableStartButton();
    }

    private void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            binding.buttonStart.hide();
            binding.buttonStop.setEnabled(true);
            binding.buttonStop.show();
        } else {
            binding.buttonStart.show();
            binding.buttonStop.hide();
        }
        maybeEnableStartButton();
    }

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

    private void maybeEnableStartButton() {
        binding.buttonStart.setEnabled(!enabled && !starting);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        MainViewModel model = new ViewModelProvider(this).get(MainViewModel.class);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        intent = new Intent(getApplication(), MediaControlService.class);

        binding.buttonStart.setOnClickListener(this::onStartButtonClicked);
        binding.buttonStop.setOnClickListener(this::onStopButtonClicked);

        binding.textSpeedThreshold.setOnClickListener(v -> {
            NumberPicker numberPicker = new NumberPicker(this);
            numberPicker.setMinValue(1);
            numberPicker.setMaxValue(30);
            numberPicker.setWrapSelectorWheel(false);
            numberPicker.setValue(model.getSpeedThreshold().getValue());

            new AlertDialog.Builder(this)
                    .setTitle(R.string.speed_threshold_title)
                    .setMessage(R.string.kph)
                    .setView(numberPicker)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> sharedPreferences
                            .edit()
                            .putInt(getString(R.string.speed_threshold_key), numberPicker.getValue())
                            .apply())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });

        model.getSpeedThreshold().observe(this, speedThreshold -> binding.textSpeedThreshold.setText(getString(R.string.n_kph, speedThreshold)));

        model.isInVehicleSelected().observe(this, inVehicleSelected -> {
            binding.imageInVehicle.setVisibility(inVehicleSelected ? View.VISIBLE : View.GONE);
            binding.textInVehicle.setVisibility(inVehicleSelected ? View.VISIBLE : View.GONE);
        });
        model.isOnBicycleSelected().observe(this, onBicycleSelected -> {
            binding.imageOnBicycle.setVisibility(onBicycleSelected ? View.VISIBLE : View.GONE);
            binding.textOnBicycle.setVisibility(onBicycleSelected ? View.VISIBLE : View.GONE);
        });
        model.isRunningSelected().observe(this, runningSelected -> {
            binding.imageRunning.setVisibility(runningSelected ? View.VISIBLE : View.GONE);
            binding.textRunning.setVisibility(runningSelected ? View.VISIBLE : View.GONE);
        });
        model.isWalkingSelected().observe(this, walkingSelected -> {
            binding.imageWalking.setVisibility(walkingSelected ? View.VISIBLE : View.GONE);
            binding.textWalking.setVisibility(walkingSelected ? View.VISIBLE : View.GONE);
        });

        model.getSelectedActivityCount().observe(this, count -> {
            binding.textSelectedActivityCount.setText(getString(R.string.n_types_selected, count));
            if (count > 1) {
                binding.viewMultiSelectedActivities.setVisibility(View.VISIBLE);
                binding.viewSingleSelectedActivity.setVisibility(View.GONE);
            } else {
                binding.viewMultiSelectedActivities.setVisibility(View.GONE);
                binding.viewSingleSelectedActivity.setVisibility(View.VISIBLE);
            }
        });

        model.getUsesActivityRecognition().observe(this, usesActivityRecognition -> {
            binding.switchActivityRecognition.setChecked(usesActivityRecognition);
            if (usesActivityRecognition) {
                binding.layout.setVisibility(View.VISIBLE);
                binding.divider.setVisibility(View.VISIBLE);
            } else {
                binding.layout.setVisibility(View.GONE);
                binding.divider.setVisibility(View.GONE);
            }
        });

        binding.switchActivityRecognition.setOnCheckedChangeListener((buttonView, isChecked) -> sharedPreferences
                .edit()
                .putBoolean(getString(R.string.activity_recognition_key), isChecked)
                .apply());

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

        activities = Arrays.asList(
                new Activity(R.string.in_vehicle_key, R.string.in_vehicle_title, binding.imageInVehicle),
                new Activity(R.string.on_bicycle_key, R.string.on_bicycle_title, binding.imageOnBicycle),
                new Activity(R.string.running_key, R.string.running_title, binding.imageRunning),
                new Activity(R.string.walking_key, R.string.walking_title, binding.imageWalking));

    }

    private void onStopButtonClicked(View v) {
        binding.buttonStop.setEnabled(false);
        mediaControlService.stopMediaControl();
        stopService(intent);
    }

    private void onStartButtonClicked(View v) {
        List<String> requestedPermissions = new ArrayList<>();
        if (sharedPreferences.getBoolean(getString(R.string.location_key), true) &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestedPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            requestedPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (sharedPreferences.getBoolean(getString(R.string.activity_recognition_key), true) &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestedPermissions.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }

        if (!requestedPermissions.isEmpty()) {
            setStarting(true);
            requestPermissionsLauncher.launch(requestedPermissions.toArray(new String[0]));
            return;
        }

        startForegroundService(intent);
    }

    private class Activity {
        final public String key;
        final public String title;
        final public ImageView imageView;

        Activity(int keyResId, int titleResId, ImageView imageView) {
            this.key = getString(keyResId);
            this.title = getString(titleResId);
            this.imageView = imageView;
        }
    }
}