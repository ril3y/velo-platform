package io.freewheel.ucb;

/**
 * Manufacturing test / calibration commands for UCB hardware.
 *
 * MFG_TEST (0x1D) protocol from hardware/ucb-firmware/ANALYSIS.md:
 * - Enter MFG test state → puts UCB into manufacturing mode (state 6)
 * - Resistance calibration: 3-point (ZERO, MAX, CENTER)
 * - Exit MFG test state → returns to normal operation
 *
 * All MFG commands use Purpose.POST (0x02).
 */
public class MfgTestCommand {

    // MFG Test intents (from ANALYSIS.md MFG_TEST protocol)
    public static final int INTENT_START = 0x01;
    public static final int INTENT_CANCEL = 0x02;
    public static final int INTENT_PASS = 0x03;
    public static final int INTENT_FAIL = 0x04;
    public static final int INTENT_STATUS = 0x05;
    public static final int INTENT_USER_CONFIRM = 0x06;

    // Calibration phases (resistance 3-point calibration)
    public static final int CAL_PHASE_ZERO = 0;
    public static final int CAL_PHASE_MAX = 1;
    public static final int CAL_PHASE_CENTER = 2;

    // MFG test types
    public static final int TEST_RESISTANCE_CAL = 0x08;  // Sensor Test → resistance calibration
    public static final int TEST_ENTER_MFG = 0x20;       // ENTER_MFG_TEST_STATE (msg 32 in analysis)
    public static final int TEST_EXIT_MFG = 0x00;        // Exit MFG mode

    /**
     * Build payload to enter MFG test state.
     * UCB transitions to firmware state MFG (6).
     */
    public static byte[] enterMfgTestState() {
        return new byte[]{(byte) TEST_ENTER_MFG, (byte) INTENT_START};
    }

    /**
     * Build payload to start resistance calibration at a given phase.
     * @param phase CAL_PHASE_ZERO, CAL_PHASE_MAX, or CAL_PHASE_CENTER
     */
    public static byte[] calibrateResistance(int phase) {
        return new byte[]{(byte) TEST_RESISTANCE_CAL, (byte) INTENT_START, (byte) phase};
    }

    /**
     * Build payload to confirm a calibration step (user has positioned the knob).
     */
    public static byte[] confirmCalibrationStep() {
        return new byte[]{(byte) TEST_RESISTANCE_CAL, (byte) INTENT_USER_CONFIRM};
    }

    /**
     * Build payload to cancel calibration.
     */
    public static byte[] cancelCalibration() {
        return new byte[]{(byte) TEST_RESISTANCE_CAL, (byte) INTENT_CANCEL};
    }

    /**
     * Build payload to exit MFG test state.
     * UCB returns to normal operation (SELECTION state).
     */
    public static byte[] exitMfgTestState() {
        return new byte[]{(byte) TEST_EXIT_MFG, (byte) INTENT_CANCEL};
    }

    /**
     * Build payload to query calibration status.
     */
    public static byte[] queryStatus() {
        return new byte[]{(byte) TEST_RESISTANCE_CAL, (byte) INTENT_STATUS};
    }

    public static String phaseName(int phase) {
        switch (phase) {
            case CAL_PHASE_ZERO: return "Zero (minimum)";
            case CAL_PHASE_MAX: return "Maximum";
            case CAL_PHASE_CENTER: return "Center detent";
            default: return "Unknown phase " + phase;
        }
    }

    public static String phaseInstruction(int phase) {
        switch (phase) {
            case CAL_PHASE_ZERO: return "Turn resistance knob to minimum (zero). Press Confirm.";
            case CAL_PHASE_MAX: return "Turn resistance knob to maximum. Press Confirm.";
            case CAL_PHASE_CENTER: return "Turn resistance knob to center detent. Press Confirm.";
            default: return "Unknown calibration phase.";
        }
    }
}
