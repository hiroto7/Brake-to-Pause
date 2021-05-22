package com.example.getofftopause;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
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

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener, View.OnClickListener {

    private static final String TAG = "MainActivity";

    private boolean enabled;

    private ExtendedFloatingActionButton button;
    private SharedPreferences sharedPreferences;
    private Intent intent;

    private void updateButtonEnabled() {
        button.setEnabled(
                sharedPreferences.getBoolean(getString(R.string.location_key), true) ||
                        sharedPreferences.getBoolean(getString(R.string.activity_recognition_key), true));
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

        enabled = false;
        updateButtonEnabled();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.location_key)) || key.equals(getString(R.string.activity_recognition_key))) {
            updateButtonEnabled();
        }
    }

    @Override
    public void onClick(View v) {
        if (enabled) {
            enabled = false;
            button.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_baseline_music_note_24));
            button.setText(R.string.enable_media_control);

            stopService(intent);
        } else {
            enabled = true;
            button.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_baseline_stop_24));
            button.setText(R.string.disable_media_control);

            startForegroundService(intent);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
        private SharedPreferences sharedPreferences;
        private SwitchPreference locationPreference;
        private final ActivityResultLauncher<String[]> requestLocationPermissionsLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    if (result.containsValue(true)) {
                        locationPreference.setChecked(true);
                    }
                });
        private SwitchPreference activityRecognitionPreference;
        private final ActivityResultLauncher<String> requestActivityRecognitionPermissionLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        activityRecognitionPreference.setChecked(true);
                    }
                });

        private void updateTitleAndSummary() {
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
            locationPreference = findPreference(getString(R.string.location_key));
            activityRecognitionPreference = findPreference(getString(R.string.activity_recognition_key));

            if (speedPreference != null) {
                speedPreference.setOnBindEditTextListener(
                        editText -> {
                            editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                            editText.setHint(R.string.speed_threshold_default_value);
                        });

                speedPreference.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) preference -> preference.getText() + " " + getString(R.string.kph));
                speedPreference.setOnPreferenceChangeListener((preference, newValue) -> !newValue.equals(""));
            }

            if (locationPreference != null) {
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    locationPreference.setChecked(false);
                }

                locationPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    if (newValue.equals(true) &&
                            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                        requestLocationPermissionsLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
                        return false;
                    }

                    return true;
                });

                sharedPreferences.registerOnSharedPreferenceChangeListener(this);
            }

            if (activityRecognitionPreference != null) {
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                    activityRecognitionPreference.setChecked(false);
                }

                activityRecognitionPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    if (newValue.equals(true) &&
                            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {

                        requestActivityRecognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION);
                        return false;
                    }

                    return true;
                });

                updateTitleAndSummary();
            }
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