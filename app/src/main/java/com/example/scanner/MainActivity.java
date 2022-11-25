package com.example.scanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.example.scanner.ml.Model;
import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity implements ImageAnalysis.Analyzer {

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private static final int MY_CAMERA_REQUEST_CODE = 100;

    private PreviewView previewView;
    private Button webButton;
    private TextView textView, confidenceView;
    private final int imageSize = 224;
    int maxPos = 0;
    float maxConfidence = 0;


    private ImageAnalysis imageAnalysis;

    ArrayList<String> labels = new ArrayList<>();

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
        webButton = findViewById(R.id.webBtn);
        confidenceView = findViewById(R.id.confidence);
        textView = findViewById(R.id.resultView);

        webButton.setVisibility(View.INVISIBLE);

        webButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_VIEW);

                String base = "https://www.google.com/search?q=";
                intent.setData(Uri.parse(base + labels.get(maxPos)));

                imageAnalysis.clearAnalyzer();
                startActivity(intent);
            }
        });

        // get labels from file
        try {
            AssetManager assetManager = getApplicationContext().getAssets();
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(assetManager.open("labels.txt"))
            );
            //int size = is.available();

            while(true) {
                String label = bufferedReader.readLine();

                if (label != null) {
                    labels.add(label);
                } else {
                    break;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!labels.isEmpty()) {
            for(String str: labels) {
                System.out.println(str);
            }
        }

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

    private void startCamera(@NonNull ProcessCameraProvider processCameraProvider) {
        processCameraProvider.unbindAll();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        // Preview
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Image Analysis
        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), this);

        // binding use case
        processCameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageAnalysis);
    }

    private void classifyImage(@NonNull Bitmap image) {
        try {
            Model model = Model.newInstance(getApplicationContext());

            // input reference
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);
            ByteBuffer byteBuffer = ByteBuffer.allocate(4 * imageSize * imageSize * 3);
            byteBuffer.order(ByteOrder.nativeOrder());

            // array of 224 * 224 pixels in image
            int[] intValue = new int[imageSize * imageSize];
            image.getPixels(intValue, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());

            // iterate through pixel for RGB
            int pixel = 0;

            for (int i=0; i<imageSize; ++i) {
                for (int j=0; j<imageSize; ++j) {
                    int val = intValue[pixel++]; //RGB
                    byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat((val & 0xFF) * (1.f / 255.f));
                }
            }

            inputFeature0.loadBuffer(byteBuffer);

            // run model interface
            Model.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeatures0 = outputs.getOutputFeature0AsTensorBuffer();

            float[] confidence = outputFeatures0.getFloatArray();

            // find the biggest confidence value
            for (int i=0; i<confidence.length; ++i) {
                if (confidence[i] > maxConfidence) {
                    maxConfidence = confidence[i];
                    maxPos = i;
                }
            }

            textView.setText(labels.get(maxPos));
            webButton.setVisibility(View.VISIBLE);
            confidenceView.setText(String.format("Confidence: %s%%", maxConfidence * 100));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        Bitmap bmp = previewView.getBitmap();
        assert bmp != null;

        bmp = Bitmap.createScaledBitmap(bmp, imageSize, imageSize, false);
        classifyImage(bmp);

        imageAnalysis.clearAnalyzer();
    }
}