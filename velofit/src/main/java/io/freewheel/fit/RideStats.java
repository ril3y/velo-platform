package io.freewheel.fit;

/**
 * Ride statistics to log to VeloLauncher.
 * Use the Builder for convenient construction.
 */
public class RideStats {
    public final long startTime;
    public final int durationSeconds;
    public final int calories;
    public final int avgRpm;
    public final int avgPower;
    public final int maxPower;
    public final float avgSpeedMph;
    public final float distanceMiles;
    public final int avgResistance;
    public final int avgHeartRate;
    public final String sourcePackage;
    public final String sourceLabel;
    public final String workoutId;
    public final String workoutName;

    private RideStats(Builder b) {
        this.startTime = b.startTime;
        this.durationSeconds = b.durationSeconds;
        this.calories = b.calories;
        this.avgRpm = b.avgRpm;
        this.avgPower = b.avgPower;
        this.maxPower = b.maxPower;
        this.avgSpeedMph = b.avgSpeedMph;
        this.distanceMiles = b.distanceMiles;
        this.avgResistance = b.avgResistance;
        this.avgHeartRate = b.avgHeartRate;
        this.sourcePackage = b.sourcePackage;
        this.sourceLabel = b.sourceLabel;
        this.workoutId = b.workoutId;
        this.workoutName = b.workoutName;
    }

    public static class Builder {
        long startTime = System.currentTimeMillis();
        int durationSeconds, calories, avgRpm, avgPower, maxPower;
        float avgSpeedMph, distanceMiles;
        int avgResistance, avgHeartRate;
        String sourcePackage = "unknown";
        String sourceLabel = "Unknown";
        String workoutId, workoutName;

        public Builder startTime(long v) { startTime = v; return this; }
        public Builder durationSeconds(int v) { durationSeconds = v; return this; }
        public Builder calories(int v) { calories = v; return this; }
        public Builder avgRpm(int v) { avgRpm = v; return this; }
        public Builder avgPower(int v) { avgPower = v; return this; }
        public Builder maxPower(int v) { maxPower = v; return this; }
        public Builder avgSpeedMph(float v) { avgSpeedMph = v; return this; }
        public Builder distanceMiles(float v) { distanceMiles = v; return this; }
        public Builder avgResistance(int v) { avgResistance = v; return this; }
        public Builder avgHeartRate(int v) { avgHeartRate = v; return this; }
        public Builder source(String pkg, String label) { sourcePackage = pkg; sourceLabel = label; return this; }
        public Builder workout(String id, String name) { workoutId = id; workoutName = name; return this; }

        public RideStats build() { return new RideStats(this); }
    }
}
