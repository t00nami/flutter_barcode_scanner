package com.amolg.flutterbarcodescanner;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;

import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.embedding.engine.plugins.lifecycle.FlutterLifecycleAdapter;

public class FlutterBarcodeScannerPlugin implements
        FlutterPlugin,
        ActivityAware,
        MethodCallHandler,
        EventChannel.StreamHandler {

    private static final String TAG = "FlutterBarcodeScanner";
    private static final String CHANNEL = "flutter_barcode_scanner";
    private static final int RC_BARCODE_CAPTURE = 9001;

    private MethodChannel channel;
    private EventChannel eventChannel;
    private Activity activity;
    private ActivityPluginBinding activityBinding;
    private Application applicationContext;
    private Result pendingResult;
    private Map<String, Object> arguments;
    private static EventChannel.EventSink barcodeStream;
    private Lifecycle lifecycle;
    private LifeCycleObserver observer;

    public static String lineColor = "";
    public static boolean isShowFlashIcon = false;
    public static boolean isContinuousScan = false;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        BinaryMessenger messenger = binding.getBinaryMessenger();
        channel = new MethodChannel(messenger, CHANNEL);
        channel.setMethodCallHandler(this);

        eventChannel = new EventChannel(messenger, "flutter_barcode_scanner_receiver");
        eventChannel.setStreamHandler(this);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        eventChannel.setStreamHandler(null);
        channel = null;
        eventChannel = null;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activityBinding = binding;
        activity = binding.getActivity();
        applicationContext = (Application) activity.getApplicationContext();

        observer = new LifeCycleObserver(activity);
        lifecycle = FlutterLifecycleAdapter.getActivityLifecycle(binding);
        lifecycle.addObserver(observer);

        activityBinding.addActivityResultListener((requestCode, resultCode, data) -> onActivityResult(requestCode, resultCode, data));
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        if (activityBinding != null) {
            activityBinding.removeActivityResultListener((requestCode, resultCode, data) -> onActivityResult(requestCode, resultCode, data));
            activityBinding = null;
        }
        if (lifecycle != null && observer != null) {
            lifecycle.removeObserver(observer);
            observer = null;
            lifecycle = null;
        }
        activity = null;
        applicationContext = null;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        try {
            pendingResult = result;

            if ("scanBarcode".equals(call.method)) {
                if (!(call.arguments instanceof Map)) {
                    throw new IllegalArgumentException("Plugin expects a Map parameter.");
                }
                arguments = (Map<String, Object>) call.arguments;
                lineColor = (String) arguments.get("lineColor");
                isShowFlashIcon = (boolean) arguments.get("isShowFlashIcon");
                if (lineColor == null || lineColor.isEmpty()) lineColor = "#DC143C";
                if (arguments.get("scanMode") != null) {
                    int mode = (int) arguments.get("scanMode");
                    BarcodeCaptureActivity.SCAN_MODE = mode;
                } else {
                    BarcodeCaptureActivity.SCAN_MODE = BarcodeCaptureActivity.SCAN_MODE_ENUM.QR.ordinal();
                }
                isContinuousScan = (boolean) arguments.get("isContinuousScan");

                startBarcodeScannerActivity((String) arguments.get("cancelButtonText"), isContinuousScan);
            }
        } catch (Exception e) {
            Log.e(TAG, "onMethodCall: " + e.getLocalizedMessage());
            result.error("ERROR", e.getLocalizedMessage(), null);
        }
    }

    private void startBarcodeScannerActivity(String buttonText, boolean continuousScan) {
        try {
            Intent intent = new Intent(activity, BarcodeCaptureActivity.class)
                    .putExtra("cancelButtonText", buttonText);
            if (continuousScan) {
                activity.startActivity(intent);
            } else {
                activity.startActivityForResult(intent, RC_BARCODE_CAPTURE);
            }
        } catch (Exception e) {
            Log.e(TAG, "startBarcodeScannerActivity: " + e.getLocalizedMessage());
        }
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_BARCODE_CAPTURE) {
            if (resultCode == CommonStatusCodes.SUCCESS && data != null) {
                try {
                    Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
                    pendingResult.success(barcode != null ? barcode.rawValue : "-1");
                } catch (Exception e) {
                    pendingResult.success("-1");
                }
            } else {
                pendingResult.success("-1");
            }
            pendingResult = null;
            arguments = null;
            return true;
        }
        return false;
    }

    @Override
    public void onListen(Object o, EventChannel.EventSink eventSink) {
        barcodeStream = eventSink;
    }

    @Override
    public void onCancel(Object o) {
        barcodeStream = null;
    }

    public static void onBarcodeScanReceiver(final Barcode barcode) {
        if (barcode != null && barcode.rawValue != null && !barcode.rawValue.isEmpty() && barcodeStream != null) {
            barcodeStream.success(barcode.rawValue);
        }
    }

    private static class LifeCycleObserver implements Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
        private final Activity activity;

        LifeCycleObserver(Activity activity) {
            this.activity = activity;
        }

        @Override public void onCreate(@NonNull LifecycleOwner owner) {}
        @Override public void onStart(@NonNull LifecycleOwner owner) {}
        @Override public void onResume(@NonNull LifecycleOwner owner) {}
        @Override public void onPause(@NonNull LifecycleOwner owner) {}
        @Override public void onStop(@NonNull LifecycleOwner owner) {}
        @Override public void onDestroy(@NonNull LifecycleOwner owner) {}

        @Override public void onActivityCreated(Activity activity, android.os.Bundle savedInstanceState) {}
        @Override public void onActivityStarted(Activity activity) {}
        @Override public void onActivityResumed(Activity activity) {}
        @Override public void onActivityPaused(Activity activity) {}
        @Override public void onActivitySaveInstanceState(Activity activity, android.os.Bundle outState) {}
        @Override public void onActivityStopped(Activity activity) {}
        @Override public void onActivityDestroyed(Activity activity) {}
    }
}
