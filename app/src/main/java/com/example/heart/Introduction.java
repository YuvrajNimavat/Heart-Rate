package com.example.heart;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class Introduction extends AppCompatActivity {


    Button gotItBtn;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_introduction);


        gotItBtn = findViewById(R.id.gotItBtn);


        gotItBtn.setOnClickListener(v -> {
            // Navigate to MainActivity after the splash screen
            Intent mainIntent = new Intent(Introduction.this, MainActivity.class);
            startActivity(mainIntent);
            finish(); // Close the SplashActivity so the user can't go back to it
        });

        }
    }
