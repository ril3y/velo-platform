package io.freewheel.bridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.io.FileInputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.CRC32;

import io.freewheel.ucb.MfgTestCommand;
import io.freewheel.ucb.OtaFirmwareFile;
import io.freewheel.ucb.OtaFlashSession;
import io.freewheel.ucb.UcbMessageIds;

/**
 * Direct serial-to-TCP bridge service with AIDL bike sensor interface.
 * Opens /dev/ttyS4 at 230400 baud via JNI and serves it on TCP:9999.
 * Also exposes IBikeService AIDL for direct programmatic access to sensor data.
 */
public class BridgeService extends Service {
    private static final String TAG = "SerialBridge";
    private static final String CHANNEL_ID = "serial_bridge";
    private static final String SERIAL_DEVICE = "/dev/ttyS4";
    private static final int BAUD_RATE = 230400;
    private static final int TCP_PORT = 9999;
    private static final int BUFFER_SIZE = 1024;

    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private Object serialPortObj; // either ucbsystemdata or serial SerialPort
    private InputStream serialInputStream;
    private OutputStream serialOutputStream;
    private volatile Socket currentClient;
    private Thread mainThread;

    // UCB protocol constants
    private static final byte STX = 0x02;
    private static final byte ETX = 0x03;

    // UCB message types
    private static final int MSG_TYPE_ACK = 0x00;
    private static final int MSG_TYPE_REQUEST = 0x01;
    private static final int MSG_TYPE_NOTIFICATION = 0x02;

    // Stream notification message IDs
    private static final int MSG_ID_STREAM_NTFCN = 0x08;
    private static final int MSG_ID_SYSTEM_DATA = 0x18;
    private static final int MSG_ID_SYSTEM_HEART_BEAT = 0x1F;
    private static final int STREAM_NTFCN_DATA_LEN = 23;

    // BLE Heart Rate Monitor
    private HrmManager hrmManager;

    // AIDL binder and listener management
    private final RemoteCallbackList<IBikeListener> bikeListeners = new RemoteCallbackList<>();
    private volatile boolean workoutActive = false;
    private volatile String sessionOwnerPkg = null;
    private volatile int sessionOwnerUid = -1;
    private volatile long lastHeartbeatTime = 0;
    private Timer watchdogTimer;
    private volatile int currentFirmwareState = -1;
    private volatile String ucbFirmwareVersion = null; // e.g. "R_5.87.5"
    private volatile int ucbHardwareId = -1;
    private volatile String ucbSerialNumber = null;
    private volatile boolean serialConnected = false;
    private volatile boolean workoutPending = false; // waiting for SELECTION state to start workout

    // Frame accumulator for parsing incoming UCB frames
    private final byte[] frameAccumBuf = new byte[512];
    private int frameAccumLen = 0;
    private boolean inFrame = false;
    private long frameCount = 0;
    private long sensorBroadcastCount = 0;

    // Queue for outgoing serial writes (avoids writing from reader thread)
    private final java.util.concurrent.ConcurrentLinkedQueue<byte[]> serialWriteQueue =
        new java.util.concurrent.ConcurrentLinkedQueue<>();

    // Write arbitration — exclusive access for OTA/calibration
    private volatile String exclusiveWriteOwner = null;
    private final Object exclusiveWriteLock = new Object();

    // OTA flash session
    private volatile OtaFlashSession otaSession = null;

    // Calibration state
    private volatile boolean calibrationActive = false;
    private volatile int calibrationPhase = 0;

    // Raw frame monitoring
    private volatile boolean rawFrameMonitorEnabled = false;

    private final IBikeService.Stub binder = new IBikeService.Stub() {
        @Override
        public boolean claimSession(String packageName) {
            synchronized (BridgeService.this) {
                if (sessionOwnerPkg == null || sessionOwnerPkg.equals(packageName)) {
                    sessionOwnerPkg = packageName;
                    sessionOwnerUid = Binder.getCallingUid();
                    lastHeartbeatTime = System.currentTimeMillis();
                    Log.d(TAG, "Session claimed by: " + packageName);
                    return true;
                }
                Log.w(TAG, "Session claim denied for " + packageName + " (owner: " + sessionOwnerPkg + ")");
                return false;
            }
        }

        @Override
        public void releaseSession() {
            synchronized (BridgeService.this) {
                String pkg = sessionOwnerPkg;
                if (workoutActive) {
                    Log.d(TAG, "Stopping workout during session release by " + pkg);
                    doStopWorkout("session_released");
                }
                sessionOwnerPkg = null;
                sessionOwnerUid = -1;
                Log.d(TAG, "Session released by: " + pkg);
            }
        }

        @Override
        public boolean startWorkout() {
            synchronized (BridgeService.this) {
                if (!isCallerSessionOwner()) {
                    Log.w(TAG, "startWorkout denied - not session owner");
                    return false;
                }
                if (workoutActive) {
                    Log.d(TAG, "startWorkout - already active");
                    return true;
                }
                return doStartWorkout();
            }
        }

        @Override
        public boolean stopWorkout() {
            synchronized (BridgeService.this) {
                if (!isCallerSessionOwner()) {
                    Log.w(TAG, "stopWorkout denied - not session owner");
                    return false;
                }
                if (!workoutActive) {
                    Log.d(TAG, "stopWorkout - not active");
                    return true;
                }
                return doStopWorkout("client_requested");
            }
        }

        @Override
        public boolean setResistance(int level) {
            synchronized (BridgeService.this) {
                if (!isCallerSessionOwner()) {
                    Log.w(TAG, "setResistance denied - not session owner");
                    return false;
                }
                return doSetResistance(level);
            }
        }

        @Override
        public void heartbeat() {
            if (isCallerSessionOwner()) {
                lastHeartbeatTime = System.currentTimeMillis();
            }
        }

        @Override
        public boolean isWorkoutActive() {
            return workoutActive;
        }

        @Override
        public String getSessionOwner() {
            return sessionOwnerPkg;
        }

        @Override
        public int getFirmwareState() {
            return currentFirmwareState;
        }

        @Override
        public String getFirmwareVersion() {
            return ucbFirmwareVersion;
        }

        @Override
        public int getHardwareId() {
            return ucbHardwareId;
        }

        @Override
        public void registerListener(IBikeListener listener) {
            if (listener != null) {
                bikeListeners.register(listener);
                Log.d(TAG, "Listener registered, count: " + bikeListeners.getRegisteredCallbackCount());
            }
        }

        @Override
        public void unregisterListener(IBikeListener listener) {
            if (listener != null) {
                bikeListeners.unregister(listener);
                Log.d(TAG, "Listener unregistered, count: " + bikeListeners.getRegisteredCallbackCount());
            }
        }

        @Override
        public int getHeartRate() {
            return hrmManager != null ? hrmManager.getCurrentBpm() : 0;
        }

        @Override
        public String getConnectedHrmName() {
            return hrmManager != null ? hrmManager.getConnectedDeviceName() : null;
        }

        @Override
        public void startOtaFlash(android.os.ParcelFileDescriptor firmwareFd) {
            BridgeService.this.doStartOtaFlash(firmwareFd);
        }

        @Override
        public void cancelOtaFlash() {
            BridgeService.this.doCancelOtaFlash();
        }

        @Override
        public void startCalibration(int calibrationType) {
            BridgeService.this.doStartCalibration(calibrationType);
        }

        @Override
        public void cancelCalibration() {
            BridgeService.this.doCancelCalibration();
        }

        @Override
        public void confirmCalibrationStep() {
            BridgeService.this.doConfirmCalibrationStep();
        }

        @Override
        public void setRawFrameMonitoring(boolean enabled) {
            rawFrameMonitorEnabled = enabled;
            Log.d(TAG, "Raw frame monitoring: " + (enabled ? "ON" : "OFF"));
        }

        @Override
        public void sendRawCommand(byte[] frame) {
            if (frame != null && frame.length > 0) {
                serialWriteQueue.offer(frame);
                Log.d(TAG, "Queued raw command: " + frame.length + " bytes");
            }
        }
    };

    private boolean isCallerSessionOwner() {
        return sessionOwnerPkg != null && Binder.getCallingUid() == sessionOwnerUid;
    }

    @Override
    public IBinder onBind(Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        Log.d(TAG, "onBind action=" + action);
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "BridgeService starting - direct serial mode with AIDL v3.0");
        createNotificationChannel();
        startForeground(2, buildNotification("Starting..."));
        startWatchdog();
        startBridge();
        startHrm();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        if (watchdogTimer != null) {
            watchdogTimer.cancel();
            watchdogTimer = null;
        }
        if (hrmManager != null) {
            hrmManager.stop();
            hrmManager = null;
        }
        bikeListeners.kill();
        cleanup();
        Log.d(TAG, "BridgeService destroyed");
    }

    // --- HRM ---

    private void startHrm() {
        hrmManager = new HrmManager(this);
        hrmManager.setListener(new HrmManager.Listener() {
            @Override
            public void onHeartRate(int bpm, String deviceName) {
                broadcastHeartRate(bpm, deviceName);
            }
        });
        hrmManager.start();
    }

    // --- Watchdog ---

    private void startWatchdog() {
        watchdogTimer = new Timer("BikeWatchdog");
        watchdogTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (workoutActive && sessionOwnerPkg != null) {
                    long elapsed = System.currentTimeMillis() - lastHeartbeatTime;
                    if (elapsed > 15000) { // 15 seconds, 3 missed heartbeats
                        Log.w(TAG, "Watchdog: client " + sessionOwnerPkg + " missed heartbeats, stopping workout");
                        handleWorkoutTimeout();
                    }
                }
            }
        }, 5000, 5000);
    }

    private void handleWorkoutTimeout() {
        synchronized (this) {
            if (workoutActive) {
                doStopWorkout("watchdog_timeout");
            }
            // Don't clear sessionOwnerPkg - let the client reclaim or release
        }
    }

    // --- Workout Control ---

    private static final int FW_STATE_SELECTION = 8;
    private static final int FW_STATE_WORKOUT = 9;

    // Shared outgoing message counter (used by writer thread and command methods)
    private int outSeq = 0;

    private int nextSeq() {
        outSeq = (outSeq + 1) & 0xFF;
        return outSeq;
    }

    private boolean doStartWorkout() {
        Log.d(TAG, "doStartWorkout: fw=" + currentFirmwareState + " — activating workout session");
        workoutActive = true;
        workoutPending = false;
        lastHeartbeatTime = System.currentTimeMillis();

        // JRNY sends STREAMING_CONTROL as POST (msgType=0x02, Purpose.POST)
        byte[] streamPayload = new byte[]{(byte) MSG_TYPE_NOTIFICATION, 0x07, (byte) nextSeq(), 0x01, 0x00};
        serialWriteQueue.offer(buildUcbFrame(streamPayload));
        Log.d(TAG, "Queued STREAMING_CONTROL enable (POST)");

        // JRNY sends WORKOUT_BLE_DATA as POST (msgType=0x02) at 1Hz from engine thread.
        // The UCB transitions SELECTION→WORKOUT when it receives this.
        byte[] bleData = buildWorkoutBleData();
        byte[] payload = new byte[3 + bleData.length];
        payload[0] = (byte) MSG_TYPE_NOTIFICATION; // Purpose.POST = 0x02
        payload[1] = 0x31; // WORKOUT_BLE_DATA
        payload[2] = (byte) nextSeq();
        System.arraycopy(bleData, 0, payload, 3, bleData.length);
        serialWriteQueue.offer(buildUcbFrame(payload));
        Log.d(TAG, "Queued WORKOUT_BLE_DATA (POST, 51 bytes)");

        broadcastWorkoutState(true, "started");
        return true;
    }

    /**
     * Build a minimal WORKOUT_BLE_DATA payload (51 bytes).
     * Layout matches C9C10BleWorkoutdata: 11 LE floats + 1 uint8 + 1 BE uint32 + 1 BE uint16
     */
    private byte[] buildWorkoutBleData() {
        byte[] data = new byte[51];
        // All floats default to 0.0f (zeros)
        data[44] = 0x01; // resistanceLevel (default 1)
        return data;
    }

    private boolean doStopWorkout(String reason) {
        // Disable streaming: STREAMING_CONTROL (0x07) with enabled=0 (POST type like JRNY)
        byte[] streamPayload = new byte[]{(byte) MSG_TYPE_NOTIFICATION, 0x07, (byte) nextSeq(), 0x00, 0x00};
        serialWriteQueue.offer(buildUcbFrame(streamPayload));
        Log.d(TAG, "Queued STREAMING_CONTROL disable (POST)");

        workoutActive = false;
        workoutPending = false;
        broadcastWorkoutState(false, reason);
        return true;
    }

    private boolean doSetResistance(int level) {
        // RES_TARGET (0x12) — JRNY sends as POST (Purpose.POST = 0x02)
        byte[] data = new byte[]{
            (byte)(level & 0xFF),
            (byte)((level >> 8) & 0xFF),
            (byte)((level >> 16) & 0xFF),
            (byte)((level >> 24) & 0xFF)
        };
        byte[] payload = new byte[3 + data.length];
        payload[0] = (byte) MSG_TYPE_NOTIFICATION; // POST
        payload[1] = 0x12; // RES_TARGET
        payload[2] = (byte) nextSeq();
        System.arraycopy(data, 0, payload, 3, data.length);
        serialWriteQueue.offer(buildUcbFrame(payload));
        Log.d(TAG, "Queued resistance set to " + level);
        return true;
    }

    // --- AIDL Broadcast helpers ---

    private void broadcastSensorData(int resistance, int rpm, int tilt, float power, long crankRevCount, int crankEventTime) {
        int n = bikeListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                bikeListeners.getBroadcastItem(i).onSensorData(resistance, rpm, tilt, power, crankRevCount, crankEventTime);
            } catch (RemoteException e) {
                // RemoteCallbackList handles removal of dead listeners
            }
        }
        bikeListeners.finishBroadcast();
    }

    private void broadcastFirmwareState(int state, String stateName) {
        int n = bikeListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                bikeListeners.getBroadcastItem(i).onFirmwareStateChanged(state, stateName);
            } catch (RemoteException e) {
            }
        }
        bikeListeners.finishBroadcast();
    }

    private void broadcastConnectionChanged(boolean connected, String message) {
        serialConnected = connected;
        int n = bikeListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                bikeListeners.getBroadcastItem(i).onConnectionChanged(connected, message);
            } catch (RemoteException e) {
            }
        }
        bikeListeners.finishBroadcast();
    }

    private void broadcastWorkoutState(boolean active, String reason) {
        int n = bikeListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                bikeListeners.getBroadcastItem(i).onWorkoutStateChanged(active, reason);
            } catch (RemoteException e) {
            }
        }
        bikeListeners.finishBroadcast();
    }

    private void broadcastHeartRate(int bpm, String deviceName) {
        int n = bikeListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                bikeListeners.getBroadcastItem(i).onHeartRate(bpm, deviceName);
            } catch (RemoteException e) {
            }
        }
        bikeListeners.finishBroadcast();
    }

    // --- Write Arbitration ---

    private boolean acquireExclusiveWrite(String owner) {
        synchronized (exclusiveWriteLock) {
            if (exclusiveWriteOwner == null) {
                exclusiveWriteOwner = owner;
                Log.d(TAG, "Exclusive write acquired by: " + owner);
                return true;
            }
            Log.w(TAG, "Exclusive write denied for " + owner + " (owner: " + exclusiveWriteOwner + ")");
            return false;
        }
    }

    private void releaseExclusiveWrite() {
        synchronized (exclusiveWriteLock) {
            Log.d(TAG, "Exclusive write released by: " + exclusiveWriteOwner);
            exclusiveWriteOwner = null;
        }
    }

    // --- OTA Flash ---

    private void doStartOtaFlash(android.os.ParcelFileDescriptor firmwareFd) {
        if (!acquireExclusiveWrite("ota_flash")) {
            broadcastOtaComplete(false, "Another exclusive operation is in progress");
            return;
        }

        new Thread(() -> {
            try {
                // Read firmware file from ParcelFileDescriptor
                FileInputStream fis = new FileInputStream(firmwareFd.getFileDescriptor());
                byte[] data = readAllBytes(fis);
                fis.close();
                firmwareFd.close();

                Log.d(TAG, "OTA: Read " + data.length + " bytes from firmware file");

                // Try parsing as hex text first, then as binary
                OtaFirmwareFile firmware;
                try {
                    String text = new String(data, "UTF-8");
                    firmware = OtaFirmwareFile.parse(text);
                } catch (OtaFirmwareFile.FirmwareParseException e) {
                    // Try as raw binary
                    firmware = OtaFirmwareFile.parseFromBinary(data);
                }

                Log.d(TAG, "OTA: Firmware parsed — " + firmware.getBlockCount() + " blocks, class=" + firmware.getMachineClass());

                OtaFlashSession.FrameSender frameSender = (msgId, frameData) -> {
                    int seq = nextSeq();
                    byte[] payload = new byte[3 + frameData.length];
                    payload[0] = (byte) MSG_TYPE_NOTIFICATION; // POST
                    payload[1] = (byte) msgId;
                    payload[2] = (byte) seq;
                    System.arraycopy(frameData, 0, payload, 3, frameData.length);
                    serialWriteQueue.offer(buildUcbFrame(payload));

                    // Also broadcast as raw frame if monitoring
                    if (rawFrameMonitorEnabled) {
                        broadcastRawFrame(payload, true);
                    }
                    return seq;
                };

                OtaFlashSession.Listener sessionListener = new OtaFlashSession.Listener() {
                    @Override
                    public void onPhaseChanged(OtaFlashSession.Phase phase) {
                        Log.d(TAG, "OTA phase: " + OtaFlashSession.phaseName(phase));
                        broadcastOtaProgress(phase.ordinal(), 0, 0);
                    }

                    @Override
                    public void onBlockProgress(int current, int total) {
                        if (current % 10 == 0 || current == total) {
                            Log.d(TAG, "OTA: block " + current + "/" + total);
                        }
                        broadcastOtaProgress(OtaFlashSession.Phase.WRITE.ordinal(), current, total);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "OTA error: " + error);
                    }

                    @Override
                    public void onComplete(boolean success) {
                        Log.d(TAG, "OTA complete: " + (success ? "SUCCESS" : "FAILED"));
                        releaseExclusiveWrite();
                        broadcastOtaComplete(success, success ? null : "Flash failed");
                    }
                };

                otaSession = new OtaFlashSession(firmware, frameSender, sessionListener);
                otaSession.start();

            } catch (Exception e) {
                Log.e(TAG, "OTA flash failed: " + e.getMessage(), e);
                releaseExclusiveWrite();
                broadcastOtaComplete(false, "Parse error: " + e.getMessage());
            }
        }, "OtaFlashThread").start();
    }

    private void doCancelOtaFlash() {
        OtaFlashSession session = otaSession;
        if (session != null && session.getPhase() != OtaFlashSession.Phase.IDLE
                && session.getPhase() != OtaFlashSession.Phase.COMPLETE
                && session.getPhase() != OtaFlashSession.Phase.FAILED) {
            session.cancel();
        }
    }

    private byte[] readAllBytes(java.io.InputStream is) throws java.io.IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    // --- Calibration ---

    private void doStartCalibration(int calibrationType) {
        if (!acquireExclusiveWrite("calibration")) {
            broadcastCalibrationComplete(false);
            return;
        }

        calibrationActive = true;
        calibrationPhase = MfgTestCommand.CAL_PHASE_ZERO;

        // Enter MFG test state
        byte[] enterPayload = MfgTestCommand.enterMfgTestState();
        byte[] payload = new byte[3 + enterPayload.length];
        payload[0] = (byte) MSG_TYPE_NOTIFICATION; // POST
        payload[1] = (byte) UcbMessageIds.MFG_TEST;
        payload[2] = (byte) nextSeq();
        System.arraycopy(enterPayload, 0, payload, 3, enterPayload.length);
        serialWriteQueue.offer(buildUcbFrame(payload));
        Log.d(TAG, "Calibration: entering MFG mode");

        // Start first calibration phase
        broadcastCalibrationProgress(calibrationPhase,
            MfgTestCommand.phaseInstruction(calibrationPhase));

        byte[] calPayload = MfgTestCommand.calibrateResistance(calibrationPhase);
        byte[] calFrame = new byte[3 + calPayload.length];
        calFrame[0] = (byte) MSG_TYPE_NOTIFICATION;
        calFrame[1] = (byte) UcbMessageIds.MFG_TEST;
        calFrame[2] = (byte) nextSeq();
        System.arraycopy(calPayload, 0, calFrame, 3, calPayload.length);
        serialWriteQueue.offer(buildUcbFrame(calFrame));
    }

    private void doCancelCalibration() {
        if (!calibrationActive) return;

        byte[] cancelPayload = MfgTestCommand.cancelCalibration();
        byte[] payload = new byte[3 + cancelPayload.length];
        payload[0] = (byte) MSG_TYPE_NOTIFICATION;
        payload[1] = (byte) UcbMessageIds.MFG_TEST;
        payload[2] = (byte) nextSeq();
        System.arraycopy(cancelPayload, 0, payload, 3, cancelPayload.length);
        serialWriteQueue.offer(buildUcbFrame(payload));

        // Exit MFG mode
        byte[] exitPayload = MfgTestCommand.exitMfgTestState();
        byte[] exitFrame = new byte[3 + exitPayload.length];
        exitFrame[0] = (byte) MSG_TYPE_NOTIFICATION;
        exitFrame[1] = (byte) UcbMessageIds.MFG_TEST;
        exitFrame[2] = (byte) nextSeq();
        System.arraycopy(exitPayload, 0, exitFrame, 3, exitPayload.length);
        serialWriteQueue.offer(buildUcbFrame(exitFrame));

        calibrationActive = false;
        releaseExclusiveWrite();
        broadcastCalibrationComplete(false);
        Log.d(TAG, "Calibration cancelled");
    }

    private void doConfirmCalibrationStep() {
        if (!calibrationActive) return;

        // Send user confirm for current phase
        byte[] confirmPayload = MfgTestCommand.confirmCalibrationStep();
        byte[] payload = new byte[3 + confirmPayload.length];
        payload[0] = (byte) MSG_TYPE_NOTIFICATION;
        payload[1] = (byte) UcbMessageIds.MFG_TEST;
        payload[2] = (byte) nextSeq();
        System.arraycopy(confirmPayload, 0, payload, 3, confirmPayload.length);
        serialWriteQueue.offer(buildUcbFrame(payload));
        Log.d(TAG, "Calibration: confirmed phase " + calibrationPhase);

        // Advance to next phase
        calibrationPhase++;
        if (calibrationPhase > MfgTestCommand.CAL_PHASE_CENTER) {
            // Calibration complete — exit MFG mode
            byte[] exitPayload = MfgTestCommand.exitMfgTestState();
            byte[] exitFrame = new byte[3 + exitPayload.length];
            exitFrame[0] = (byte) MSG_TYPE_NOTIFICATION;
            exitFrame[1] = (byte) UcbMessageIds.MFG_TEST;
            exitFrame[2] = (byte) nextSeq();
            System.arraycopy(exitPayload, 0, exitFrame, 3, exitPayload.length);
            serialWriteQueue.offer(buildUcbFrame(exitFrame));

            calibrationActive = false;
            releaseExclusiveWrite();
            broadcastCalibrationComplete(true);
            Log.d(TAG, "Calibration: all phases complete");
        } else {
            // Start next phase
            broadcastCalibrationProgress(calibrationPhase,
                MfgTestCommand.phaseInstruction(calibrationPhase));

            byte[] calPayload = MfgTestCommand.calibrateResistance(calibrationPhase);
            byte[] calFrame = new byte[3 + calPayload.length];
            calFrame[0] = (byte) MSG_TYPE_NOTIFICATION;
            calFrame[1] = (byte) UcbMessageIds.MFG_TEST;
            calFrame[2] = (byte) nextSeq();
            System.arraycopy(calPayload, 0, calFrame, 3, calPayload.length);
            serialWriteQueue.offer(buildUcbFrame(calFrame));
        }
    }

    // --- New Broadcast Helpers ---

    private void broadcastOtaProgress(int phase, int blockCurrent, int blockTotal) {
        int n = bikeListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                bikeListeners.getBroadcastItem(i).onOtaProgress(phase, blockCurrent, blockTotal);
            } catch (RemoteException e) {
            }
        }
        bikeListeners.finishBroadcast();
    }

    private void broadcastOtaComplete(boolean success, String error) {
        int n = bikeListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                bikeListeners.getBroadcastItem(i).onOtaComplete(success, error);
            } catch (RemoteException e) {
            }
        }
        bikeListeners.finishBroadcast();
    }

    private void broadcastCalibrationProgress(int step, String instruction) {
        int n = bikeListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                bikeListeners.getBroadcastItem(i).onCalibrationProgress(step, instruction);
            } catch (RemoteException e) {
            }
        }
        bikeListeners.finishBroadcast();
    }

    private void broadcastCalibrationComplete(boolean success) {
        int n = bikeListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                bikeListeners.getBroadcastItem(i).onCalibrationComplete(success);
            } catch (RemoteException e) {
            }
        }
        bikeListeners.finishBroadcast();
    }

    private void broadcastRawFrame(byte[] frame, boolean isOutgoing) {
        int n = bikeListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                bikeListeners.getBroadcastItem(i).onRawFrame(frame, isOutgoing);
            } catch (RemoteException e) {
            }
        }
        bikeListeners.finishBroadcast();
    }

    // --- UCB Frame Parsing ---

    /**
     * Parse a complete UCB frame (hex-encoded payload between STX and ETX).
     * Extracts sensor data from C9C10StreamNotification (msgId=0x08) frames.
     */
    private void parseUcbFrame(byte[] frameData, int len) {
        // frameData is the raw bytes between STX and ETX (hex-encoded)
        // Decode hex string
        String hexStr = new String(frameData, 0, len);
        if (hexStr.length() < 2 || hexStr.length() % 2 != 0) return;

        byte[] decoded;
        try {
            decoded = hexDecode(hexStr);
        } catch (Exception e) {
            return;
        }

        if (decoded.length < 5) return; // minimum: msgType(1) + msgId(1) + counter(1) + CRC32(4)

        // Verify CRC32
        int dataLen = decoded.length - 4;
        CRC32 crc = new CRC32();
        crc.update(decoded, 0, dataLen);
        long calcCrc = crc.getValue() ^ 0xFFFFFFFFL; // undo final XOR

        long frameCrc = ((long)(decoded[dataLen] & 0xFF) << 24)
                      | ((long)(decoded[dataLen + 1] & 0xFF) << 16)
                      | ((long)(decoded[dataLen + 2] & 0xFF) << 8)
                      | ((long)(decoded[dataLen + 3] & 0xFF));

        if (calcCrc != frameCrc) return; // CRC mismatch

        int msgType = decoded[0] & 0xFF;
        int msgId = decoded[1] & 0xFF;
        int counter = decoded[2] & 0xFF;

        // Log all incoming message types (first 50 frames, then every 100th)
        if (frameCount <= 50 || frameCount % 100 == 0) {
            Log.d(TAG, "RX: type=" + msgType + " id=0x" + String.format("%02X", msgId)
                + " seq=" + counter + " len=" + dataLen + " (frame#" + frameCount + ")");
        }

        // Parse C9C10StreamNotification (msgId=0x08, msgType=0x02)
        if (msgType == MSG_TYPE_NOTIFICATION && msgId == MSG_ID_STREAM_NTFCN) {
            parseStreamNotification(decoded, dataLen);
        }

        // Parse heartbeat notification from UCB — track firmware state
        // NOTE: JRNY does NOT ACK heartbeat notifications — removed sendHeartbeatAck
        if (msgType == MSG_TYPE_NOTIFICATION && msgId == MSG_ID_SYSTEM_HEART_BEAT) {
            if (dataLen > 3) {
                int fwState = decoded[3] & 0xFF;
                if (fwState != currentFirmwareState) {
                    int oldState = currentFirmwareState;
                    currentFirmwareState = fwState;
                    String stateName = firmwareStateName(fwState);
                    Log.d(TAG, "Firmware state: " + oldState + " -> " + fwState + " (" + stateName + ")");
                    broadcastFirmwareState(fwState, stateName);

                    // If a workout was pending and UCB just reached SELECTION, fire it now
                    if (fwState == FW_STATE_SELECTION && workoutPending) {
                        Log.d(TAG, "UCB reached SELECTION — firing deferred workout start");
                        synchronized (BridgeService.this) {
                            doStartWorkout();
                        }
                    }
                }
            }
        }

        // SYSTEM_DATA response — parse hardware info and firmware version
        if (msgId == MSG_ID_SYSTEM_DATA && dataLen > 3) {
            int payloadLen = dataLen - 3;
            Log.d(TAG, "SYSTEM_DATA response: " + payloadLen + " bytes");
            if (payloadLen >= 1) {
                ucbHardwareId = decoded[3] & 0xFF;
                Log.d(TAG, "SYSTEM_DATA hwId=" + ucbHardwareId);
            }
            // Extract ASCII content from payload — contains serial number and hardware info
            if (payloadLen >= 10) {
                StringBuilder ascii = new StringBuilder();
                for (int i = 3; i < dataLen; i++) {
                    byte b = decoded[i];
                    if (b >= 32 && b < 127) ascii.append((char) b);
                }
                String content = ascii.toString().trim();
                Log.d(TAG, "SYSTEM_DATA ascii: " + content);

                // Extract serial number (pattern: digits + letters, 20+ chars)
                java.util.regex.Matcher snMatcher = java.util.regex.Pattern
                    .compile("[0-9A-Z]{15,}").matcher(content);
                if (snMatcher.find()) {
                    ucbSerialNumber = snMatcher.group();
                    Log.i(TAG, "UCB serial: " + ucbSerialNumber);
                }

                // Look for explicit version pattern like "R_5.87.5" or "G_3.0.0"
                java.util.regex.Matcher vMatcher = java.util.regex.Pattern
                    .compile("[RG]_\\d+\\.\\d+\\.\\d+").matcher(content);
                if (vMatcher.find()) {
                    ucbFirmwareVersion = vMatcher.group();
                } else if (ucbHardwareId > 0 && ucbHardwareId != 0xFF) {
                    // Derive partial version from hardware ID convention: R_5.{hwId}.x
                    ucbFirmwareVersion = "R_5." + ucbHardwareId + ".x";
                }
                Log.i(TAG, "UCB firmware version: " + ucbFirmwareVersion + " hwId=" + ucbHardwareId);
            }
        }

        // CONNECT_SESSION_CNTRL (0x16) — UCB offers session accept/reject
        // JRNY processes this to confirm connection established
        if (msgId == 0x16 && dataLen > 3) {
            int sessionStatus = decoded[3] & 0xFF;
            Log.d(TAG, "CONNECT_SESSION_CNTRL: status=" + sessionStatus
                + " (" + (sessionStatus == 0 ? "ACCEPT" : "REJECT") + ")");
        }

        // Route OTA_SESSION_CONTROL responses to active OTA session
        if (msgId == 0x15 && dataLen > 3) { // OTA_SESSION_CONTROL
            int otaCode = decoded[3] & 0xFF;
            Log.d(TAG, "OTA_SESSION_CONTROL: " + OtaFlashSession.otaResponseName(otaCode));
            OtaFlashSession session = otaSession;
            if (session != null) {
                session.onOtaResponse(otaCode);
            }
        }

        // Broadcast raw frame if monitoring enabled
        if (rawFrameMonitorEnabled) {
            byte[] rawData = new byte[dataLen];
            System.arraycopy(decoded, 0, rawData, 0, dataLen);
            broadcastRawFrame(rawData, false);
        }
    }

    /**
     * Parse C9C10StreamNotification: 23 bytes data (after msgType/msgId/counter).
     * Matches SensorData.java from ucblib:
     *   [0-3]   resistanceLevel (int32 BE)
     *   [4-7]   rpm (int32 BE)
     *   [8-11]  tilt (int32 BE)
     *   [12-15] power (float32 LE)
     *   [16-19] crankRevCount (uint32 BE)
     *   [20-21] crankEventTime (uint16 BE)
     *   [22]    error (uint8)
     */
    private void parseStreamNotification(byte[] decoded, int dataLen) {
        // Data starts at offset 3 (after msgType, msgId, counter)
        int dataStart = 3;
        int payloadLen = dataLen - dataStart;
        if (payloadLen < STREAM_NTFCN_DATA_LEN) return;

        // Resistance: int32 BE at offset 0
        int resistance = ((decoded[dataStart] & 0xFF) << 24)
                       | ((decoded[dataStart + 1] & 0xFF) << 16)
                       | ((decoded[dataStart + 2] & 0xFF) << 8)
                       | (decoded[dataStart + 3] & 0xFF);

        // RPM: int32 BE at offset 4
        int rpm = ((decoded[dataStart + 4] & 0xFF) << 24)
                | ((decoded[dataStart + 5] & 0xFF) << 16)
                | ((decoded[dataStart + 6] & 0xFF) << 8)
                | (decoded[dataStart + 7] & 0xFF);

        // Tilt: int32 BE at offset 8
        int tilt = ((decoded[dataStart + 8] & 0xFF) << 24)
                 | ((decoded[dataStart + 9] & 0xFF) << 16)
                 | ((decoded[dataStart + 10] & 0xFF) << 8)
                 | (decoded[dataStart + 11] & 0xFF);

        // Power: float32 LE at offset 12
        int powerBits = (decoded[dataStart + 12] & 0xFF)
                      | ((decoded[dataStart + 13] & 0xFF) << 8)
                      | ((decoded[dataStart + 14] & 0xFF) << 16)
                      | ((decoded[dataStart + 15] & 0xFF) << 24);
        float power = Float.intBitsToFloat(powerBits);

        // Crank rev count: uint32 BE at offset 16
        long crankRevCount = ((long)(decoded[dataStart + 16] & 0xFF) << 24)
                           | ((long)(decoded[dataStart + 17] & 0xFF) << 16)
                           | ((long)(decoded[dataStart + 18] & 0xFF) << 8)
                           | ((long)(decoded[dataStart + 19] & 0xFF));

        // Crank event time: uint16 BE at offset 20
        int crankEventTime = ((decoded[dataStart + 20] & 0xFF) << 8) | (decoded[dataStart + 21] & 0xFF);

        sensorBroadcastCount++;
        if (sensorBroadcastCount <= 5 || sensorBroadcastCount % 30 == 0) {
            Log.d(TAG, "Sensor: res=" + resistance + " rpm=" + rpm + " power=" + power + " (broadcast #" + sensorBroadcastCount + ")");
        }
        broadcastSensorData(resistance, rpm, tilt, power, crankRevCount, crankEventTime);
    }

    private static byte[] hexDecode(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte)((Character.digit(hex.charAt(i), 16) << 4)
                                + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String firmwareStateName(int state) {
        switch (state) {
            case 0: return "BOOT_FAILSAFE";
            case 1: return "POWER_ON_0";
            case 2: return "POWER_ON_1";
            case 3: return "POWER_ON_2";
            case 4: return "UPDATES";
            case 5: return "TRANSITION";
            case 6: return "MFG";
            case 7: return "OTA";
            case 8: return "SELECTION";
            case 9: return "WORKOUT";
            case 10: return "SLEEP";
            case 11: return "RESET";
            case 12: return "SBC_DISCONNECTED";
            default: return "UNKNOWN_" + state;
        }
    }

    // --- Notification ---

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Serial Bridge", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        return b.setContentTitle("Serial Bridge")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .build();
    }

    private void updateNotification(String text) {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(2, buildNotification(text));
        } catch (Exception e) {}
    }

    // --- TCP Bridge (backward compatible) ---

    private void startBridge() {
        running = true;
        mainThread = new Thread(new Runnable() {
            public void run() {
                while (running) {
                    try {
                        runBridgeCycle();
                    } catch (Exception e) {
                        Log.e(TAG, "Bridge cycle error: " + e.getMessage());
                    }
                    if (running) {
                        updateNotification("Reconnecting serial...");
                        broadcastConnectionChanged(false, "reconnecting");
                        try { Thread.sleep(3000); } catch (Exception e) {}
                    }
                }
            }
        });
        mainThread.setDaemon(true);
        mainThread.start();
    }

    private void runBridgeCycle() {
        // Step 1: chmod and open serial port
        Log.d(TAG, "Opening " + SERIAL_DEVICE + " @ " + BAUD_RATE);
        updateNotification("Opening serial port...");

        try {
            Runtime.getRuntime().exec("chmod 666 " + SERIAL_DEVICE).waitFor();
        } catch (Exception e) {
            Log.w(TAG, "chmod failed (may be ok): " + e.getMessage());
        }

        // Try ucbsystemdata lib first (used by UcbSerialPortThread in launcher)
        boolean opened = false;
        try {
            Log.d(TAG, "Trying ucbsystemdata serial port lib...");
            com.nautilus.ucbsystemdata.android_serialport_api.SerialPort ucbPort =
                new com.nautilus.ucbsystemdata.android_serialport_api.SerialPort(
                    new File(SERIAL_DEVICE), BAUD_RATE, 0);
            serialPortObj = ucbPort;
            serialInputStream = ucbPort.getInputStream();
            serialOutputStream = ucbPort.getOutputStream();
            opened = true;
            Log.d(TAG, "Serial port opened with ucbsystemdata lib!");
        } catch (Exception e) {
            Log.w(TAG, "ucbsystemdata lib failed: " + e.getMessage());
        }

        // Fallback to serial_portx lib
        if (!opened) {
            try {
                Log.d(TAG, "Trying serial_portx lib...");
                com.nautilus.serial.android_serialport_api.SerialPort sPort =
                    new com.nautilus.serial.android_serialport_api.SerialPort(
                        new File(SERIAL_DEVICE), BAUD_RATE, 0);
                serialPortObj = sPort;
                serialInputStream = sPort.getInputStream();
                serialOutputStream = sPort.getOutputStream();
                opened = true;
                Log.d(TAG, "Serial port opened with serial_portx lib!");
            } catch (Exception e) {
                Log.e(TAG, "Cannot open serial port: " + e.getMessage());
                return;
            }
        }

        broadcastConnectionChanged(true, "serial port opened");

        final InputStream serialIn = serialInputStream;
        final OutputStream serialOut = serialOutputStream;

        // Step 1b: Serial writer thread — sends init requests, then heartbeat loop
        Thread writerThread = new Thread(new Runnable() {
            public void run() {
                try {
                    // Phase 1: Send 5 SYSTEM_DATA GET requests (like JRNY's getMessage(SYSTEM_DATA))
                    for (int i = 1; i <= 5 && running && serialPortObj != null; i++) {
                        byte[] initPayload = new byte[]{(byte) MSG_TYPE_REQUEST, (byte) MSG_ID_SYSTEM_DATA, (byte) nextSeq()};
                        serialOut.write(buildUcbFrame(initPayload));
                        serialOut.flush();
                        Log.d(TAG, "INIT SYSTEM_DATA GET #" + i);
                        Thread.sleep(500);
                        drainWriteQueue(serialOut);
                        Thread.sleep(500);
                    }
                    Log.d(TAG, "Init complete — waiting 3s for UCB to settle...");
                    Thread.sleep(3000);
                    drainWriteQueue(serialOut);
                    Log.d(TAG, "UCB fw=" + currentFirmwareState + " — entering heartbeat loop (workout commands on demand)");

                    // Phase 2: Continuous heartbeat + workout keepalive loop
                    // JRNY sends heartbeats every 3s (POST/0x02) and WORKOUT_BLE_DATA at 1Hz (POST/0x02)
                    int tick = 0;
                    while (running && serialPortObj != null) {
                        drainWriteQueue(serialOut);

                        tick++;

                        // Send SYSTEM_HEART_BEAT every 3s as POST (0x02) — JRNY uses Purpose.POST
                        if (tick % 3 == 0) {
                            int hbSeq = nextSeq();
                            byte[] hbPayload = new byte[]{(byte) MSG_TYPE_NOTIFICATION, (byte) MSG_ID_SYSTEM_HEART_BEAT, (byte) hbSeq};
                            serialOut.write(buildUcbFrame(hbPayload));
                            serialOut.flush();
                        }

                        // Send WORKOUT_BLE_DATA every 1s as POST (0x02) when workout active
                        // JRNY engine thread sends this at 1Hz to maintain WORKOUT state
                        if (workoutActive) {
                            byte[] bleData = buildWorkoutBleData();
                            byte[] kaPayload = new byte[3 + bleData.length];
                            kaPayload[0] = (byte) MSG_TYPE_NOTIFICATION; // POST
                            kaPayload[1] = 0x31; // WORKOUT_BLE_DATA
                            kaPayload[2] = (byte) nextSeq();
                            System.arraycopy(bleData, 0, kaPayload, 3, bleData.length);
                            serialOut.write(buildUcbFrame(kaPayload));
                            serialOut.flush();
                        }

                        Thread.sleep(1000);
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Writer thread ended: " + e.getMessage());
                }
            }
        });
        writerThread.setDaemon(true);
        writerThread.start();

        // Step 2: Start TCP server
        try {
            if (serverSocket == null || serverSocket.isClosed()) {
                serverSocket = new ServerSocket(TCP_PORT);
                serverSocket.setReuseAddress(true);
                serverSocket.setSoTimeout(2000);
            }
        } catch (Exception e) {
            Log.e(TAG, "Cannot start TCP server on " + TCP_PORT + ": " + e.getMessage());
            closeSerialPort();
            return;
        }

        updateNotification("Listening on TCP:" + TCP_PORT);
        Log.d(TAG, "TCP server listening on port " + TCP_PORT);

        // Step 3: Serial reader thread - blocking single-byte read like launcher's UcbSerialPortThread
        final long[] stats = {0, 0, 0}; // [0]=serialRead, [1]=tcpWritten, [2]=readCount
        Thread serialReader = new Thread(new Runnable() {
            public void run() {
                byte[] single = new byte[1];
                byte[] accumBuf = new byte[BUFFER_SIZE];
                int accumLen = 0;
                long lastLog = System.currentTimeMillis();
                Log.d(TAG, "Serial reader starting (blocking single-byte mode)");
                try {
                    while (running && serialPortObj != null) {
                        int n = serialIn.read(single, 0, 1);
                        stats[2]++;
                        if (n > 0) {
                            stats[0]++;
                            accumBuf[accumLen++] = single[0];

                            // Also accumulate for AIDL frame parsing
                            accumulateFrameByte(single[0]);

                            // When we have a full frame or buffer is getting full, flush to TCP
                            if (single[0] == ETX || accumLen >= BUFFER_SIZE - 1) {
                                if (stats[0] <= 2000) {
                                    StringBuilder hex = new StringBuilder();
                                    for (int i = 0; i < accumLen; i++) {
                                        hex.append(String.format("%02X ", accumBuf[i] & 0xFF));
                                    }
                                    Log.d(TAG, "SER->" + accumLen + "b [" + hex.toString().trim() + "]");
                                }

                                // Forward to TCP client
                                Socket client = currentClient;
                                if (client != null && !client.isClosed()) {
                                    try {
                                        client.getOutputStream().write(accumBuf, 0, accumLen);
                                        client.getOutputStream().flush();
                                        stats[1] += accumLen;
                                    } catch (Exception e) {
                                        Log.d(TAG, "TCP write failed: " + e.getMessage());
                                        closeQuiet(client);
                                        currentClient = null;
                                    }
                                }
                                accumLen = 0;
                            }
                        } else if (n == 0) {
                            Thread.sleep(1);
                        } else {
                            Log.d(TAG, "Serial read returned " + n + " (EOF?)");
                            break;
                        }

                        long now = System.currentTimeMillis();
                        if (now - lastLog > 5000) {
                            Log.d(TAG, "Serial: read=" + stats[0] + " calls=" + stats[2] +
                                " (blocking=" + (stats[2] - stats[0]) + ")" +
                                " listeners=" + bikeListeners.getRegisteredCallbackCount() +
                                " fw=" + currentFirmwareState +
                                " workout=" + workoutActive +
                                " session=" + sessionOwnerPkg +
                                " frames=" + frameCount +
                                " sensors=" + sensorBroadcastCount +
                                (workoutPending ? " PENDING" : ""));
                            lastLog = now;
                        }
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Serial reader ended: " + e.getMessage());
                }
                Log.d(TAG, "Serial reader stopped. Read=" + stats[0] + " Written=" + stats[1]);
            }
        });
        serialReader.setDaemon(true);
        serialReader.start();

        // Step 4: Accept TCP clients and relay their writes to serial
        try {
            while (running && serialPortObj != null) {
                Socket client;
                try {
                    client = serverSocket.accept();
                } catch (java.net.SocketTimeoutException e) {
                    continue;
                }

                Log.d(TAG, "TCP client connected from " + client.getRemoteSocketAddress());
                updateNotification("Client connected | read:" + stats[0]);

                if (currentClient != null) {
                    closeQuiet(currentClient);
                }
                client.setTcpNoDelay(true);
                currentClient = client;

                final Socket cli = client;
                Thread clientReader = new Thread(new Runnable() {
                    public void run() {
                        byte[] buf = new byte[BUFFER_SIZE];
                        try {
                            InputStream cIn = cli.getInputStream();
                            while (running && !cli.isClosed() && serialPortObj != null) {
                                int n = cIn.read(buf);
                                if (n <= 0) break;
                                Log.d(TAG, "TCP->" + n + "b to serial");
                                serialOut.write(buf, 0, n);
                                serialOut.flush();
                            }
                        } catch (Exception e) {
                            Log.d(TAG, "Client reader ended: " + e.getMessage());
                        }
                    }
                });
                clientReader.setDaemon(true);
                clientReader.start();
            }
        } catch (Exception e) {
            Log.d(TAG, "Accept loop ended: " + e.getMessage());
        }

        broadcastConnectionChanged(false, "bridge cycle ended");
        closeSerialPort();
    }

    // --- Frame accumulator for AIDL parsing ---

    // Called only from serial reader thread — no synchronization needed
    private void accumulateFrameByte(byte b) {
        if (b == STX) {
            inFrame = true;
            frameAccumLen = 0;
            return;
        }
        if (b == ETX && inFrame) {
            inFrame = false;
            frameCount++;
            if (frameAccumLen > 0) {
                try {
                    parseUcbFrame(frameAccumBuf, frameAccumLen);
                } catch (Exception e) {
                    Log.w(TAG, "Frame parse error: " + e.getMessage());
                }
            }
            frameAccumLen = 0;
            return;
        }
        if (inFrame && frameAccumLen < frameAccumBuf.length) {
            frameAccumBuf[frameAccumLen++] = b;
        }
    }

    // --- Serial write queue ---

    private void drainWriteQueue(OutputStream out) {
        byte[] frame;
        int count = 0;
        while ((frame = serialWriteQueue.poll()) != null) {
            try {
                out.write(frame);
                out.flush();
                count++;
            } catch (Exception e) {
                Log.w(TAG, "Queue write failed: " + e.getMessage());
            }
        }
        if (count > 0) {
            Log.d(TAG, "Drained " + count + " queued frame(s) to serial");
        }
    }

    // --- UCB Frame Building ---

    /**
     * Build UCB frame: STX + hex_encode(payload + CRC32_BE) + ETX
     * CRC32: init=0xFFFFFFFF, no final XOR, big-endian byte order.
     */
    static byte[] buildUcbFrame(byte[] payload) {
        CRC32 crc = new CRC32();
        crc.update(payload);
        long crcVal = crc.getValue() ^ 0xFFFFFFFFL; // undo final XOR
        byte[] crcBe = new byte[]{
            (byte)((crcVal >> 24) & 0xFF), (byte)((crcVal >> 16) & 0xFF),
            (byte)((crcVal >> 8) & 0xFF), (byte)(crcVal & 0xFF)
        };
        byte[] raw = new byte[payload.length + 4];
        System.arraycopy(payload, 0, raw, 0, payload.length);
        System.arraycopy(crcBe, 0, raw, payload.length, 4);
        StringBuilder hexStr = new StringBuilder();
        for (byte b : raw) hexStr.append(String.format("%02X", b & 0xFF));
        byte[] hexBytes = hexStr.toString().getBytes();
        byte[] frame = new byte[hexBytes.length + 2];
        frame[0] = STX;
        System.arraycopy(hexBytes, 0, frame, 1, hexBytes.length);
        frame[frame.length - 1] = ETX;
        return frame;
    }

    // --- Cleanup ---

    private void closeQuiet(java.io.Closeable c) {
        try { if (c != null) c.close(); } catch (Exception e) {}
    }

    private void closeSerialPort() {
        if (serialPortObj instanceof com.nautilus.ucbsystemdata.android_serialport_api.SerialPort) {
            ((com.nautilus.ucbsystemdata.android_serialport_api.SerialPort) serialPortObj).closePort();
        } else if (serialPortObj instanceof com.nautilus.serial.android_serialport_api.SerialPort) {
            ((com.nautilus.serial.android_serialport_api.SerialPort) serialPortObj).closePort();
        }
        serialPortObj = null;
        serialInputStream = null;
        serialOutputStream = null;
    }

    private void cleanup() {
        closeQuiet(currentClient);
        closeSerialPort();
        closeQuiet(serverSocket);
    }
}
