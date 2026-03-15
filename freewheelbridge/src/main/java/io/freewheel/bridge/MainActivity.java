package io.freewheel.bridge;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

/**
 * Minimal activity - just starts the bridge service and finishes.
 * The factory_reset installer launches the main activity after install.
 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("SerialBridge", "MainActivity - starting BridgeService");
        Intent si = new Intent(this, BridgeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(si);
        } else {
            startService(si);
        }
        // Don't show any UI - just start the service
        finish();
    }
}
