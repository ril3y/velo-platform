package io.freewheel.fit;

import io.freewheel.fit.IWorkoutListener;

interface IWorkoutSession {
    boolean requestStart(String callerPackage);
    boolean requestStop(String callerPackage);
    boolean setResistance(String callerPackage, int level);
    boolean isWorkoutActive();
    String getActiveAppPackage();
    int getFirmwareState();
    int getHeartRate();
    String getConnectedHrmName();
    void registerListener(IWorkoutListener listener);
    void unregisterListener(IWorkoutListener listener);
}
