package io.freewheel.fit;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client for the VeloLauncher Workout Session AIDL service.
 * External workout/game apps use this to start/stop workouts, set resistance,
 * and receive real-time sensor data from the bike.
 *
 * Usage:
 *   WorkoutSessionClient session = new WorkoutSessionClient(context);
 *   session.addListener(myListener);
 *   session.bind();
 *   session.requestStart();
 *   // ... receive sensor data via listener ...
 *   session.setResistance(50);
 *   session.requestStop();
 *   session.unbind();
 */
public class WorkoutSessionClient {
    private static final String TAG = "WorkoutSessionClient";
    private static final String SERVICE_ACTION = "io.freewheel.launcher.WORKOUT_SESSION";
    private static final String SERVICE_PACKAGE = "io.freewheel.launcher";
    private static final long RECONNECT_DELAY_MS = 3000;

    private final Context context;
    private final String clientPackage;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private volatile IWorkoutSession service;
    private volatile boolean bound = false;
    private volatile boolean shouldBind = false;

    public interface Listener {
        void onSensorData(int resistance, int rpm, int tilt, float power,
                          long crankRevCount, int crankEventTime);
        void onHeartRate(int bpm, String deviceName);
        void onWorkoutStateChanged(boolean active, String reason);
        void onConnectionChanged(boolean connected, String message);
        void onFirmwareStateChanged(int state, String stateName);
        void onServiceConnected();
        void onServiceDisconnected();
    }

    public static class ListenerAdapter implements Listener {
        @Override public void onSensorData(int resistance, int rpm, int tilt, float power,
                                           long crankRevCount, int crankEventTime) {}
        @Override public void onHeartRate(int bpm, String deviceName) {}
        @Override public void onWorkoutStateChanged(boolean active, String reason) {}
        @Override public void onConnectionChanged(boolean connected, String message) {}
        @Override public void onFirmwareStateChanged(int state, String stateName) {}
        @Override public void onServiceConnected() {}
        @Override public void onServiceDisconnected() {}
    }

    public WorkoutSessionClient(Context context) {
        this.context = context.getApplicationContext();
        this.clientPackage = context.getPackageName();
    }

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }
    public boolean isBound() { return bound && service != null; }

    // --- AIDL listener that receives callbacks from launcher ---

    private final IWorkoutListener.Stub workoutListener = new IWorkoutListener.Stub() {
        @Override
        public void onSensorData(int resistance, int rpm, int tilt, float power,
                                 long crankRevCount, int crankEventTime) {
            for (Listener l : listeners) {
                try { l.onSensorData(resistance, rpm, tilt, power, crankRevCount, crankEventTime); }
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
        public void onWorkoutStateChanged(boolean active, String reason) {
            for (Listener l : listeners) {
                try { l.onWorkoutStateChanged(active, reason); }
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
        public void onFirmwareStateChanged(int state, String stateName) {
            for (Listener l : listeners) {
                try { l.onFirmwareStateChanged(state, stateName); }
                catch (Exception e) { Log.w(TAG, "Listener error", e); }
            }
        }
    };

    // --- Service connection ---

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IWorkoutSession.Stub.asInterface(binder);
            bound = true;
            Log.d(TAG, "Connected to VeloLauncher WorkoutSession service");

            try {
                service.registerListener(workoutListener);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to register listener on connect", e);
            }

            for (Listener l : listeners) {
                try { l.onServiceConnected(); }
                catch (Exception e) { Log.w(TAG, "Listener error", e); }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "VeloLauncher WorkoutSession service disconnected");
            service = null;
            bound = false;

            for (Listener l : listeners) {
                try { l.onServiceDisconnected(); }
                catch (Exception e) { Log.w(TAG, "Listener error", e); }
            }

            // Auto-reconnect
            if (shouldBind) {
                handler.postDelayed(() -> {
                    if (shouldBind) {
                        Log.d(TAG, "Attempting reconnect...");
                        doBind();
                    }
                }, RECONNECT_DELAY_MS);
            }
        }
    };

    // --- Public API ---

    /** Bind to VeloLauncher WorkoutSession service. */
    public void bind() {
        shouldBind = true;
        doBind();
    }

    /** Unbind from the service. */
    public void unbind() {
        shouldBind = false;
        if (bound) {
            try {
                if (service != null) {
                    service.unregisterListener(workoutListener);
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

    /** Request to start a workout. Returns true if granted. */
    public boolean requestStart() {
        if (service == null) return false;
        try {
            return service.requestStart(clientPackage);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to request start", e);
            return false;
        }
    }

    /** Request to stop the workout. Returns true if granted. */
    public boolean requestStop() {
        if (service == null) return false;
        try {
            return service.requestStop(clientPackage);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to request stop", e);
            return false;
        }
    }

    /** Set resistance level (1-100). Only works if this app owns the session. */
    public boolean setResistance(int level) {
        if (service == null) return false;
        try {
            return service.setResistance(clientPackage, level);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set resistance", e);
            return false;
        }
    }

    /** Check if a workout is currently active. */
    public boolean isWorkoutActive() {
        if (service == null) return false;
        try {
            return service.isWorkoutActive();
        } catch (RemoteException e) {
            return false;
        }
    }

    /** Get the package name of the app currently owning the workout session. */
    public String getActiveAppPackage() {
        if (service == null) return null;
        try {
            return service.getActiveAppPackage();
        } catch (RemoteException e) {
            return null;
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

    /** Get current heart rate. Returns 0 if no HRM connected. */
    public int getHeartRate() {
        if (service == null) return 0;
        try {
            return service.getHeartRate();
        } catch (RemoteException e) {
            return 0;
        }
    }

    /** Get name of connected HRM device. Returns null if none. */
    public String getConnectedHrmName() {
        if (service == null) return null;
        try {
            return service.getConnectedHrmName();
        } catch (RemoteException e) {
            return null;
        }
    }

    // --- Internal ---

    private void doBind() {
        Intent intent = new Intent(SERVICE_ACTION);
        intent.setPackage(SERVICE_PACKAGE);
        try {
            boolean ok = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            if (!ok) {
                Log.w(TAG, "bindService returned false — VeloLauncher may not be installed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind to VeloLauncher WorkoutSession", e);
        }
    }
}
