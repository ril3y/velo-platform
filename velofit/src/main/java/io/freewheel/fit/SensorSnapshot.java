package io.freewheel.fit;

/**
 * Point-in-time snapshot of sensor data from the active workout session,
 * queried from VeloLauncher's ContentProvider.
 */
public class SensorSnapshot {
    public final int resistance;
    public final int rpm;
    public final int tilt;
    public final float power;
    public final long crankRevCount;
    public final int crankEventTime;
    public final int heartRate;
    public final String hrmDeviceName;

    public SensorSnapshot(int resistance, int rpm, int tilt, float power,
                          long crankRevCount, int crankEventTime,
                          int heartRate, String hrmDeviceName) {
        this.resistance = resistance;
        this.rpm = rpm;
        this.tilt = tilt;
        this.power = power;
        this.crankRevCount = crankRevCount;
        this.crankEventTime = crankEventTime;
        this.heartRate = heartRate;
        this.hrmDeviceName = hrmDeviceName;
    }
}
