package com.amolg.flutterbarcodescanner;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.IOException;

public class BarcodeCaptureActivity extends Activity {

    public static final String BarcodeObject = "Barcode";
    private static final String TAG = "BarcodeCaptureActivity";

    public enum SCAN_MODE_ENUM {
        DEFAULT,
        QR,
        BARCODE
    }

    public static int SCAN_MODE = SCAN_MODE_ENUM.QR.ordinal();

    private CameraSource cameraSource;
    private BarcodeDetector barcodeDetector;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            barcodeDetector = new BarcodeDetector.Builder(this)
                    .setBarcodeFormats(Barcode.ALL_FORMATS)
                    .build();

            if (!barcodeDetector.isOperational()) {
                Log.e(TAG, "Detector dependencies are not yet available.");
                setResult(CommonStatusCodes.ERROR);
                finish();
                return;
            }

            barcodeDetector.setProcessor(new Detector.Processor<Barcode>() {
                @Override
                public void release() {
                }

                @Override
                public void receiveDetections(Detector.Detections<Barcode> detections) {
                    if (detections != null && detections.getDetectedItems().size() > 0) {
                        Barcode barcode = detections.getDetectedItems().valueAt(0);

                        // Devolver resultado
                        Intent data = new Intent();
                        data.putExtra(BarcodeObject, barcode);
                        setResult(CommonStatusCodes.SUCCESS, data);

                        // Si no es escaneo continuo, cerramos la activity
                        if (!FlutterBarcodeScannerPlugin.isContinuousScan) {
                            finish();
                        } else {
                            // En modo continuo, emitimos al EventChannel
                            FlutterBarcodeScannerPlugin.onBarcodeScanReceiver(barcode);
                        }
                    }
                }
            });

            cameraSource = new CameraSource.Builder(this, barcodeDetector)
                    .setFacing(CameraSource.CAMERA_FACING_BACK)
                    .setAutoFocusEnabled(true)
                    .setRequestedPreviewSize(1600, 1024)
                    .setRequestedFps(15.0f)
                    .build();

        } catch (Exception e) {
            Log.e(TAG, "Error initializing barcode scanner: " + e.getMessage());
            setResult(CommonStatusCodes.ERROR);
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (cameraSource != null) {
                cameraSource.start();
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not start camera source.", e);
            cameraSource.release();
            cameraSource = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraSource != null) {
            cameraSource.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraSource != null) {
            cameraSource.release();
        }
        if (barcodeDetector != null) {
            barcodeDetector.release();
        }
    }
}
