package io.freewheel.ucb;

/**
 * Keys for the Bundle returned by IBikeService.getDiagnostics(), plus the shared rule for
 * turning those numbers into a user-facing link state.
 *
 * "Connected" used to mean "the AIDL service bound" or "open() on the tty succeeded", which
 * says nothing about whether the bike is actually talking. A valid (CRC-checked) frame within
 * {@link #FRESH_FRAME_MS} is the only thing that counts as "Bike connected".
 */
public final class BridgeDiagnostics {
    private BridgeDiagnostics() {}

    public static final String KEY_PORT_DEV = "portDev";
    public static final String KEY_BAUD = "baud";
    public static final String KEY_PORT_OPEN = "portOpen";
    public static final String KEY_BYTES_READ = "bytesRead";
    public static final String KEY_FRAMES_OK = "framesOk";
    public static final String KEY_CRC_ERRORS = "crcErrors";
    /** Milliseconds since the last valid frame, or -1 if none has ever been received. */
    public static final String KEY_LAST_FRAME_AGE_MS = "lastFrameAgeMs";
    public static final String KEY_FW_STATE = "fwState";
    public static final String KEY_FW_NAME = "fwName";
    /** Comma-separated pid:cmdline of other processes holding the serial device, or "none". */
    public static final String KEY_HOLDERS = "holders";

    public static final long FRESH_FRAME_MS = 5000;

    public enum LinkState {
        /** AIDL service not bound (FreewheelBridge not installed / not running). */
        BRIDGE_NOT_RUNNING("Bridge not running"),
        /** Bridge is running but the serial port could not be opened. */
        PORT_CLOSED("Bridge running – serial port not open"),
        /** Port open, but no valid frame from the bike in the last 5s. */
        PORT_OPEN_NO_DATA("Port open – no data from bike"),
        /** A valid frame arrived within the last 5s. */
        BIKE_CONNECTED("Bike connected");

        public final String label;

        LinkState(String label) {
            this.label = label;
        }
    }

    /**
     * @param serviceBound   AIDL connection to the bridge is up
     * @param portOpen       bridge reports the serial port open
     * @param lastFrameAgeMs ms since last valid frame, -1 if never
     */
    public static LinkState classify(boolean serviceBound, boolean portOpen, long lastFrameAgeMs) {
        if (!serviceBound) return LinkState.BRIDGE_NOT_RUNNING;
        if (lastFrameAgeMs >= 0 && lastFrameAgeMs <= FRESH_FRAME_MS) return LinkState.BIKE_CONNECTED;
        if (!portOpen) return LinkState.PORT_CLOSED;
        return LinkState.PORT_OPEN_NO_DATA;
    }
}
