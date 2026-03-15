package io.freewheel.bridge;

interface IBikeListener {
    // Sensor data from UCB (called at ~1Hz during WORKOUT state)
    void onSensorData(int resistance, int rpm, int tilt, float power, long crankRevCount, int crankEventTime);

    // Firmware state changes
    void onFirmwareStateChanged(int state, String stateName);

    // Connection state changes
    void onConnectionChanged(boolean connected, String message);

    // Workout state changes (started/stopped, possibly by watchdog)
    void onWorkoutStateChanged(boolean active, String reason);

    // Heart rate from BLE HRM
    void onHeartRate(int bpm, String deviceName);

    // OTA firmware flash progress
    void onOtaProgress(int phase, int blockCurrent, int blockTotal);
    void onOtaComplete(boolean success, String error);

    // Resistance calibration progress
    void onCalibrationProgress(int step, String instruction);
    void onCalibrationComplete(boolean success);

    // Raw UCB frame monitoring
    void onRawFrame(in byte[] frame, boolean isOutgoing);
}
