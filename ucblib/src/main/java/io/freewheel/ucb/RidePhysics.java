package io.freewheel.ucb;

public class RidePhysics {
    // Power-to-speed: simplified cycling power model
    // speedMps = cbrt(powerWatts / DRAG_COEFFICIENT)
    private static final double DRAG_COEFFICIENT = 4.0;
    private static final double MPS_TO_MPH = 2.24;
    private static final double MAX_SPEED_MPH = 45.0;

    // Calorie calculation: metabolic cost model
    // kcal = (avgPower / METABOLIC_EFFICIENCY) * hours / KCAL_CONVERSION
    private static final double METABOLIC_EFFICIENCY = 0.25;
    private static final double KCAL_CONVERSION = 1.163;

    public static float speedMph(int powerWatts) {
        if (powerWatts <= 0) return 0f;
        double speedMps = Math.cbrt(powerWatts / DRAG_COEFFICIENT);
        return (float) Math.min(speedMps * MPS_TO_MPH, MAX_SPEED_MPH);
    }

    public static int calories(double avgPowerWatts, double elapsedHours) {
        return (int) ((avgPowerWatts / METABOLIC_EFFICIENCY) * elapsedHours / KCAL_CONVERSION);
    }
}
