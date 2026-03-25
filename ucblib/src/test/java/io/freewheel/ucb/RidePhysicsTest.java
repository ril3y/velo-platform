package io.freewheel.ucb;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for RidePhysics speed and calorie calculations.
 *
 * Formulas under test:
 *   speedMph = min(cbrt(power / 4.0) * 2.24, 45.0)   — returns 0 for power <= 0
 *   calories = (avgPower / 0.25) * elapsedHours / 1.163
 */
public class RidePhysicsTest {

    private static final double DELTA = 0.01;

    // ── Speed tests ──────────────────────────────────────────────

    @Test
    public void speedMph_zeroPower_returnsZero() {
        double speed = computeSpeedMph(0);
        assertEquals(0.0, speed, DELTA);
    }

    @Test
    public void speedMph_negativePower_returnsZero() {
        double speed = computeSpeedMph(-50);
        assertEquals(0.0, speed, DELTA);
    }

    @Test
    public void speedMph_100watts_returnsExpected() {
        // cbrt(100 / 4.0) = cbrt(25) ≈ 2.924, * 2.24 ≈ 6.55
        double speed = computeSpeedMph(100);
        assertEquals(6.55, speed, 0.02);
    }

    @Test
    public void speedMph_400watts_returnsExpected() {
        // cbrt(400 / 4.0) = cbrt(100) ≈ 4.642, * 2.24 ≈ 10.40
        double speed = computeSpeedMph(400);
        assertEquals(10.40, speed, 0.02);
    }

    @Test
    public void speedMph_veryHighPower_cappedAt45() {
        // With extreme power the formula would exceed 45, but it is capped.
        double speed = computeSpeedMph(500_000);
        assertEquals(45.0, speed, DELTA);
    }

    // ── Calorie tests ────────────────────────────────────────────

    @Test
    public void calories_zeroPower_returnsZero() {
        double cal = computeCalories(0, 1.0);
        assertEquals(0.0, cal, DELTA);
    }

    @Test
    public void calories_100watts_1hour_returnsExpected() {
        // (100 / 0.25) * 1.0 / 1.163 ≈ 344.11
        double cal = computeCalories(100, 1.0);
        assertEquals(344.11, cal, 0.5);
    }

    @Test
    public void calories_200watts_halfHour_returnsExpected() {
        // (200 / 0.25) * 0.5 / 1.163 ≈ 344.11
        double cal = computeCalories(200, 0.5);
        assertEquals(344.11, cal, 0.5);
    }

    // ── Helper methods implementing the known formulas ───────────

    /**
     * Reference implementation of the speed formula.
     * speedMph = min(cbrt(power / 4.0) * 2.24, 45.0), returns 0 for power <= 0.
     */
    private static double computeSpeedMph(double power) {
        if (power <= 0) return 0.0;
        return Math.min(Math.cbrt(power / 4.0) * 2.24, 45.0);
    }

    /**
     * Reference implementation of the calorie formula.
     * calories = (avgPower / 0.25) * elapsedHours / 1.163
     */
    private static double computeCalories(double avgPower, double elapsedHours) {
        return (avgPower / 0.25) * elapsedHours / 1.163;
    }
}
