package io.freewheel.ucb;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import io.freewheel.bridge.IBikeListener;
import io.freewheel.bridge.IBikeService;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client wrapper for the FreewheelBridge AIDL service.
 * Handles binding, auto-heartbeat, reconnection, and death detection.
 *
 * Usage:
 *   BikeServiceClient client = new BikeServiceClient(context);
 *   client.addListener(myListener);
 *   client.bind();
 *   // ...
 *   client.startWorkout();
 *   // heartbeat runs automatically every 5s while workout is active
 *   // ...
 *   client.stopWorkout();
 *   client.unbind();
 */
public class BikeServiceClient {
    private static final String TAG = "BikeServiceClient";
    private static final String SERVICE_ACTION = "io.freewheel.bridge.BIKE_SERVICE";
    private static final String SERVICE_PACKAGE = "io.freewheel.bridge";
    private static final long HEARTBEAT_INTERVAL_MS = 5000;
    private static final long RECONNECT_DELAY_MS = 3000;

    private final Context context;
    private final String clientPackage;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private volatile IBikeService service;
    private volatile boolean bound = false;
    private volatile boolean workoutActive = false;
    private volatile boolean shouldBind = false;

    public interface Listener {
        void onSensorData(SensorData data);
        void onFirmwareStateChanged(int state, String stateName);
        void onConnectionChanged(boolean connected, String message);
        void onWorkoutStateChanged(boolean active, String reason);
        void onHeartRate(int bpm, String deviceName);
        void onServiceConnected();
        void onServiceDisconnected();
        void onOtaProgress(int phase, int blockCurrent, int blockTotal);
        void onOtaComplete(boolean success, String error);
        void onCalibrationProgress(int step, String instruction);
        void onCalibrationComplete(boolean success);
        void onRawFrame(byte[] frame, boolean isOutgoing);
    }

    public static class ListenerAdapter implements Listener {
        @Override public void onSensorData(SensorData data) {}
        @Override public void onFirmwareStateChanged(int state, String stateName) {}
        @Override public void onConnectionChanged(boolean connected, String message) {}
        @Override public void onWorkoutStateChanged(boolean active, String reason) {}
        @Override public void onHeartRate(int bpm, String deviceName) {}
        @Override public void onServiceConnected() {}
        @Override public void onServiceDisconnected() {}
        @Override public void onOtaProgress(int phase, int blockCurrent, int blockTotal) {}
        @Override public void onOtaComplete(boolean success, String error) {}
        @Override public void onCalibrationProgress(int step, String instruction) {}
        @Override public void onCalibrationComplete(boolean success) {}
        @Override public void onRawFrame(byte[] frame, boolean isOutgoing) {}
    }

    public BikeServiceClient(Context context) {
        this.context = context.getApplicationContext();
        this.clientPackage = context.getPackageName();
    }

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }
    public boolean isBound() { return bound && service != null; }
    public boolean isWorkoutActive() { return workoutActive; }

    // --- AIDL listener that receives callbacks from FreewheelBridge ---

    private final IBikeListener.Stub bikeListener = new IBikeListener.Stub() {
        @Override
        public void onSensorData(int resistance, int rpm, int tilt, float power,
                                 long crankRevCount, int crankEventTime) {
            SensorData data = new SensorData();
            data.resistanceLevel = resistance;
            data.rpm = rpm;
            data.tilt = tilt;
            data.power = power;
            data.crankRevCount = crankRevCount;
            data.crankEventTime = crankEventTime;

            for (Listener l : listeners) {
                try { l.onSensorData(data); }
                catch (Exception e) { Log.w(TAG, "Listener error", e); }
            }
        }

        @Override
        public void onFirmwareStateChanged(int state, String stateName) {
            for (Listener l : listeners) {
                try { l.onFirmwareStateChanged(state, stateName); }
                catch (Exception e) { Log.w(TAG, "Listener error", e); }
            }
        }

        @Override
        public void onConnectionChanged(boolean connected, String message) {
            for (Listener l : listeners) {
                try { l.onConnectionChanged(connected, message); }
                catch (Exception e) { Log.w(TAG, "Listener error", e); }
            }
        }

        @Override
        public void onWorkoutStateChanged(boolean active, String reason) {
            workoutActive = active;
            if (!active) {
                stopHeartbeat();
            }
            for (Listener l : listeners) {
                try { l.onWorkoutStateChanged(active, reason); }
                catch (Exception e) { Log.w(TAG, "Listener error", e); }
            }
        }

        @Override
        public void onHeartRate(int bpm, String deviceName) {
            for (Listener l : listeners) {
                try { l.onHeartRate(bpm, deviceName); }
                catch (Exception e) { Log.w(TAG, "Listener error", e); }
            }
        }

        @Override
        public void onOtaProgress(int phase, int blockCurrent, int blockTotal) {
            for (Listener l : listeners) {
                try { l.onOtaProgress(phase, blockCurrent, blockTotal); }
                catch (Exception e) { Log.w(TAG, "Listener error", e); }
            }
        }

        @Override
        public void onOtaComplete(boolean success, String error) {
            for (Listener l : listeners) {
                try { l.onOtaComplete(success, error); }
                catch (Exception e) { Log.w(TAG, "Listener error", e); }
            }
        }

        @Override
        public void onCalibrationProgress(int step, String instruction) {
            for (Listener l : listeners) {
                try { l.onCalibrationProgress(step, instruction); }
                catch (Exception e) { Log.w(TAG, "Listener error", e); }
            }
        }

        @Override
        public void onCalibrationComplete(boolean success) {
            for (Listener l : listeners) {
                try { l.onCalibrationComplete(success); }
                catch (Exception e) { Log.w(TAG, "Listener error", e); }
            }
        }

        @Override
        public void onRawFrame(byte[] frame, boolean isOutgoing) {
            for (Listener l : listeners) {
                try { l.onRawFrame(frame, isOutgoing); }
                catch (Exception e) { Log.w(TAG, "Listener error", e); }
            }
        }
    };

    // --- Service connection ---

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IBikeService.Stub.asInterface(binder);
            bound = true;
            Log.d(TAG, "Connected to FreewheelBridge service");

            try {
                service.registerListener(bikeListener);
                boolean claimed = service.claimSession(clientPackage);
                Log.d(TAG, "claimSession(" + clientPackage + ") = " + claimed);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to register/claim on connect", e);
            }

            for (Listener l : listeners) {
                try { l.onServiceConnected(); }
                catch (Exception e) { Log.w(TAG, "Listener error", e); }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "FreewheelBridge service disconnected");
            service = null;
            bound = false;
            workoutActive = false;
            stopHeartbeat();

            for (Listener l : listeners) {
                try { l.onServiceDisconnected(); }
                catch (Exception e) { Log.w(TAG, "Listener error", e); }
            }

            // Auto-reconnect
            if (shouldBind) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (shouldBind) {
                            Log.d(TAG, "Attempting reconnect...");
                            doBind();
                        }
                    }
                }, RECONNECT_DELAY_MS);
            }
        }
    };

    // --- Public API ---

    /** Bind to FreewheelBridge service. */
    public void bind() {
        shouldBind = true;
        doBind();
    }

    /** Unbind from FreewheelBridge service. */
    public void unbind() {
        shouldBind = false;
        stopHeartbeat();

        if (bound) {
            try {
                if (service != null) {
                    service.unregisterListener(bikeListener);
                    service.releaseSession();
                }
            } catch (RemoteException e) {
                Log.w(TAG, "Error during unbind cleanup", e);
            }
            try {
                context.unbindService(connection);
            } catch (Exception e) {
                Log.w(TAG, "Error unbinding", e);
            }
            service = null;
            bound = false;
        }
    }

    /** Start a workout. Returns true if successful. */
    public boolean startWorkout() {
        if (service == null) {
            Log.w(TAG, "startWorkout: service is null!");
            return false;
        }
        try {
            boolean ok = service.startWorkout();
            if (ok) {
                workoutActive = true;
                startHeartbeat();
            }
            return ok;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to start workout", e);
            return false;
        }
    }

    /** Stop the current workout. Returns true if successful. */
    public boolean stopWorkout() {
        if (service == null) return false;
        try {
            boolean ok = service.stopWorkout();
            workoutActive = false;
            stopHeartbeat();
            return ok;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to stop workout", e);
            return false;
        }
    }

    /** Set resistance level (1-100). Only works during active workout. */
    public boolean setResistance(int level) {
        if (service == null) return false;
        try {
            return service.setResistance(level);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set resistance", e);
            return false;
        }
    }

    /** Get current firmware state. Returns -1 if not connected. */
    public int getFirmwareState() {
        if (service == null) return -1;
        try {
            return service.getFirmwareState();
        } catch (RemoteException e) {
            return -1;
        }
    }

    /** Get UCB firmware version string (e.g. "R_5.87.5"). Returns null if not yet known. */
    public String getFirmwareVersion() {
        if (service == null) return null;
        try {
            return service.getFirmwareVersion();
        } catch (RemoteException e) {
            return null;
        }
    }

    /** Get UCB hardware variant ID. Returns -1 if not connected. */
    public int getHardwareId() {
        if (service == null) return -1;
        try {
            return service.getHardwareId();
        } catch (RemoteException e) {
            return -1;
        }
    }

    /** Get current heart rate from BLE HRM. Returns 0 if no HRM connected. */
    public int getHeartRate() {
        if (service == null) return 0;
        try {
            return service.getHeartRate();
        } catch (RemoteException e) {
            return 0;
        }
    }

    /** Get name of connected BLE HRM device. Returns null if none. */
    public String getConnectedHrmName() {
        if (service == null) return null;
        try {
            return service.getConnectedHrmName();
        } catch (RemoteException e) {
            return null;
        }
    }

    /** Start resistance calibration. */
    public void startCalibration(int calibrationType) {
        if (service == null) return;
        try {
            service.startCalibration(calibrationType);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to start calibration", e);
        }
    }

    /** Cancel resistance calibration. */
    public void cancelCalibration() {
        if (service == null) return;
        try {
            service.cancelCalibration();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to cancel calibration", e);
        }
    }

    /** Confirm current calibration step. */
    public void confirmCalibrationStep() {
        if (service == null) return;
        try {
            service.confirmCalibrationStep();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to confirm calibration step", e);
        }
    }

    /** Enable/disable raw UCB frame monitoring. */
    public void setRawFrameMonitoring(boolean enabled) {
        if (service == null) return;
        try {
            service.setRawFrameMonitoring(enabled);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set raw frame monitoring", e);
        }
    }

    /** Send a raw UCB command frame. */
    public void sendRawCommand(byte[] frame) {
        if (service == null) return;
        try {
            service.sendRawCommand(frame);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to send raw command", e);
        }
    }

    /** Get the raw AIDL service interface for advanced operations (OTA, etc). */
    public IBikeService getService() {
        return service;
    }

    // --- Heartbeat ---

    private Runnable heartbeatRunnable;

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (workoutActive && service != null) {
                    try {
                        service.heartbeat();
                    } catch (RemoteException e) {
                        Log.w(TAG, "Heartbeat failed", e);
                    }
                    handler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
                }
            }
        };
        handler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS);
    }

    private void stopHeartbeat() {
        if (heartbeatRunnable != null) {
            handler.removeCallbacks(heartbeatRunnable);
            heartbeatRunnable = null;
        }
    }

    // --- Internal ---

    private void doBind() {
        Intent intent = new Intent(SERVICE_ACTION);
        intent.setPackage(SERVICE_PACKAGE);
        try {
            boolean ok = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            if (!ok) {
                Log.w(TAG, "bindService returned false — FreewheelBridge may not be installed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind to FreewheelBridge", e);
        }
    }
}
