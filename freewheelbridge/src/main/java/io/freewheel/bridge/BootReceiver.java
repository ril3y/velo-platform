package io.freewheel.bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "SerialBridge";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Boot completed - starting BridgeService");
            Intent si = new Intent(context, BridgeService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(si);
            } else {
                context.startService(si);
            }

            // Re-apply iptables OTA blocks (rules don't persist across reboot)
            blockOtaNetwork(context);

            // Launcher overlay appop + GMS/GSF enabled (needs system uid; off the main thread)
            final PendingResult pending = goAsync();
            final Context appContext = context.getApplicationContext();
            new Thread(() -> {
                try {
                    SelfHeal.runAll(appContext, "boot");
                } catch (Throwable t) {
                    Log.w(TAG, "Self-heal on boot failed: " + t);
                } finally {
                    pending.finish();
                }
            }, "SelfHealBoot").start();
        }
    }

    /**
     * Block outbound network for OTA/Asset Manager apps via iptables.
     * SerialBridge runs as system UID so it can execute iptables.
     * Uses -C (check) before -A (append) to avoid duplicates.
     */
    private void blockOtaNetwork(Context context) {
        String[] packages = {
            "com.nautilus.g4assetmanager",
            "com.redbend.client",
            "com.redbend.vdmc"
        };
        for (String pkg : packages) {
            try {
                int uid = context.getPackageManager().getApplicationInfo(pkg, 0).uid;
                if (uid < android.os.Process.FIRST_APPLICATION_UID) {
                    // A shared system uid (e.g. 1000) would drop ALL system traffic,
                    // including loopback/ADB-over-WiFi — never install that rule.
                    Log.w(TAG, "Not blocking " + pkg + ": uid " + uid + " is a system uid");
                    continue;
                }
                String uidStr = String.valueOf(uid);
                // Check if rule exists, add if not
                Runtime rt = Runtime.getRuntime();
                Process check = rt.exec(new String[]{
                    "iptables", "-C", "OUTPUT", "-m", "owner",
                    "--uid-owner", uidStr, "-j", "DROP"
                });
                if (check.waitFor() != 0) {
                    Process add = rt.exec(new String[]{
                        "iptables", "-A", "OUTPUT", "-m", "owner",
                        "--uid-owner", uidStr, "-j", "DROP"
                    });
                    add.waitFor();
                    Log.d(TAG, "Blocked network for " + pkg + " (uid=" + uid + ")");
                }
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                // Package not installed, skip
            } catch (Exception e) {
                Log.w(TAG, "Failed to block " + pkg + ": " + e.getMessage());
            }
        }
    }
}
