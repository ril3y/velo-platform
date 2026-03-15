package io.freewheel.fit;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

/**
 * Client for the VeloLauncher Fitness API.
 *
 * Queries the launcher's ContentProvider for user profile, FTP, target power ranges,
 * and logs rides. Falls back to sensible defaults if the launcher is unavailable.
 *
 * Usage:
 *   VeloFitnessClient client = new VeloFitnessClient(context);
 *   FitnessConfig config = client.getFitnessConfig();
 *   PowerTarget target = client.getTargetPower(15); // resistance 15
 *   PowerTarget.EffortZone zone = target.zone(actualPower);
 */
public class VeloFitnessClient {

    private static final String TAG = "VeloFit";
    private static final String AUTHORITY = "io.freewheel.launcher.provider";
    private static final Uri URI_FITNESS_CONFIG = Uri.parse("content://" + AUTHORITY + "/fitness_config");
    private static final Uri URI_TARGET_POWER = Uri.parse("content://" + AUTHORITY + "/target_power");
    private static final Uri URI_RIDES = Uri.parse("content://" + AUTHORITY + "/rides");
    private static final Uri URI_PROFILE = Uri.parse("content://" + AUTHORITY + "/profile");
    private static final Uri URI_WORKOUTS = Uri.parse("content://" + AUTHORITY + "/workouts");

    private final ContentResolver resolver;
    private FitnessConfig cachedConfig;

    public VeloFitnessClient(Context context) {
        this.resolver = context.getContentResolver();
    }

    /**
     * Get the user's fitness configuration (FTP, max HR, weight, age).
     * Result is cached after first call. Returns defaults if launcher unavailable.
     */
    public FitnessConfig getFitnessConfig() {
        if (cachedConfig != null) return cachedConfig;

        try {
            Cursor c = resolver.query(URI_FITNESS_CONFIG, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                cachedConfig = new FitnessConfig(
                    c.getInt(c.getColumnIndexOrThrow("ftp")),
                    c.getInt(c.getColumnIndexOrThrow("maxHeartRate")),
                    c.getInt(c.getColumnIndexOrThrow("weightLbs")),
                    c.getInt(c.getColumnIndexOrThrow("age"))
                );
                c.close();
                Log.i(TAG, "Fitness config loaded: FTP=" + cachedConfig.ftp + "W");
                return cachedConfig;
            }
            if (c != null) c.close();
        } catch (Exception e) {
            Log.w(TAG, "VeloLauncher unavailable, using defaults: " + e.getMessage());
        }
        cachedConfig = FitnessConfig.defaults();
        return cachedConfig;
    }

    /**
     * Get the target power range for a specific resistance level (1-25).
     * Queries the launcher which computes from user profile + FTP.
     */
    public PowerTarget getTargetPower(int resistance) {
        try {
            Uri uri = Uri.parse("content://" + AUTHORITY + "/target_power/" + resistance);
            Cursor c = resolver.query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                PowerTarget target = new PowerTarget(
                    c.getInt(c.getColumnIndexOrThrow("resistance")),
                    c.getInt(c.getColumnIndexOrThrow("ftp")),
                    c.getInt(c.getColumnIndexOrThrow("targetLow")),
                    c.getInt(c.getColumnIndexOrThrow("targetHigh")),
                    c.getInt(c.getColumnIndexOrThrow("centerPower"))
                );
                c.close();
                return target;
            }
            if (c != null) c.close();
        } catch (Exception e) {
            Log.w(TAG, "Target power query failed, computing locally: " + e.getMessage());
        }
        // Fallback: compute locally from cached/default config
        return computeTargetPowerLocally(resistance);
    }

    /**
     * Get target power with a difficulty multiplier applied.
     * Multiplier scales the resistance before computing the power range.
     */
    public PowerTarget getTargetPower(int resistance, float difficultyMultiplier) {
        int scaledRes = Math.max(1, Math.min(25,
            Math.round(resistance * difficultyMultiplier)));
        return getTargetPower(scaledRes);
    }

    /**
     * Log a completed ride to VeloLauncher's ride history database.
     * Returns the ride URI if successful, null otherwise.
     */
    public Uri logRide(RideStats stats) {
        try {
            ContentValues cv = new ContentValues();
            cv.put("startTime", stats.startTime);
            cv.put("durationSeconds", stats.durationSeconds);
            cv.put("calories", stats.calories);
            cv.put("avgRpm", stats.avgRpm);
            cv.put("avgPowerWatts", stats.avgPower);
            cv.put("maxPowerWatts", stats.maxPower);
            cv.put("avgSpeedMph", stats.avgSpeedMph);
            cv.put("distanceMiles", stats.distanceMiles);
            cv.put("avgResistance", stats.avgResistance);
            cv.put("avgHeartRate", stats.avgHeartRate);
            cv.put("source", stats.sourcePackage);
            cv.put("sourceLabel", stats.sourceLabel);
            if (stats.workoutId != null) cv.put("workoutId", stats.workoutId);
            if (stats.workoutName != null) cv.put("workoutName", stats.workoutName);

            Uri result = resolver.insert(URI_RIDES, cv);
            Log.i(TAG, "Ride logged: " + stats.durationSeconds + "s, " + stats.calories + " cal → " + result);
            return result;
        } catch (Exception e) {
            Log.w(TAG, "Failed to log ride via ContentProvider: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get current workout session state (active, owner, elapsed time).
     * Returns null if launcher is unavailable.
     */
    public SessionState getSessionState() {
        try {
            Uri uri = Uri.parse("content://" + AUTHORITY + "/session");
            Cursor c = resolver.query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                SessionState state = new SessionState(
                    c.getInt(c.getColumnIndexOrThrow("active")) != 0,
                    c.getString(c.getColumnIndexOrThrow("ownerPackage")),
                    c.getString(c.getColumnIndexOrThrow("ownerLabel")),
                    c.getLong(c.getColumnIndexOrThrow("startTime")),
                    c.getInt(c.getColumnIndexOrThrow("elapsedSeconds"))
                );
                c.close();
                return state;
            }
            if (c != null) c.close();
        } catch (Exception e) {
            Log.w(TAG, "Session state query failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get the latest sensor data snapshot from the active session.
     * Returns null if no active session or launcher unavailable.
     */
    public SensorSnapshot getLatestSensor() {
        try {
            Uri uri = Uri.parse("content://" + AUTHORITY + "/session/sensor");
            Cursor c = resolver.query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                SensorSnapshot snap = new SensorSnapshot(
                    c.getInt(c.getColumnIndexOrThrow("resistance")),
                    c.getInt(c.getColumnIndexOrThrow("rpm")),
                    c.getInt(c.getColumnIndexOrThrow("tilt")),
                    c.getFloat(c.getColumnIndexOrThrow("power")),
                    c.getLong(c.getColumnIndexOrThrow("crankRevCount")),
                    c.getInt(c.getColumnIndexOrThrow("crankEventTime")),
                    c.getInt(c.getColumnIndexOrThrow("heartRate")),
                    c.getString(c.getColumnIndexOrThrow("hrmDeviceName"))
                );
                c.close();
                return snap;
            }
            if (c != null) c.close();
        } catch (Exception e) {
            Log.w(TAG, "Sensor snapshot query failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Import a workout definition into the launcher's workout database.
     * The JSON should follow the workout format with segments.
     * Returns the URI of the imported workout, or null on failure.
     */
    public Uri importWorkout(String workoutJson) {
        try {
            android.content.ContentValues cv = new android.content.ContentValues();
            org.json.JSONObject obj = new org.json.JSONObject(workoutJson);
            cv.put("id", obj.optString("id", java.util.UUID.randomUUID().toString()));
            cv.put("name", obj.optString("name", "Imported Workout"));
            cv.put("description", obj.optString("description", ""));
            cv.put("durationMinutes", obj.optInt("durationMinutes", 0));
            cv.put("type", obj.optString("type", "custom"));
            cv.put("category", obj.optString("category", "Imported"));
            cv.put("coach", obj.optString("coach", ""));
            cv.put("optionalMedia", obj.optBoolean("optionalMedia", false) ? 1 : 0);
            cv.put("color", obj.optString("color", "#22D3EE"));
            cv.put("segmentsJson", obj.optJSONArray("segments") != null
                ? obj.getJSONArray("segments").toString() : "[]");
            cv.put("source", obj.optString("source", "external"));
            cv.put("sourceLabel", obj.optString("sourceLabel", "External App"));

            Uri result = resolver.insert(URI_WORKOUTS, cv);
            Log.i(TAG, "Workout imported: " + cv.getAsString("name") + " → " + result);
            return result;
        } catch (Exception e) {
            Log.w(TAG, "Failed to import workout: " + e.getMessage());
            return null;
        }
    }

    /** Clear cached config (e.g., if user updates their profile) */
    public void invalidateCache() {
        cachedConfig = null;
    }

    /** Check if the launcher is reachable */
    public boolean isAvailable() {
        try {
            Cursor c = resolver.query(URI_FITNESS_CONFIG, null, null, null, null);
            boolean available = c != null && c.getCount() > 0;
            if (c != null) c.close();
            return available;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Local fallback computation — same formula as the launcher uses.
     * FTP × effortFraction(resistance) ± 15%
     */
    private PowerTarget computeTargetPowerLocally(int resistance) {
        FitnessConfig config = getFitnessConfig();
        int res = Math.max(1, Math.min(25, resistance));
        float effortFraction = 0.10f + (res - 1) * (0.90f / 24f);
        float centerPower = config.ftp * effortFraction;
        int lo = Math.max(5, (int)(centerPower * 0.85f));
        int hi = Math.max(10, (int)(centerPower * 1.15f));
        return new PowerTarget(res, config.ftp, lo, hi, (int) centerPower);
    }
}
