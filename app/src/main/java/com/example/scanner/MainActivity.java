package com.example.scanner;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity implements ImageAnalysis.Analyzer {

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private static final int MY_CAMERA_REQUEST_CODE = 100;

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // remove action bar from top
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // manage permission
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, MY_CAMERA_REQUEST_CODE);
        }

        previewView = findViewById(R.id.preview); // instance variable for preview

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider processCameraProvider = cameraProviderFuture.get();
                startCamera(processCameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void startCamera(ProcessCameraProvider processCameraProvider) {
        processCameraProvider.unbindAll();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        // Preview
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Image Capture
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        // Image Analysis
        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), this);

        // binding use case
        processCameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture, imageAnalysis);
    }

    // process image frame
    @Override
    public void analyze(@NonNull ImageProxy image) {
        // Log.d("main activity", "got the frame at: " + image.getImageInfo().getTimestamp());
        // Bitmap
        final Bitmap bitmap = previewView.getBitmap();

        for (int i=0; i<bitmap.getWidth(); ++i) {
            for (int j=0; j<bitmap.getHeight(); ++j) {
                // pixel wise color matching
                int pixel = bitmap.getPixel(i, j);

                int color = Color.rgb(pixel, pixel, pixel);

                // checks for GREEN color in pixel

                if (color == Color.GREEN) {
                    String url = "https://i.pinimg.com/originals/90/13/f7/9013f7b5eb6db0f41f4fd51d989491e7.gif"; // successful gif

                    Intent intent = new Intent(Intent.ACTION_VIEW); // Browser Intent
                    intent.setData(Uri.parse(url));
                    startActivity(intent);
                }
            }
        }

        image.close();
    }
}