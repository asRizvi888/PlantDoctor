package com.example.scanner;

import static androidx.camera.core.ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.example.scanner.ml.Model;
import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity implements ImageAnalysis.Analyzer {

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private static final int MY_REQUEST_CODE = 100;

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private Button webButton, captureButton;
    private TextView textView, confidenceView;

    private LinearLayout linearLayout;

    private final int imageSize = 224;
    int maxPos = 0;
    float maxConfidence = 0;
    File path = new File("/storage/emulated/0/Pictures/plat-doctor.jpg");

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
            requestPermissions(new String[]{Manifest.permission.CAMERA}, MY_REQUEST_CODE);
        }

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_REQUEST_CODE);
        }

        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MY_REQUEST_CODE);
        }

        previewView = findViewById(R.id.preview); // instance variable for preview
        webButton = findViewById(R.id.webBtn);
        confidenceView = findViewById(R.id.confidence);
        textView = findViewById(R.id.resultView);
        linearLayout = findViewById(R.id.resultLayout);

        linearLayout.setVisibility(View.INVISIBLE);

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

        /*
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //TODO: capture current frame & pass to analyzer to validate classifier model
                @SuppressLint("SdCardPath")

                long timestamp = System.currentTimeMillis();
                ContentValues contentValues = new ContentValues();

                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "plant-doctor");
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");

                        imageCapture.takePicture(
                                new ImageCapture.OutputFileOptions.Builder(
                                        getContentResolver(),
                                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                        contentValues
                                ).build(),
                                getExecutor(),
                                new ImageCapture.OnImageSavedCallback() {
                                    @Override
                                    public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                                        // insert your code here.
                                        Bitmap temp = BitmapFactory.decodeFile(path.getAbsolutePath());
                                        Bitmap bitmap = Bitmap.createScaledBitmap(
                                                //MediaStore.Images.Media.getBitmap(getContentResolver(), Uri.fromFile(path)),
                                                temp, imageSize, imageSize, false
                                        );

                                        classifyImage(bitmap);

                                        // delete image
                                        path.delete();

                                    }
                                    @Override
                                    public void onError(ImageCaptureException error) {
                                        // insert your code here.
                                        error.printStackTrace();
                                        Toast.makeText(MainActivity.this, "Failed to scan", Toast.LENGTH_SHORT).show();
                                    }
                                }
                        );

                textView.setText(labels.get(maxPos));
                captureButton.setVisibility(View.INVISIBLE);
                linearLayout.setVisibility(View.VISIBLE);
            }
        });

         */

        // get labels from file
        try {
            AssetManager assetManager = getApplicationContext().getAssets();
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(assetManager.open("labels.txt"))
            );

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

    @NonNull
    private Executor getExecutor () {
        return ContextCompat.getMainExecutor(this);
    }

    private void startCamera(@NonNull ProcessCameraProvider processCameraProvider) {
        processCameraProvider.unbindAll();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        // Preview
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Capture Image
         imageCapture = new ImageCapture.Builder()
                 .setCaptureMode(CAPTURE_MODE_MINIMIZE_LATENCY)
                 .build();

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

            confidenceView.setText(String.format("Confidence: %.2f%%", maxConfidence * 100));
            textView.setText(labels.get(maxPos));
            linearLayout.setVisibility(View.VISIBLE);

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

        image.close();
        //imageAnalysis.clearAnalyzer();
    }
}