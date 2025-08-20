package com.amolg.flutterbarcodescannerexample;

import android.os.Bundle;
import androidx.annotation.NonNull;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import com.amolg.flutterbarcodescanner.FlutterBarcodeScannerPlugin;

public class MainActivity extends FlutterActivity {

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        // Registrar manualmente el plugin si es necesario
        flutterEngine.getPlugins().add(new FlutterBarcodeScannerPlugin());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // No es necesario GeneratedPluginRegistrant en embedding V2
    }
}
