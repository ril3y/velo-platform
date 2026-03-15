package io.freewheel.ucb;

/**
 * OTA firmware flash state machine for UCB hardware.
 *
 * Implements the 5-phase flash protocol:
 *   1. PERMISSION — Request OTA session, wait for ACCEPT
 *   2. ERASE — Send NVRAM PREPARE, wait for ERASE_COMPLETE
 *   3. WRITE — Send firmware blocks via NVRAM_WRITE_LOGICAL, per-block ACK
 *   4. VALIDATE — Send REQUEST_APPLY, wait for VALIDATION_PASS
 *   5. REBOOT — Send RESET_CONSOLE
 *
 * Ported from hardware/ucb-firmware/ucb_flash.py UcbFlasher.flash_firmware().
 */
public class OtaFlashSession {

    // OTA Session Control values (data[0] of OTA_SESSION_CONTROL messages)
    public static final int OTA_REQUEST_PERMISSION = 0x00;
    public static final int OTA_REQUEST_CANCEL = 0x01;
    public static final int OTA_REQUEST_APPLY = 0x02;
    public static final int OTA_RESPONSE_ACCEPT = 0x03;
    public static final int OTA_RESPONSE_REJECT = 0x04;
    public static final int OTA_RESPONSE_TIMEOUT = 0x05;
    public static final int OTA_RESPONSE_ERASE_COMPLETE = 0x06;
    public static final int OTA_RESPONSE_PACKET_WRITTEN = 0x07;
    public static final int OTA_RESPONSE_VALIDATION_PASS = 0x08;
    public static final int OTA_RESPONSE_VALIDATION_FAIL = 0x09;

    // NVRAM constants
    public static final int NVRAM_TYPE_PACKAGE = 0x00;
    public static final int NVRAM_CMD_PREPARE = 0x00;
    public static final int NVRAM_CMD_WRITE = 0x02;

    // RESET_CONSOLE message ID (0x09 in UcbMessageIds, but SET_RESISTANCE in the enum;
    // the OTA protocol uses 0x09 as RESET_CONSOLE per ucb_flash.py)
    public static final int MSG_RESET_CONSOLE = 0x09;

    public enum Phase {
        IDLE,
        PERMISSION,
        ERASE,
        WRITE,
        VALIDATE,
        REBOOT,
        COMPLETE,
        FAILED
    }

    public interface Listener {
        void onPhaseChanged(Phase phase);
        void onBlockProgress(int current, int total);
        void onError(String error);
        void onComplete(boolean success);
    }

    public interface FrameSender {
        /** Send a raw wire frame (already encoded with STX/ETX). Returns the sequence number used. */
        int sendFrame(int msgId, byte[] data);
    }

    private final OtaFirmwareFile firmware;
    private final FrameSender sender;
    private final Listener listener;

    private volatile Phase phase = Phase.IDLE;
    private volatile int currentBlock = 0;
    private volatile boolean cancelled = false;

    public OtaFlashSession(OtaFirmwareFile firmware, FrameSender sender, Listener listener) {
        this.firmware = firmware;
        this.sender = sender;
        this.listener = listener;
    }

    public Phase getPhase() { return phase; }
    public int getCurrentBlock() { return currentBlock; }
    public boolean isCancelled() { return cancelled; }

    /**
     * Start the flash sequence. Call this from a background thread.
     * The session will call sender.sendFrame() for each outgoing frame and
     * block waiting for responses via onResponse().
     */
    public void start() {
        if (phase != Phase.IDLE) {
            listener.onError("Session already started");
            return;
        }
        setPhase(Phase.PERMISSION);
        // Send OTA_SESSION_CONTROL REQUEST_PERMISSION
        sender.sendFrame(UcbMessageIds.OTA_SESSION_CONTROL,
            new byte[]{OTA_REQUEST_PERMISSION});
        // Now waiting for onOtaResponse() to be called with ACCEPT
    }

    /**
     * Cancel the flash session.
     */
    public void cancel() {
        cancelled = true;
        sender.sendFrame(UcbMessageIds.OTA_SESSION_CONTROL,
            new byte[]{OTA_REQUEST_CANCEL});
        setPhase(Phase.FAILED);
        listener.onError("Cancelled by user");
        listener.onComplete(false);
    }

    /**
     * Called by BridgeService when an OTA_SESSION_CONTROL response is received.
     * @param responseCode the first data byte of the OTA_SESSION_CONTROL message
     */
    public void onOtaResponse(int responseCode) {
        if (cancelled) return;

        switch (phase) {
            case PERMISSION:
                handlePermissionResponse(responseCode);
                break;
            case ERASE:
                handleEraseResponse(responseCode);
                break;
            case WRITE:
                handleWriteResponse(responseCode);
                break;
            case VALIDATE:
                handleValidateResponse(responseCode);
                break;
            default:
                break;
        }
    }

    private void handlePermissionResponse(int code) {
        if (code == OTA_RESPONSE_ACCEPT) {
            // Permission granted — start erase
            setPhase(Phase.ERASE);
            sender.sendFrame(UcbMessageIds.NVRAM_WRITE_LOGICAL,
                new byte[]{NVRAM_TYPE_PACKAGE, NVRAM_CMD_PREPARE});
        } else if (code == OTA_RESPONSE_REJECT) {
            fail("OTA permission rejected by UCB");
        } else if (code == OTA_RESPONSE_TIMEOUT) {
            fail("OTA permission timed out");
        }
    }

    private void handleEraseResponse(int code) {
        if (code == OTA_RESPONSE_ERASE_COMPLETE) {
            // Erase done — start writing blocks
            currentBlock = 0;
            setPhase(Phase.WRITE);
            sendNextBlock();
        } else if (code == OTA_RESPONSE_REJECT || code == OTA_RESPONSE_TIMEOUT) {
            fail("Erase failed: " + otaResponseName(code));
        }
    }

    private void handleWriteResponse(int code) {
        if (code == OTA_RESPONSE_PACKET_WRITTEN) {
            currentBlock++;
            listener.onBlockProgress(currentBlock, firmware.getBlockCount());
            if (currentBlock >= firmware.getBlockCount()) {
                // All blocks written — validate
                setPhase(Phase.VALIDATE);
                sender.sendFrame(UcbMessageIds.OTA_SESSION_CONTROL,
                    new byte[]{OTA_REQUEST_APPLY});
            } else {
                sendNextBlock();
            }
        } else if (code == OTA_RESPONSE_REJECT || code == OTA_RESPONSE_TIMEOUT
                || code == OTA_RESPONSE_VALIDATION_FAIL) {
            fail("Write failed at block " + currentBlock + ": " + otaResponseName(code));
        }
    }

    private void handleValidateResponse(int code) {
        if (code == OTA_RESPONSE_VALIDATION_PASS) {
            // Validation passed — reboot
            setPhase(Phase.REBOOT);
            sender.sendFrame(MSG_RESET_CONSOLE,
                new byte[]{0x00, 0x00, 0x00, 0x00});
            setPhase(Phase.COMPLETE);
            listener.onComplete(true);
        } else if (code == OTA_RESPONSE_VALIDATION_FAIL) {
            fail("Firmware validation FAILED — UCB may need recovery");
        }
    }

    private void sendNextBlock() {
        if (cancelled) return;
        byte[] blockData = firmware.getBlock(currentBlock);
        byte[] payload = new byte[2 + blockData.length];
        payload[0] = NVRAM_TYPE_PACKAGE;
        payload[1] = NVRAM_CMD_WRITE;
        System.arraycopy(blockData, 0, payload, 2, blockData.length);
        sender.sendFrame(UcbMessageIds.NVRAM_WRITE_LOGICAL, payload);
        listener.onBlockProgress(currentBlock, firmware.getBlockCount());
    }

    private void setPhase(Phase newPhase) {
        phase = newPhase;
        listener.onPhaseChanged(newPhase);
    }

    private void fail(String error) {
        setPhase(Phase.FAILED);
        listener.onError(error);
        listener.onComplete(false);
    }

    public static String otaResponseName(int code) {
        switch (code) {
            case OTA_REQUEST_PERMISSION: return "REQUEST_PERMISSION";
            case OTA_REQUEST_CANCEL: return "REQUEST_CANCEL";
            case OTA_REQUEST_APPLY: return "REQUEST_APPLY";
            case OTA_RESPONSE_ACCEPT: return "RESPONSE_ACCEPT";
            case OTA_RESPONSE_REJECT: return "RESPONSE_REJECT";
            case OTA_RESPONSE_TIMEOUT: return "RESPONSE_TIMEOUT";
            case OTA_RESPONSE_ERASE_COMPLETE: return "RESPONSE_ERASE_COMPLETE";
            case OTA_RESPONSE_PACKET_WRITTEN: return "RESPONSE_PACKET_WRITTEN";
            case OTA_RESPONSE_VALIDATION_PASS: return "RESPONSE_VALIDATION_PASS";
            case OTA_RESPONSE_VALIDATION_FAIL: return "RESPONSE_VALIDATION_FAIL";
            default: return "UNKNOWN_" + code;
        }
    }

    public static String phaseName(Phase phase) {
        switch (phase) {
            case IDLE: return "Idle";
            case PERMISSION: return "Requesting permission";
            case ERASE: return "Erasing flash";
            case WRITE: return "Writing firmware";
            case VALIDATE: return "Validating";
            case REBOOT: return "Rebooting UCB";
            case COMPLETE: return "Complete";
            case FAILED: return "Failed";
            default: return phase.name();
        }
    }
}
