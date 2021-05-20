package com.example.getofftopause;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

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

        Intent intent = new Intent(getApplication(), MediaControlService.class);
        enableButton.setOnClickListener(v -> {
            textInputLayout.setEnabled(false);
            enableButton.setEnabled(false);
            disableButton.setEnabled(true);

            startForegroundService(intent);
        });

        disableButton.setOnClickListener(v -> {
            textInputLayout.setEnabled(true);
            enableButton.setEnabled(true);
            disableButton.setEnabled(false);

            stopService(intent);
        });

    }
}