package io.freewheel.fit;

/**
 * Target power range for a given resistance level.
 * Queried from the launcher or computed locally from FitnessConfig.
 */
public class PowerTarget {
    public final int resistance;
    public final int ftp;
    public final int targetLow;
    public final int targetHigh;
    public final int centerPower;

    public PowerTarget(int resistance, int ftp, int targetLow, int targetHigh, int centerPower) {
        this.resistance = resistance;
        this.ftp = ftp;
        this.targetLow = targetLow;
        this.targetHigh = targetHigh;
        this.centerPower = centerPower;
    }

    /** The display scale max — 2x center so target zone sits in the middle of a bar */
    public float maxDisplay() {
        return Math.max(centerPower * 2f, 30f);
    }

    /**
     * Compute compliance ratio (0..1) for a given actual power.
     * 0 = bottom of bar, 1 = top of bar. Clamped.
     */
    public float complianceRatio(int actualPower) {
        float max = maxDisplay();
        return max > 0 ? Math.max(0f, Math.min(1f, actualPower / max)) : 0f;
    }

    /** Zone low fraction (0..1) for bar rendering */
    public float zoneLowFraction() {
        float max = maxDisplay();
        return max > 0 ? Math.max(0f, Math.min(1f, targetLow / max)) : 0.35f;
    }

    /** Zone high fraction (0..1) for bar rendering */
    public float zoneHighFraction() {
        float max = maxDisplay();
        return max > 0 ? Math.max(0f, Math.min(1f, targetHigh / max)) : 0.65f;
    }

    /** Determine effort zone for the given actual power */
    public EffortZone zone(int actualPower) {
        if (actualPower == 0) return EffortZone.IDLE;
        if (actualPower < targetLow) return EffortZone.UNDER;
        if (actualPower > targetHigh) return EffortZone.OVER;
        return EffortZone.ON_TARGET;
    }

    public enum EffortZone {
        IDLE, UNDER, ON_TARGET, OVER
    }
}
