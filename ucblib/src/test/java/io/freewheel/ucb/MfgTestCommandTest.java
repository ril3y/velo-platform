package io.freewheel.ucb;

import org.junit.Test;
import static org.junit.Assert.*;

public class MfgTestCommandTest {

    @Test
    public void enterMfgTestState_correctBytes() {
        byte[] payload = MfgTestCommand.enterMfgTestState();
        assertEquals(2, payload.length);
        assertEquals(MfgTestCommand.TEST_ENTER_MFG, payload[0] & 0xFF);
        assertEquals(MfgTestCommand.INTENT_START, payload[1] & 0xFF);
    }

    @Test
    public void exitMfgTestState_correctBytes() {
        byte[] payload = MfgTestCommand.exitMfgTestState();
        assertEquals(2, payload.length);
        assertEquals(MfgTestCommand.TEST_EXIT_MFG, payload[0] & 0xFF);
        assertEquals(MfgTestCommand.INTENT_CANCEL, payload[1] & 0xFF);
    }

    @Test
    public void calibrateResistance_zero_correctBytes() {
        byte[] payload = MfgTestCommand.calibrateResistance(MfgTestCommand.CAL_PHASE_ZERO);
        assertEquals(3, payload.length);
        assertEquals(MfgTestCommand.TEST_RESISTANCE_CAL, payload[0] & 0xFF);
        assertEquals(MfgTestCommand.INTENT_START, payload[1] & 0xFF);
        assertEquals(MfgTestCommand.CAL_PHASE_ZERO, payload[2] & 0xFF);
    }

    @Test
    public void calibrateResistance_max_correctBytes() {
        byte[] payload = MfgTestCommand.calibrateResistance(MfgTestCommand.CAL_PHASE_MAX);
        assertEquals(3, payload.length);
        assertEquals(MfgTestCommand.CAL_PHASE_MAX, payload[2] & 0xFF);
    }

    @Test
    public void calibrateResistance_center_correctBytes() {
        byte[] payload = MfgTestCommand.calibrateResistance(MfgTestCommand.CAL_PHASE_CENTER);
        assertEquals(3, payload.length);
        assertEquals(MfgTestCommand.CAL_PHASE_CENTER, payload[2] & 0xFF);
    }

    @Test
    public void confirmCalibrationStep_correctBytes() {
        byte[] payload = MfgTestCommand.confirmCalibrationStep();
        assertEquals(2, payload.length);
        assertEquals(MfgTestCommand.TEST_RESISTANCE_CAL, payload[0] & 0xFF);
        assertEquals(MfgTestCommand.INTENT_USER_CONFIRM, payload[1] & 0xFF);
    }

    @Test
    public void cancelCalibration_correctBytes() {
        byte[] payload = MfgTestCommand.cancelCalibration();
        assertEquals(2, payload.length);
        assertEquals(MfgTestCommand.TEST_RESISTANCE_CAL, payload[0] & 0xFF);
        assertEquals(MfgTestCommand.INTENT_CANCEL, payload[1] & 0xFF);
    }

    @Test
    public void queryStatus_correctBytes() {
        byte[] payload = MfgTestCommand.queryStatus();
        assertEquals(2, payload.length);
        assertEquals(MfgTestCommand.TEST_RESISTANCE_CAL, payload[0] & 0xFF);
        assertEquals(MfgTestCommand.INTENT_STATUS, payload[1] & 0xFF);
    }

    @Test
    public void phaseInstruction_allPhasesHaveText() {
        assertNotNull(MfgTestCommand.phaseInstruction(MfgTestCommand.CAL_PHASE_ZERO));
        assertNotNull(MfgTestCommand.phaseInstruction(MfgTestCommand.CAL_PHASE_MAX));
        assertNotNull(MfgTestCommand.phaseInstruction(MfgTestCommand.CAL_PHASE_CENTER));
        assertTrue(MfgTestCommand.phaseInstruction(MfgTestCommand.CAL_PHASE_ZERO).contains("minimum"));
        assertTrue(MfgTestCommand.phaseInstruction(MfgTestCommand.CAL_PHASE_MAX).contains("maximum"));
        assertTrue(MfgTestCommand.phaseInstruction(MfgTestCommand.CAL_PHASE_CENTER).contains("center"));
    }
}
