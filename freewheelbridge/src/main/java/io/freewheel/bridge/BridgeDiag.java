package io.freewheel.bridge;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure (Android-free) helpers for SerialBridge diagnostics so they can be unit tested
 * on the JVM. Nothing in here touches the serial port or sends anything to the UCB.
 */
final class BridgeDiag {
    private BridgeDiag() {}

    // --- Serial device / baud overrides (Settings.Global) ---

    static final String SETTING_SERIAL_DEV = "freewheel_serial_dev";
    static final String SETTING_SERIAL_BAUD = "freewheel_serial_baud";

    static final String DEFAULT_SERIAL_DEV = "/dev/ttyS4";
    static final int DEFAULT_BAUD = 230400;

    private static final Pattern SERIAL_DEV_PATTERN = Pattern.compile("^/dev/tty[A-Za-z0-9]+$");

    /** Standard rates the serial_port JNI libs map to termios speeds. */
    static final int[] ALLOWED_BAUDS = {
        9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600
    };

    /** Returns the override if it is a plausible tty path, otherwise the default. */
    static String resolveSerialDev(String raw) {
        if (raw == null) return DEFAULT_SERIAL_DEV;
        String v = raw.trim();
        if (v.isEmpty() || "null".equals(v)) return DEFAULT_SERIAL_DEV;
        return SERIAL_DEV_PATTERN.matcher(v).matches() ? v : DEFAULT_SERIAL_DEV;
    }

    /** Returns the override if it is a whitelisted baud rate, otherwise the default. */
    static int resolveBaud(String raw) {
        if (raw == null) return DEFAULT_BAUD;
        int baud;
        try {
            baud = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_BAUD;
        }
        for (int allowed : ALLOWED_BAUDS) {
            if (allowed == baud) return baud;
        }
        return DEFAULT_BAUD;
    }

    // --- /proc/<pid>/fd scanning ---

    /** Parses a /proc entry name as a pid, or -1 if it is not numeric. */
    static int parsePid(String name) {
        if (name == null || name.isEmpty() || name.length() > 10) return -1;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < '0' || c > '9') return -1;
        }
        try {
            return Integer.parseInt(name);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** True if an fd symlink target refers to the serial device. */
    static boolean fdTargetMatches(String linkTarget, String serialDev) {
        if (linkTarget == null || serialDev == null) return false;
        // Deleted files show as "/path (deleted)"; a tty node is never deleted, but be exact.
        return linkTarget.equals(serialDev);
    }

    /**
     * /proc/<pid>/cmdline is NUL-separated argv; return argv[0] (the process/package name
     * for app processes), or "?" if empty.
     */
    static String cmdlineFromBytes(byte[] raw, int len) {
        if (raw == null || len <= 0) return "?";
        int end = 0;
        while (end < len && raw[end] != 0) end++;
        String s = new String(raw, 0, end, java.nio.charset.StandardCharsets.UTF_8).trim();
        return s.isEmpty() ? "?" : s;
    }

    /** Only stock Nautilus/JRNY processes are ever killed for holding the serial port. */
    static boolean isKillableHolder(String cmdline) {
        return cmdline != null && cmdline.startsWith("com.nautilus.");
    }

    static final class Holder {
        final int pid;
        final String cmdline;

        Holder(int pid, String cmdline) {
            this.pid = pid;
            this.cmdline = cmdline;
        }
    }

    /** "pid:cmdline,pid:cmdline" or "none". Commas/spaces in names are replaced so the DIAG line stays parseable. */
    static String formatHolders(List<Holder> holders) {
        if (holders == null || holders.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        for (Holder h : holders) {
            if (sb.length() > 0) sb.append(',');
            sb.append(h.pid).append(':').append(sanitizeToken(h.cmdline));
        }
        return sb.toString();
    }

    private static String sanitizeToken(String s) {
        if (s == null || s.isEmpty()) return "?";
        return s.replace(',', '_').replace(' ', '_').replace('=', '_');
    }

    // --- DIAG log line (cross-repo contract with the freewheel installer) ---

    /**
     * Builds the periodic DIAG line. The installer parses this from logcat, so the format is
     * a contract: keep keys, order and separators exactly as-is.
     */
    static String formatDiagLine(String dev, int baud, boolean open, long bytes, long frames,
                                 long crcErrors, long lastFrameAgeMs, int fwState,
                                 String fwName, String holders) {
        return "DIAG port=" + dev + "@" + baud
            + " open=" + (open ? 1 : 0)
            + " bytes=" + bytes
            + " frames=" + frames
            + " crc=" + crcErrors
            + " lastFrameAgeMs=" + lastFrameAgeMs
            + " fw=" + fwState
            + " fwName=" + sanitizeToken(fwName == null || fwState < 0 ? "Unknown" : fwName)
            + " holders=" + (holders == null || holders.isEmpty() ? "none" : holders);
    }

    /** Age of the last valid frame, or -1 if none has ever been received. */
    static long frameAgeMs(long lastValidFrameMs, long nowMs) {
        if (lastValidFrameMs <= 0) return -1;
        return Math.max(0, nowMs - lastValidFrameMs);
    }

    // --- Firmware-state deferred streaming ---

    static final int FW_STATE_SELECTION = 8;
    static final int FW_STATE_WORKOUT = 9;

    static boolean isStreamingState(int fwState) {
        return fwState == FW_STATE_SELECTION || fwState == FW_STATE_WORKOUT;
    }

    /**
     * Called at workout start. Only when the firmware state is KNOWN and is not one where
     * streaming works do we remember to re-send the (same) STREAMING_CONTROL enable later.
     * Unknown (-1) or SELECTION/WORKOUT: behave exactly as before (no pending).
     */
    static boolean shouldDeferStreaming(int fwState) {
        return fwState >= 0 && !isStreamingState(fwState);
    }

    /** Called on a heartbeat-reported state change. */
    static boolean shouldFireDeferredStreaming(boolean workoutPending, boolean workoutActive,
                                               int oldState, int newState) {
        return workoutPending && workoutActive && oldState != newState && isStreamingState(newState);
    }

    // --- Rate-limited "unexpected frame" logging ---

    /** Remembers which (key) we've already warned about so each distinct case is logged once. */
    static final class OnceLimiter {
        private static final int MAX_KEYS = 256;
        private final Set<String> seen = new HashSet<>();

        synchronized boolean firstTime(String key) {
            if (seen.contains(key)) return false;
            if (seen.size() >= MAX_KEYS) return false;
            seen.add(key);
            return true;
        }
    }
}
