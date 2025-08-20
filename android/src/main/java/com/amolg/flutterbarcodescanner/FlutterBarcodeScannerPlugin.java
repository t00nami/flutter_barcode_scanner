package com.amolg.flutterbarcodescanner;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
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
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.embedding.engine.plugins.lifecycle.FlutterLifecycleAdapter;

public class FlutterBarcodeScannerPlugin implements MethodChannel.MethodCallHandler,
        EventChannel.StreamHandler, FlutterPlugin, ActivityAware {

    private static final String CHANNEL = "flutter_barcode_scanner";
    private static final String TAG = FlutterBarcodeScannerPlugin.class.getSimpleName();
    private static final int RC_BARCODE_CAPTURE = 9001;

    private MethodChannel channel;
    private EventChannel eventChannel;
    private static EventChannel.EventSink barcodeStream;

    private Activity activity;
    private Application applicationContext;
    private ActivityPluginBinding activityBinding;
    private FlutterPluginBinding pluginBinding;
    private Lifecycle lifecycle;
    private LifeCycleObserver observer;

    private Map<String, Object> arguments;
    public static String lineColor = "";
    public static boolean isShowFlashIcon = false;
    public static boolean isContinuousScan = false;

    public FlutterBarcodeScannerPlugin() {}

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        pluginBinding = binding;
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        pluginBinding = null;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activityBinding = binding;
        activity = binding.getActivity();
        applicationContext = (Application) activity.getApplicationContext();
        lifecycle = FlutterLifecycleAdapter.getActivityLifecycle(binding);
        observer = new LifeCycleObserver(activity);
        lifecycle.addObserver(observer);

        setupChannels(pluginBinding.getBinaryMessenger());
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
        clearPluginSetup();
    }

    private void setupChannels(BinaryMessenger messenger) {
        channel = new MethodChannel(messenger, CHANNEL);
        channel.setMethodCallHandler(this);

        eventChannel = new EventChannel(messenger, "flutter_barcode_scanner_receiver");
        eventChannel.setStreamHandler(this);
    }

    private void clearPluginSetup() {
        if (activityBinding != null) {
            activityBinding.removeActivityResultListener((requestCode, resultCode, data) -> onActivityResult(requestCode, resultCode, data));
            activityBinding = null;
        }
        if (lifecycle != null && observer != null) {
            lifecycle.removeObserver(observer);
            observer = null;
            lifecycle = null;
        }
        if (channel != null) {
            channel.setMethodCallHandler(null);
            channel = null;
        }
        if (eventChannel != null) {
            eventChannel.setStreamHandler(null);
            eventChannel = null;
        }
        activity = null;
        applicationContext = null;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        try {
            if (call.method.equals("scanBarcode")) {
                if (!(call.arguments instanceof Map)) {
                    throw new IllegalArgumentException("Plugin expects a map as parameter");
                }
                arguments = (Map<String, Object>) call.arguments;
                lineColor = (String) arguments.getOrDefault("lineColor", "#DC143C");
                isShowFlashIcon = (boolean) arguments.getOrDefault("isShowFlashIcon", false);
                isContinuousScan = (boolean) arguments.getOrDefault("isContinuousScan", false);

                int scanMode = BarcodeCaptureActivity.SCAN_MODE_ENUM.QR.ordinal();
                if (arguments.get("scanMode") != null) {
                    scanMode = (int) arguments.get("scanMode");
                }
                BarcodeCaptureActivity.SCAN_MODE = scanMode;

                startBarcodeScannerActivity((String) arguments.get("cancelButtonText"), isContinuousScan);
                result.success(null);
            } else {
                result.notImplemented();
            }
        } catch (Exception e) {
            Log.e(TAG, "onMethodCall: " + e.getMessage());
            result.error("ERROR", e.getMessage(), null);
        }
    }

    private void startBarcodeScannerActivity(String buttonText, boolean continuousScan) {
        Intent intent = new Intent(activity, BarcodeCaptureActivity.class)
                .putExtra("cancelButtonText", buttonText);
        if (continuousScan) {
            activity.startActivity(intent);
        } else {
            activity.startActivityForResult(intent, RC_BARCODE_CAPTURE);
        }
    }

    private boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_BARCODE_CAPTURE) {
            if (resultCode == CommonStatusCodes.SUCCESS && data != null) {
                Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
                if (barcode != null) {
                    pendingResultSuccess(barcode.rawValue);
                } else {
                    pendingResultSuccess("-1");
                }
            } else {
                pendingResultSuccess("-1");
            }
            return true;
        }
        return false;
    }

    private void pendingResultSuccess(String value) {
        if (barcodeStream != null) {
            barcodeStream.success(value);
        }
    }

    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        barcodeStream = events;
    }

    @Override
    public void onCancel(Object arguments) {
        barcodeStream = null;
    }

    public static void onBarcodeScanReceiver(final Barcode barcode) {
        if (barcode != null && !barcode.displayValue.isEmpty() && barcodeStream != null) {
            barcodeStream.success(barcode.rawValue);
        }
    }

    private static class LifeCycleObserver implements DefaultLifecycleObserver {
        private final Activity activity;

        LifeCycleObserver(Activity activity) {
            this.activity = activity;
        }

        @Override
        public void onStop(@NonNull LifecycleOwner owner) {}

        @Override
        public void onDestroy(@NonNull LifecycleOwner owner) {}
    }
}
