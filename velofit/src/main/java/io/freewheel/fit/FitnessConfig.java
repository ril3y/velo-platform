package io.freewheel.fit;

/**
 * Fitness configuration from the VeloLauncher user profile.
 * Immutable snapshot queried via VeloFitnessClient.
 */
public class FitnessConfig {
    public final int ftp;           // functional threshold power (watts)
    public final int maxHeartRate;
    public final int weightLbs;
    public final int age;

    public FitnessConfig(int ftp, int maxHeartRate, int weightLbs, int age) {
        this.ftp = ftp;
        this.maxHeartRate = maxHeartRate;
        this.weightLbs = weightLbs;
        this.age = age;
    }

    /** Default config for when VeloLauncher is unavailable */
    public static FitnessConfig defaults() {
        return new FitnessConfig(120, 180, 170, 35);
    }
}
