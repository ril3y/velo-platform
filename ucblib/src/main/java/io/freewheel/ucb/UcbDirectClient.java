package io.freewheel.ucb;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Direct UCB client -- connects to the serial bridge TCP:9999 and handles
 * all protocol signaling independently. No JRNY needed.
 *
 * Performs the full UCB init sequence:
 *   1. SYSTEM_DATA requests (identify hardware)
 *   2. STREAMING_CONTROL (enable sensor stream)
 *   3. Heartbeats every 3 seconds
 *   4. Receives STREAM_NTFCN with raw sensor data (resistance, rpm, tilt, power)
 *
 * Also reads the lean sensor from /dev/input/event2.
 *
 * Lifecycle:
 *   connectAsync() -- starts background thread, connects, begins streaming
 *   pause()        -- disconnects and frees TCP:9999 for other apps
 *   resume()       -- reconnects (call after pause)
 *   disconnect()   -- full shutdown
 *
 * The serial bridge (TCP:9999) only sends data to one client at a time.
 * If constructed with a Context, lifecycle is managed automatically via
 * ActivityLifecycleCallbacks (foreground app wins).
 *
 * Usage:
 *   UcbDirectClient client = new UcbDirectClient(context);
 *   client.addListener(myListener);
 *   client.connectAsync();
 *   // lifecycle is automatic -- or call pause()/resume() manually
 *   client.disconnect();
 */
public class UcbDirectClient {
    private static final String TAG = "UcbDirect";
    private static final int DEFAULT_PORT = 9999;
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int HEARTBEAT_INTERVAL_MS = 3000;
    private static final int INIT_COUNT = 5;
    private static final int RECONNECT_BASE_MS = 3000;
    private static final int RECONNECT_MAX_MS = 30000;

    private String host = DEFAULT_HOST;
    private int port = DEFAULT_PORT;
    private volatile boolean running;
    private volatile boolean paused;
    private volatile boolean connected;
    private Thread clientThread;
    private Thread heartbeatThread;
    private final LeanSensor leanSensor = new LeanSensor();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    // Guarded by `lock` -- protects socket/out/seq from concurrent access
    private final Object lock = new Object();
    private volatile Socket socket;
    private volatile OutputStream out;
    private int seq;

    // Last received data (volatile for cross-thread visibility)
    private volatile SensorData lastSensor;
    private volatile int firmwareState = -1;
    private volatile float lastLean;

    public interface Listener {
        /** Called at ~1 Hz with raw sensor data from UCB hardware. */
        void onSensorData(SensorData data);

        /** Called on each accelerometer update with lean angle. */
        void onLeanUpdate(float leanDegrees, int rawX, int rawY, int rawZ);

        /** Called on heartbeat response with firmware state. */
        void onHeartbeat(int firmwareState, String stateName);

        /** Called on any UCB frame (for apps that want raw protocol access). */
        void onFrame(UcbFrame frame);

        /** Called on connection state changes. */
        void onConnectionChanged(boolean connected, String message);
    }

    public static class ListenerAdapter implements Listener {
        @Override public void onSensorData(SensorData data) {}
        @Override public void onLeanUpdate(float leanDegrees, int rawX, int rawY, int rawZ) {}
        @Override public void onHeartbeat(int firmwareState, String stateName) {}
        @Override public void onFrame(UcbFrame frame) {}
        @Override public void onConnectionChanged(boolean connected, String message) {}
    }

    private Application application;
    private Application.ActivityLifecycleCallbacks lifecycleCallbacks;

    // Shared lean listener to avoid duplicate anonymous classes
    private final LeanSensor.LeanListener leanListener = new LeanSensor.LeanListener() {
        @Override
        public void onLean(int x, int y, int z, float leanDegrees) {
            lastLean = leanDegrees;
            for (Listener l : listeners) {
                try { l.onLeanUpdate(leanDegrees, x, y, z); }
                catch (Exception e) { Log.w(TAG, "Listener error in onLeanUpdate", e); }
            }
        }
    };

    public UcbDirectClient() {}

    public UcbDirectClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Create a lifecycle-aware client. Automatically pauses when the app goes
     * to background (releasing TCP:9999) and resumes when foregrounded.
     * Uses ActivityManager to verify process importance -- never falsely pauses
     * during activity transitions.
     */
    public UcbDirectClient(Context context) {
        this.application = (Application) context.getApplicationContext();
    }

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }
    public SensorData getLastSensor() { return lastSensor; }
    public float getLastLean() { return lastLean; }
    public int getFirmwareState() { return firmwareState; }
    public boolean isConnected() { return connected; }
    public boolean isPaused() { return paused; }
    public LeanSensor getLeanSensor() { return leanSensor; }

    /**
     * Connect and run in background thread. Returns immediately.
     * If constructed with a Context, registers lifecycle callbacks so the
     * foreground app always owns TCP:9999.
     */
    public synchronized void connectAsync() {
        if (running) return;
        running = true;
        paused = false;
        registerLifecycle();
        leanSensor.start(leanListener);

        clientThread = new Thread(new Runnable() {
            public void run() {
                runClient();
            }
        });
        clientThread.setDaemon(true);
        clientThread.start();
    }

    /**
     * Pause -- disconnect from TCP:9999 but keep the client alive.
     * Call when app goes to background to free the serial bridge for other apps.
     */
    public synchronized void pause() {
        if (!running || paused) return;
        paused = true;
        leanSensor.stop();
        closeSocket();
        stopHeartbeat();
        Log.d(TAG, "Paused -- serial bridge released");
        notifyConnection(false, "Paused");
    }

    /**
     * Resume -- reconnect to TCP:9999 after a pause.
     * Call when app returns to foreground.
     */
    public synchronized void resume() {
        if (!running || !paused) return;
        paused = false;
        leanSensor.start(leanListener);

        // Wake up the client thread -- it's sleeping in the paused loop
        Thread t = clientThread;
        if (t != null) {
            t.interrupt();
        }
        Log.d(TAG, "Resumed -- reconnecting to serial bridge");
    }

    /** Disconnect and clean up completely. */
    public synchronized void disconnect() {
        running = false;
        paused = false;
        unregisterLifecycle();
        leanSensor.stop();
        closeSocket();
        stopHeartbeat();
        Thread t = clientThread;
        clientThread = null;
        if (t != null) {
            t.interrupt();
        }
    }

    /** Set resistance level (1-100). Sends SET_RESISTANCE command to UCB. */
    public boolean setResistance(int level) {
        try {
            byte[] data = new byte[4];
            data[0] = (byte) (level & 0xFF);
            data[1] = (byte) ((level >> 8) & 0xFF);
            data[2] = (byte) ((level >> 16) & 0xFF);
            data[3] = (byte) ((level >> 24) & 0xFF);
            sendCommand(UcbMessageIds.SET_RESISTANCE, data);
            return true;
        } catch (Exception e) {
            // Not connected or write failed -- normal during init/teardown
            return false;
        }
    }

    /** Transition UCB firmware to WORKOUT state. Called automatically during init. */
    public boolean startWorkout() {
        try {
            sendCommand(UcbMessageIds.SET_WORKOUT_STATE, new byte[]{0x01}); // NLS_WORKOUT_RUNNING
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Transition UCB firmware to STOPPED state. Call before disconnect for clean shutdown. */
    public boolean stopWorkout() {
        try {
            sendCommand(UcbMessageIds.SET_WORKOUT_STATE, new byte[]{0x00}); // NLS_WORKOUT_STOPPED
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Set incline/tilt. Sends SET_INCLINE command to UCB. */
    public boolean setIncline(int level) {
        try {
            byte[] data = new byte[4];
            data[0] = (byte) (level & 0xFF);
            data[1] = (byte) ((level >> 8) & 0xFF);
            data[2] = (byte) ((level >> 16) & 0xFF);
            data[3] = (byte) ((level >> 24) & 0xFF);
            sendCommand(UcbMessageIds.SET_INCLINE, data);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // --- Lifecycle management ---

    /**
     * Check if this process is actually in the foreground using ActivityManager.
     * This is the ground truth -- correct even during activity transitions.
     */
    private boolean isProcessForeground() {
        if (application == null) return true;
        ActivityManager am = (ActivityManager) application.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs == null) return true;
        int myPid = android.os.Process.myPid();
        for (ActivityManager.RunningAppProcessInfo info : procs) {
            if (info.pid == myPid) {
                return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
            }
        }
        return true;
    }

    private void registerLifecycle() {
        if (application == null) return;
        lifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity a) {
                if (paused && running) {
                    Log.d(TAG, "App foregrounded -- resuming");
                    resume();
                }
            }
            @Override public void onActivityStopped(Activity a) {
                if (running && !paused && !isProcessForeground()) {
                    Log.d(TAG, "App backgrounded -- pausing");
                    pause();
                }
            }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityResumed(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        };
        application.registerActivityLifecycleCallbacks(lifecycleCallbacks);
    }

    private void unregisterLifecycle() {
        if (application != null && lifecycleCallbacks != null) {
            application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks);
            lifecycleCallbacks = null;
        }
    }

    /** Send a raw command to the UCB. */
    public void sendCommand(int msgId, byte[] data) throws Exception {
        synchronized (lock) {
            OutputStream o = out;
            if (o == null) throw new IllegalStateException("Not connected");
            seq = (seq + 1) & 0xFF;
            byte[] frame = UcbFrame.buildRequest(msgId, seq, data);
            o.write(frame);
            o.flush();
        }
    }

    // --- Internal ---

    private void runClient() {
        int reconnectDelay = RECONNECT_BASE_MS;
        while (running) {
            // If paused, sleep until resumed
            while (paused && running) {
                try { Thread.sleep(60000); }
                catch (InterruptedException e) { /* resume() interrupts us */ }
            }
            if (!running) break;

            try {
                notifyConnection(false, "Connecting to " + host + ":" + port + "...");
                Socket s = new Socket();
                s.setTcpNoDelay(true);
                s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                s.setSoTimeout(1000);

                synchronized (lock) {
                    socket = s;
                    out = s.getOutputStream();
                    seq = 0;
                }

                final InputStream in = s.getInputStream();
                connected = true;
                notifyConnection(true, "Connected");
                Log.d(TAG, "Connected to " + host + ":" + port);
                reconnectDelay = RECONNECT_BASE_MS;

                // Phase 1: Init -- send SYSTEM_DATA requests
                for (int i = 0; i < INIT_COUNT; i++) {
                    sendCommand(UcbMessageIds.SYSTEM_DATA, new byte[0]);
                    Thread.sleep(200);
                }

                // Phase 2: Enable streaming
                sendCommand(UcbMessageIds.STREAMING_CONTROL, new byte[]{0x01, 0x00});
                Log.d(TAG, "Streaming enabled");

                // Phase 2b: Send WORKOUT_BLE_DATA to trigger SELECTION→WORKOUT transition.
                // VeloCore firmware ignores SET_WORKOUT_STATE (0x3D) -- that's only for legacy
                // consoles. The firmware transitions to WORKOUT when it receives WORKOUT_BLE_DATA
                // from the SBC, indicating a workout has started.
                sendCommand(UcbMessageIds.WORKOUT_BLE_DATA, buildWorkoutBleData());
                Log.d(TAG, "Sent WORKOUT_BLE_DATA to trigger WORKOUT state");

                // Phase 3: Start heartbeat thread
                startHeartbeat();

                // Phase 4: Read frames
                byte[] buf = new byte[4096];
                byte[] accumBuf = new byte[8192];
                int accumLen = 0;

                while (running && !paused && !s.isClosed()) {
                    int n;
                    try {
                        n = in.read(buf);
                    } catch (java.net.SocketTimeoutException e) {
                        continue;
                    }
                    if (n <= 0) break;

                    // Append to accumulation buffer
                    if (accumLen + n > accumBuf.length) {
                        Log.w(TAG, "Buffer full (" + accumLen + "+" + n + "), resetting");
                        accumLen = 0;
                    }
                    System.arraycopy(buf, 0, accumBuf, accumLen, n);
                    accumLen += n;

                    // Extract complete frames
                    int consumed = extractAndDispatch(accumBuf, 0, accumLen);
                    if (consumed > 0 && consumed < accumLen) {
                        System.arraycopy(accumBuf, consumed, accumBuf, 0, accumLen - consumed);
                        accumLen -= consumed;
                    } else if (consumed >= accumLen) {
                        accumLen = 0;
                    }
                }

            } catch (Exception e) {
                Log.w(TAG, "Connection error: " + e.getMessage());
            } finally {
                connected = false;
                stopHeartbeat();
                closeSocket();
                notifyConnection(false, "Disconnected");
            }

            // Reconnect with exponential backoff (unless paused or stopped)
            if (running && !paused) {
                try { Thread.sleep(reconnectDelay); } catch (InterruptedException e) { /* resume or stop */ }
                reconnectDelay = Math.min(reconnectDelay * 2, RECONNECT_MAX_MS);
            }
        }
    }

    private int extractAndDispatch(byte[] buf, int offset, int length) {
        int totalConsumed = 0;
        int end = offset + length;
        int pos = offset;

        while (pos < end) {
            int stx = -1;
            for (int i = pos; i < end; i++) {
                if (buf[i] == UcbFrame.STX) { stx = i; break; }
            }
            if (stx < 0) { totalConsumed += (end - pos); break; }
            totalConsumed += (stx - pos);

            int etx = -1;
            for (int i = stx + 1; i < end; i++) {
                if (buf[i] == UcbFrame.ETX) { etx = i; break; }
            }
            if (etx < 0) break; // incomplete frame, wait for more data

            int frameLen = etx - stx + 1;
            UcbFrame frame = UcbFrame.decode(buf, stx, frameLen);
            if (frame != null) {
                dispatchFrame(frame);
            }
            totalConsumed += frameLen;
            pos = etx + 1;
        }
        return totalConsumed;
    }

    private void dispatchFrame(UcbFrame frame) {
        for (Listener l : listeners) {
            try { l.onFrame(frame); }
            catch (Exception e) { Log.w(TAG, "Listener error in onFrame", e); }
        }

        if (frame.msgType == UcbFrame.MSG_TYPE_ACK) return;

        switch (frame.msgId) {
            case UcbMessageIds.STREAM_NTFCN:
                SensorData sensor = SensorData.parse(frame.data);
                if (sensor != null) {
                    lastSensor = sensor;
                    for (Listener l : listeners) {
                        try { l.onSensorData(sensor); }
                        catch (Exception e) { Log.w(TAG, "Listener error in onSensorData", e); }
                    }
                }
                break;

            case UcbMessageIds.SYSTEM_HEART_BEAT:
                if (frame.data.length > 0) {
                    firmwareState = frame.data[0] & 0xFF;
                    String name = getFirmwareStateName(firmwareState);
                    for (Listener l : listeners) {
                        try { l.onHeartbeat(firmwareState, name); }
                        catch (Exception e) { Log.w(TAG, "Listener error in onHeartbeat", e); }
                    }
                }
                break;

            case UcbMessageIds.SYSTEM_DATA:
                if (frame.data.length > 0) {
                    Log.d(TAG, "SYSTEM_DATA response: " + frame.data.length + " bytes");
                }
                break;
        }
    }

    /**
     * Build a minimal WORKOUT_BLE_DATA (0x31) payload.
     * 51 bytes matching C9C10BleWorkoutdata layout:
     * 11 floats (LE) + 1 uint8 + 1 uint32 (BE) + 1 uint16 (BE)
     */
    private byte[] buildWorkoutBleData() {
        byte[] data = new byte[51];
        // All floats default to 0.0f (all zeros in IEEE 754)
        // resistanceLevel at offset 44, default 1
        data[44] = 0x01;
        // cumulativeCrankRevolutions (uint32 BE) at offset 45 = 0
        // lastCrankEventTime (uint16 BE) at offset 49 = 0
        return data;
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatThread = new Thread(new Runnable() {
            public void run() {
                int tick = 0;
                try {
                    while (running && connected && !paused) {
                        Thread.sleep(HEARTBEAT_INTERVAL_MS);
                        if (connected) {
                            sendCommand(UcbMessageIds.SYSTEM_HEART_BEAT, new byte[0]);
                            // Send WORKOUT_BLE_DATA every other heartbeat (~6s) to
                            // maintain WORKOUT state. The UCB firmware may revert to
                            // SELECTION if it stops receiving workout data.
                            tick++;
                            if (tick % 2 == 0) {
                                sendCommand(UcbMessageIds.WORKOUT_BLE_DATA, buildWorkoutBleData());
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    // Normal shutdown
                } catch (Exception e) {
                    Log.w(TAG, "Heartbeat error: " + e.getMessage());
                }
            }
        });
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    private void stopHeartbeat() {
        Thread t = heartbeatThread;
        heartbeatThread = null;
        if (t != null) {
            t.interrupt();
        }
    }

    private void closeSocket() {
        synchronized (lock) {
            out = null;
            Socket s = socket;
            socket = null;
            if (s != null) {
                try { s.close(); } catch (Exception e) {}
            }
        }
    }

    private void notifyConnection(boolean isConnected, String message) {
        for (Listener l : listeners) {
            try { l.onConnectionChanged(isConnected, message); }
            catch (Exception e) { Log.w(TAG, "Listener error in onConnectionChanged", e); }
        }
    }

    static String getFirmwareStateName(int state) {
        switch (state) {
            case 0:  return "BOOT_FAILSAFE";
            case 1:  return "POWER_ON_0";
            case 2:  return "POWER_ON_1";
            case 3:  return "POWER_ON_2";
            case 4:  return "UPDATES";
            case 5:  return "TRANSITION";
            case 6:  return "MFG";
            case 7:  return "OTA";
            case 8:  return "SELECTION";
            case 9:  return "WORKOUT";
            case 10: return "SLEEP";
            case 11: return "RESET";
            case 12: return "SBC_DISCONNECTED";
            default: return "UNKNOWN_" + state;
        }
    }
}
