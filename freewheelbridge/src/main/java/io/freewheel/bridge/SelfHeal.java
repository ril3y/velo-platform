package io.freewheel.bridge;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * Self-healing tasks that need the system uid. The launcher used to attempt some of these
 * itself ("pm enable", Settings.Global writes) but as a normal app those fail silently, so
 * they live here in the platform-signed bridge instead.
 *
 * Everything is best-effort: each step is isolated in try/catch and only logged.
 */
final class SelfHeal {
    private static final String TAG = "SerialBridge";

    static final String LAUNCHER_PKG = "io.freewheel.launcher";

    // Google Play services / Services Framework must stay enabled: disabling them on this
    // Rockchip build breaks the WiFi HAL and crash-loops Bluetooth. We only ever ENABLE them.
    private static final String[] MUST_BE_ENABLED = {
        "com.google.android.gms",
        "com.google.android.gsf",
    };

    private SelfHeal() {}

    static void runAll(Context context, String reason) {
        Log.i(TAG, "Self-heal (" + reason + ")");
        ensureOverlayPermission(context);
        ensureGooglePackagesEnabled(context);
    }

    /** Ensure the launcher keeps SYSTEM_ALERT_WINDOW (home gesture zone / ride overlay). */
    static void ensureOverlayPermission(Context context) {
        int uid;
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(LAUNCHER_PKG, 0);
            uid = ai.uid;
        } catch (PackageManager.NameNotFoundException e) {
            return; // launcher not installed, nothing to do
        } catch (Exception e) {
            Log.w(TAG, "Self-heal: cannot look up " + LAUNCHER_PKG + ": " + e.getMessage());
            return;
        }

        AppOpsManager aom = null;
        try {
            aom = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (aom != null) {
                int mode = aom.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, LAUNCHER_PKG);
                if (mode == AppOpsManager.MODE_ALLOWED) {
                    return;
                }
                Log.i(TAG, "Self-heal: SYSTEM_ALERT_WINDOW mode=" + mode + " for " + LAUNCHER_PKG + ", setting allow");
            }
        } catch (Exception e) {
            Log.w(TAG, "Self-heal: checkOp failed: " + e.getMessage());
        }

        // Preferred: hidden AppOpsManager.setMode(int op, int uid, String pkg, int mode) via
        // reflection. The bridge is platform-signed (system uid), so it holds UPDATE_APP_OPS_STATS.
        if (aom != null) {
            try {
                int op = AppOpsManager.class.getField("OP_SYSTEM_ALERT_WINDOW").getInt(null);
                AppOpsManager.class
                    .getMethod("setMode", int.class, int.class, String.class, int.class)
                    .invoke(aom, op, uid, LAUNCHER_PKG, AppOpsManager.MODE_ALLOWED);
                Log.i(TAG, "Self-heal: SYSTEM_ALERT_WINDOW allowed via AppOpsManager");
                return;
            } catch (Throwable t) {
                Log.w(TAG, "Self-heal: AppOpsManager.setMode reflection failed: " + t);
            }
        }

        // Fallback: the appops shell command (works as system uid).
        int rc = exec(new String[]{"appops", "set", LAUNCHER_PKG, "SYSTEM_ALERT_WINDOW", "allow"});
        Log.i(TAG, "Self-heal: 'appops set " + LAUNCHER_PKG + " SYSTEM_ALERT_WINDOW allow' rc=" + rc);
    }

    /** Re-enable GMS/GSF only if something disabled them. Never disables anything. */
    static void ensureGooglePackagesEnabled(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String pkg : MUST_BE_ENABLED) {
            try {
                int state = pm.getApplicationEnabledSetting(pkg);
                if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) {
                    pm.setApplicationEnabledSetting(pkg,
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, 0);
                    Log.w(TAG, "Self-heal: re-enabled " + pkg + " (was state " + state + ")");
                }
            } catch (IllegalArgumentException e) {
                // Package not installed
            } catch (Exception e) {
                Log.w(TAG, "Self-heal: could not check/enable " + pkg + ": " + e.getMessage());
            }
        }
    }

    private static int exec(String[] cmd) {
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            return p.waitFor();
        } catch (Exception e) {
            Log.w(TAG, "Self-heal: exec " + cmd[0] + " failed: " + e.getMessage());
            return -1;
        }
    }
}
