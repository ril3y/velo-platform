package io.freewheel.fit;

interface IWorkoutListener {
    void onSensorData(int resistance, int rpm, int tilt, float power,
                      long crankRevCount, int crankEventTime);
    void onHeartRate(int bpm, String deviceName);
    void onWorkoutStateChanged(boolean active, String reason);
    void onConnectionChanged(boolean connected, String message);
    void onFirmwareStateChanged(int state, String stateName);
}
