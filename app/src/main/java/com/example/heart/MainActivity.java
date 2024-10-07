package com.example.heart;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.view.Gravity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "HeartRateMonitor";
    private static final int CAMERA_PERMISSION_REQUEST = 100;
    private static final int FPS = 30;
    private static final AtomicBoolean processing = new AtomicBoolean(false);

    private Camera camera;
    private TextureView textureView;
    private CircularProgressIndicator circularProgressIndicator;
    private TextView textViewHeartbeat;
    private TextView textViewInstruction;
    private TextView textViewProgress;
    private Vibrator vibrator;
    private Runnable vibrationRunnable;
    LottieAnimationView lottieAnimationView;


    private Handler handler = new Handler();
    private int progressValue = 0;
    private boolean isMeasuring = false;
    private boolean isFingerDetected = false;

    private int[] averageArray = new int[4];
    private int averageArrayIndex = 0;

    private int beatsIndex = 0;
    private int[] beatsArray = new int[3];
    private double beats = 0;
    private long startTime = 0;

    private int currentHeartRate = 0;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = findViewById(R.id.textureView);
        circularProgressIndicator = findViewById(R.id.circularProgressIndicator);
        textViewHeartbeat = findViewById(R.id.textViewHeartbeat);
        textViewInstruction = findViewById(R.id.textViewInstruction);
        textViewProgress = findViewById(R.id.textViewProgress);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        lottieAnimationView=findViewById(R.id.lottieAnimationView1);



        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        } else {
            initializeCamera();
        }
        lottieAnimationView.setVisibility(View.VISIBLE);
        startBlinkingInstruction();

        vibrationRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFingerDetected && vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(200);
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(vibrationRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(vibrationRunnable);
    }

    private void initializeCamera() {
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                openCamera(surface);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                // Ignored, Camera does not support changing preview size on the fly
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                releaseCamera();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                // Update logic here if needed
            }
        });
    }

    private void openCamera(SurfaceTexture surface) {
        try {
            camera = Camera.open();
            Camera.Parameters parameters = camera.getParameters();
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            camera.setParameters(parameters);

            try {
                camera.setPreviewTexture(surface);
            } catch (IOException e) {
                e.printStackTrace();
            }

            Camera.Size previewSize = camera.getParameters().getPreviewSize();
            textureView.setLayoutParams(new FrameLayout.LayoutParams(
                    previewSize.width, previewSize.height, Gravity.CENTER));

            camera.setDisplayOrientation(90);
            camera.startPreview();

            camera.setPreviewCallback(previewCallback);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    private Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera cam) {
            if (data == null) return;
            Camera.Size size = cam.getParameters().getPreviewSize();
            if (size == null) return;

            if (!processing.compareAndSet(false, true)) return;

            int width = size.width;
            int height = size.height;

            int imgAvg = ImageProcessing.decodeYUV420SPtoRedAvg(data.clone(), height, width);
            Log.d(TAG, "Image Average: " + imgAvg);

            if (imgAvg == 0 || imgAvg == 255) {
                processing.set(false);
                return;
            }

            int averageArrayAvg = 0;
            int averageArrayCnt = 0;
            for (int i = 0; i < averageArray.length; i++) {
                if (averageArray[i] > 0) {
                    averageArrayAvg += averageArray[i];
                    averageArrayCnt++;
                }
            }
            int rollingAverage = (averageArrayCnt > 0) ? (averageArrayAvg / averageArrayCnt) : 0;
            int newType = imgAvg < rollingAverage ? 0 : 1;

            if (averageArrayIndex == averageArray.length) averageArrayIndex = 0;
            averageArray[averageArrayIndex] = imgAvg;
            averageArrayIndex++;

            // Check if a finger is detected
            if (imgAvg > 200) {
                if (!isFingerDetected) {
                    isFingerDetected = true;
                    Log.d(TAG, "Finger detected");
                    runOnUiThread(() -> {
                        textViewInstruction.setText("Keep your finger still");
                        textViewInstruction.clearAnimation();
                    });
                }
                if (!isMeasuring) {
                    startMeasurement();
                }
            } else {
                if (isFingerDetected) {
                    isFingerDetected = false;
                    Log.d(TAG, "Finger removed");
                    if (isMeasuring) {
                        stopMeasurement();
                    }
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Please put your finger on the camera", Toast.LENGTH_SHORT).show();
                        textViewInstruction.setText("Place your finger on the camera");
                        startBlinkingInstruction();
                    });
                }
                processing.set(false);
                return;
            }

            if (newType != beatsArray[beatsIndex]) {
                beatsArray[beatsIndex] = newType;
                beatsIndex = (beatsIndex + 1) % 3;

                if (newType == 1) {
                    beats++;
                    Log.d(TAG, "BEAT!! beats=" + beats);
                }
            }

            long endTime = System.currentTimeMillis();
            double totalTimeInSecs = (endTime - startTime) / 1000d;
            if (totalTimeInSecs >= 5) {
                double bps = (beats / totalTimeInSecs);
                int dpm = (int) (bps * 60d);
                Log.d(TAG, "Calculated BPM: " + dpm);
                if (dpm < 40 || dpm > 110) {
                    Log.d(TAG, "Invalid BPM, resetting measurement");
                    startTime = System.currentTimeMillis();
                    beats = 0;
                    processing.set(false);
                    return;
                }

                if (beatsArray[0] == 1 && beatsArray[1] == 1 && beatsArray[2] == 1) {
                    currentHeartRate = dpm;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textViewHeartbeat.setText(String.valueOf(currentHeartRate));

                            Log.d(TAG, "Updated heart rate: " + currentHeartRate);
                        }
                    });
                }
                startTime = System.currentTimeMillis();
                beats = 0;
            }
            processing.set(false);
        }
    };

    private void startMeasurement() {
        isMeasuring = true;
        startTime = System.currentTimeMillis();
        beats = 0;
        progressValue = 0;
        currentHeartRate = 0;
        Log.d(TAG, "Starting measurement");
        handler.post(updateProgress);
    }

    private void stopMeasurement() {
        isMeasuring = false;
        handler.removeCallbacks(updateProgress);
        progressValue = 0;
        Log.d(TAG, "Stopping measurement");
        runOnUiThread(() -> {
            circularProgressIndicator.setProgress(0);
            textViewProgress.setText("0%");
            textViewHeartbeat.setText("--");
            textViewHeartbeat.setVisibility(View.VISIBLE);
            textViewProgress.setVisibility(View.VISIBLE);
            circularProgressIndicator.setVisibility(View.VISIBLE);
            lottieAnimationView.setVisibility(View.VISIBLE);

        });
    }

    private Runnable updateProgress = new Runnable() {
        @Override
        public void run() {
            if (progressValue < 100 && isMeasuring) {
                progressValue++;
                runOnUiThread(() -> {
                    circularProgressIndicator.setProgress(progressValue);
                    textViewProgress.setText(progressValue + "%");
                });
                handler.postDelayed(this, 150); // 15 seconds total duration
            } else {
                isMeasuring = false;
                if (progressValue >= 100) {
                    Log.d(TAG, "Measurement completed. Final heart rate: " + currentHeartRate);
                    if (currentHeartRate > 0) {
                        showFinalHeartRate(currentHeartRate);
                    } else {
                        Log.d(TAG, "No valid heart rate detected");
                        showMeasurementFailed();
                    }
                }
            }
        }
    };

    private void showFinalHeartRate(int heartRate) {
        runOnUiThread(() -> {
            circularProgressIndicator.setVisibility(View.GONE);
            textViewProgress.setVisibility(View.GONE);
            textViewHeartbeat.setText(heartRate + " BPM");
            textViewHeartbeat.setTextSize(36); // Increase text size
            textViewHeartbeat.setVisibility(View.VISIBLE);
            textViewInstruction.setText("Measurement complete");
            textViewInstruction.clearAnimation();
            lottieAnimationView.setVisibility(View.GONE);

            // Center the heart rate display
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            );
            params.gravity = Gravity.CENTER;
            textViewHeartbeat.setLayoutParams(params);
        });
    }

    private void showMeasurementFailed() {
        runOnUiThread(() -> {
            circularProgressIndicator.setVisibility(View.GONE);
            textViewProgress.setVisibility(View.GONE);
            textViewHeartbeat.setText("Measurement failed");
            lottieAnimationView.setVisibility(View.GONE);
            textViewHeartbeat.setTextSize(24); // Slightly smaller than success message
            textViewHeartbeat.setVisibility(View.VISIBLE);
            textViewInstruction.setText("Please try again");
            textViewInstruction.clearAnimation();

            // Center the failure message
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            );
            params.gravity = Gravity.CENTER;
            textViewHeartbeat.setLayoutParams(params);
        });
    }

    private void startBlinkingInstruction() {
        Animation anim = new AlphaAnimation(0.0f, 1.0f);
        anim.setDuration(1000);
        anim.setStartOffset(20);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        textViewInstruction.startAnimation(anim);
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    private static class ImageProcessing {
        private static int decodeYUV420SPtoRedAvg(byte[] yuv420sp, int height, int width) {
            if (yuv420sp == null) return 0;
            final int frameSize = width * height;
            int sum = 0;
            for (int j = 0, yp = 0; j < height; j++) {
                int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
                for (int i = 0; i < width; i++, yp++) {
                    int y = (0xff & yuv420sp[yp]) - 16;
                    if (y < 0) y = 0;
                    if ((i & 1) == 0) {
                        v = (0xff & yuv420sp[uvp++]) - 128;
                        u = (0xff & yuv420sp[uvp++]) - 128;
                    }
                    int y1192 = 1192 * y;
                    int r = (y1192 + 1634 * v);
                    r = Math.min(Math.max(r, 0), 262143);
                    sum += (r >> 10) & 0xff;
                }
            }
            return sum / frameSize;
        }
    }
}