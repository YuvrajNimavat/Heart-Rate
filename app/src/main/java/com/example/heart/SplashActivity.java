package com.example.heart;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SplashActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);


        // Delay for 3 seconds, then start MainActivity
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // Navigate to MainActivity after the splash screen
                Intent mainIntent = new Intent(SplashActivity.this, Introduction.class);
                startActivity(mainIntent);
                finish(); // Close the SplashActivity so the user can't go back to it
            }
        }, 3000);
    }
}