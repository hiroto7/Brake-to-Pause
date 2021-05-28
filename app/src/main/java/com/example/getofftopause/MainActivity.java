package com.example.getofftopause;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener, View.OnClickListener {

    private static final String TAG = "MainActivity";

    private boolean enabled;

    private ExtendedFloatingActionButton button;
    private SharedPreferences sharedPreferences;
    private Intent intent;
    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                button.setEnabled(true);

                if (result.containsValue(false)) {
                    Snackbar.make(button, getString(R.string.permission_denied), Snackbar.LENGTH_SHORT).setAnchorView(button).show();
                    return;
                }

                startForegroundService(intent);
            });
    private MediaControlService mediaControlService;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MediaControlService.MediaControlBinder binder = (MediaControlService.MediaControlBinder) service;
            mediaControlService = binder.getService();

            setEnabled(mediaControlService.isEnabled());
            mediaControlService.setOnMediaControlServiceSwitchListener(MainActivity.this::setEnabled);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    private void setEnabled(boolean enabled) {
        this.enabled = enabled;
        updateButtonTextAndIcon();
    }

    private void updateButtonEnabled() {
        if (sharedPreferences.getBoolean(getString(R.string.activity_recognition_key), true)) {
            button.setEnabled(
                    sharedPreferences.getBoolean(getString(R.string.in_vehicle_key), true) ||
                            sharedPreferences.getBoolean(getString(R.string.on_bicycle_key), true) ||
                            sharedPreferences.getBoolean(getString(R.string.running_key), false) ||
                            sharedPreferences.getBoolean(getString(R.string.walking_key), false));
        } else {
            button.setEnabled(sharedPreferences.getBoolean(getString(R.string.location_key), true));
        }
    }

    private void updateButtonTextAndIcon() {
        if (enabled) {
            button.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_baseline_stop_24));
            button.setText(R.string.disable_media_control);

            SettingsFragment settingsFragment = (SettingsFragment) getSupportFragmentManager().findFragmentById(R.id.settings);
            if (settingsFragment != null) {
                settingsFragment.getPreferenceScreen().setEnabled(false);
            }
        } else {
            button.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_baseline_music_note_24));
            button.setText(R.string.enable_media_control);

            SettingsFragment settingsFragment = (SettingsFragment) getSupportFragmentManager().findFragmentById(R.id.settings);
            if (settingsFragment != null) {
                settingsFragment.getPreferenceScreen().setEnabled(true);
            }
        }
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }

        button = findViewById(R.id.floatingActionButton);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        intent = new Intent(getApplication(), MediaControlService.class);

        button.setOnClickListener(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        updateButtonEnabled();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Arrays.asList(
                getString(R.string.location_key),
                getString(R.string.activity_recognition_key),
                getString(R.string.in_vehicle_key),
                getString(R.string.on_bicycle_key),
                getString(R.string.running_key),
                getString(R.string.walking_key)).contains(key)) {
            updateButtonEnabled();
        }
    }

    @Override
    public void onClick(View v) {
        if (enabled) {
            mediaControlService.disable();
            stopService(intent);
        } else {
            List<String> requestedPermissions = new ArrayList<>();

            if (sharedPreferences.getBoolean(getString(R.string.location_key), true) &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestedPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
                requestedPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }

            if (sharedPreferences.getBoolean(getString(R.string.activity_recognition_key), true) &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                requestedPermissions.add(Manifest.permission.ACTIVITY_RECOGNITION);
            }

            if (!requestedPermissions.isEmpty()) {
                button.setEnabled(false);
                requestPermissionsLauncher.launch(requestedPermissions.toArray(new String[0]));

                return;
            }

            startForegroundService(intent);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
        private SharedPreferences sharedPreferences;

        private void updateTitleAndSummary() {
            SwitchPreference activityRecognitionPreference = findPreference(getString(R.string.activity_recognition_key));

            if (activityRecognitionPreference == null) {
                return;
            }

            if (sharedPreferences.getBoolean(getString(R.string.location_key), true)) {
                activityRecognitionPreference.setTitle(R.string.activity_recognition_title_with_location);
                activityRecognitionPreference.setSummary(R.string.activity_recognition_summary_with_location);
            } else {
                activityRecognitionPreference.setTitle(R.string.activity_recognition_title_without_location);
                activityRecognitionPreference.setSummary(R.string.activity_recognition_summary_without_location);
            }
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            sharedPreferences = getPreferenceManager().getSharedPreferences();
            EditTextPreference speedPreference = findPreference(getString(R.string.speed_threshold_key));

            if (speedPreference != null) {
                speedPreference.setOnBindEditTextListener(
                        editText -> {
                            editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                            editText.setHint(R.string.speed_threshold_default_value);
                        });

                speedPreference.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) preference -> preference.getText() + " " + getString(R.string.kph));
                speedPreference.setOnPreferenceChangeListener((preference, newValue) -> !newValue.equals(""));
            }

            sharedPreferences.registerOnSharedPreferenceChangeListener(this);
            updateTitleAndSummary();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (!isAdded()) {
                return;
            }

            if (key.equals(getString(R.string.location_key))) {
                updateTitleAndSummary();
            }
        }
    }
}