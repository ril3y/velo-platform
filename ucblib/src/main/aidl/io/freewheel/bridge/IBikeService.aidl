package io.freewheel.bridge;

import io.freewheel.bridge.IBikeListener;

interface IBikeService {
    // Binding - single client at a time
    // Returns true if successfully claimed, false if another client is active
    boolean claimSession(String packageName);
    void releaseSession();

    // Workout control (only works for session owner)
    boolean startWorkout();
    boolean stopWorkout();
    boolean setResistance(int level);

    // Heartbeat - client must call every 5s during active workout
    void heartbeat();

    // State queries
    boolean isWorkoutActive();
    String getSessionOwner();
    int getFirmwareState();

    // Listener registration
    void registerListener(IBikeListener listener);
    void unregisterListener(IBikeListener listener);

    // Heart rate monitor
    int getHeartRate();
    String getConnectedHrmName();

    // OTA firmware flash
    void startOtaFlash(in ParcelFileDescriptor firmwareFd);
    void cancelOtaFlash();

    // Resistance calibration
    void startCalibration(int calibrationType);
    void cancelCalibration();
    void confirmCalibrationStep();

    // Raw UCB frame monitoring and control
    void setRawFrameMonitoring(boolean enabled);
    void sendRawCommand(in byte[] frame);
}
