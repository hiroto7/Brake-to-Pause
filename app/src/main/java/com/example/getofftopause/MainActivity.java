package com.example.getofftopause;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

public class MainActivity extends AppCompatActivity {
    private boolean enabled = false;

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

        ExtendedFloatingActionButton button = findViewById(R.id.floatingActionButton);

        Intent intent = new Intent(getApplication(), MediaControlService.class);

        button.setOnClickListener(v -> {
            if (enabled) {
                enabled = false;
                button.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_baseline_pedal_bike_24));
                button.setText(R.string.enable_media_control);

                stopService(intent);
            } else {
                enabled = true;
                button.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_baseline_stop_24));
                button.setText(R.string.disable_media_control);

                startForegroundService(intent);
            }
        });

    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            EditTextPreference speedPreference = findPreference("speed_threshold");

            if (speedPreference != null) {
                speedPreference.setOnBindEditTextListener(
                        editText -> {
                            editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                            editText.setHint(getString(R.string.speed_threshold_default_value));
                        });

                speedPreference.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) preference -> preference.getText() + " " + getString(R.string.kph));
                speedPreference.setOnPreferenceChangeListener((preference, newValue) -> !newValue.equals(""));
            }
        }
    }
}