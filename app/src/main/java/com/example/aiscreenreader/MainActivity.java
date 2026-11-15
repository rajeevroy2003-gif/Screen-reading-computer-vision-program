
package com.example.aiscreenreader;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.nio.ByteBuffer;

public class MainActivity extends Activity {

    private static final int REQUEST_CAPTURE = 100;

    private MediaProjectionManager manager;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private TextView output;

    private final Handler handler =
        new Handler(Looper.getMainLooper());

    private final TextRecognizer recognizer =
        TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        );

    private boolean serviceBound = false;
    private Intent serviceIntent;

    private final ServiceConnection connection =
        new ServiceConnection() {
            @Override
            public void onServiceConnected(
                ComponentName name, IBinder binder
            ) {
                serviceBound = true;
            }

            @Override
            public void onServiceDisconnected(
                ComponentName name
            ) {
                serviceBound = false;
            }
        };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        manager = (MediaProjectionManager)
            getSystemService(MEDIA_PROJECTION_SERVICE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 32, 24, 16);

        Button button = new Button(this);
        button.setText("Read Screen");

        output = new TextView(this);
        output.setText(
            "AI Screen Reader\n\n" +
            "Tap Read Screen to recognize text."
        );
        output.setTextSize(18);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(output);

        layout.addView(button);
        layout.addView(scroll);

        setContentView(layout);

        button.setOnClickListener(v -> {
            startActivityForResult(
                manager.createScreenCaptureIntent(),
                REQUEST_CAPTURE
            );
        });
    }

    @Override
    protected void onActivityResult(
        int requestCode,
        int resultCode,
        Intent data
    ) {
        super.onActivityResult(
            requestCode, resultCode, data
        );

        if (requestCode != REQUEST_CAPTURE) {
            return;
        }

        if (resultCode != RESULT_OK || data == null) {
            output.setText("Screen capture cancelled.");
            return;
        }

        serviceIntent = new Intent(
            this, ScreenCaptureService.class
        );

        startForegroundService(serviceIntent);

        boolean bound = bindService(
            serviceIntent,
            new ServiceConnection() {
                @Override
                public void onServiceConnected(
                    ComponentName name, IBinder binder
                ) {
                    serviceBound = true;
                    handler.postDelayed(
                        () -> beginProjection(resultCode, data),
                        500
                    );
                }

                @Override
                public void onServiceDisconnected(
                    ComponentName name
                ) {
                    serviceBound = false;
                }
            },
            Context.BIND_AUTO_CREATE
        );

        if (!bound) {
            output.setText(
                "Could not connect to capture service."
            );
        }
    }

    private void beginProjection(
        int resultCode, Intent data
    ) {
        try {
            projection = manager.getMediaProjection(
                resultCode, data
            );

            DisplayMetrics metrics =
                getResources().getDisplayMetrics();

            int width = metrics.widthPixels;
            int height = metrics.heightPixels;

            reader = ImageReader.newInstance(
                width,
                height,
                PixelFormat.RGBA_8888,
                2
            );

            projection.registerCallback(
                new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        handler.post(() -> stopCapture());
                    }
                },
                handler
            );

            display = projection.createVirtualDisplay(
                "AI_Screen_Reader",
                width,
                height,
                metrics.densityDpi,
                DisplayManager
                    .VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(),
                null,
                handler
            );

            output.setText("Capturing screen...");

            handler.postDelayed(
                () -> readScreen(), 800
            );

        } catch (Exception e) {
            output.setText(
                "Capture error: " + e.getMessage()
            );
            stopCapture();
        }
    }

    private void readScreen() {
        if (reader == null) return;

        Image image = reader.acquireLatestImage();

        if (image == null) {
            handler.postDelayed(
                () -> readScreen(), 250
            );
            return;
        }

        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();

            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();

            int width = image.getWidth();
            int height = image.getHeight();

            Bitmap padded = Bitmap.createBitmap(
                rowStride / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            );

            padded.copyPixelsFromBuffer(buffer);

            Bitmap bitmap = Bitmap.createBitmap(
                padded, 0, 0, width, height
            );

            padded.recycle();

            recognizer.process(
                InputImage.fromBitmap(bitmap, 0)
            )
            .addOnSuccessListener(result -> {
                String text = result.getText();

                output.setText(
                    text.isEmpty()
                        ? "No text detected."
                        : text
                );
            })
            .addOnFailureListener(e -> {
                output.setText(
                    "OCR error: " + e.getMessage()
                );
            })
            .addOnCompleteListener(task -> {
                bitmap.recycle();
                stopCapture();
            });

        } catch (Exception e) {
            output.setText(
                "Reading error: " + e.getMessage()
            );
            stopCapture();
        } finally {
            image.close();
        }
    }

    private void stopCapture() {
        if (display != null) {
            display.release();
            display = null;
        }

        if (reader != null) {
            reader.close();
            reader = null;
        }

        if (projection != null) {
            MediaProjection old = projection;
            projection = null;
            old.stop();
        }

        if (serviceIntent != null) {
            stopService(serviceIntent);
        }
    }

    @Override
    protected void onDestroy() {
        stopCapture();
        recognizer.close();
        super.onDestroy();
    }
}

