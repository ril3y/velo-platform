package io.freewheel.ucb;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

public class OtaFlashSessionTest {

    private OtaFirmwareFile firmware;
    private List<int[]> sentFrames; // [msgId, dataLength]
    private List<OtaFlashSession.Phase> phaseChanges;
    private List<int[]> blockProgress; // [current, total]
    private String lastError;
    private Boolean completedSuccess;

    @Before
    public void setUp() throws Exception {
        sentFrames = new ArrayList<>();
        phaseChanges = new ArrayList<>();
        blockProgress = new ArrayList<>();
        lastError = null;
        completedSuccess = null;

        // Build test firmware with 3 blocks (5000 bytes image)
        byte[] binary = buildTestFirmware(87, 5000);
        firmware = OtaFirmwareFile.parseFromBinary(binary);
    }

    private OtaFlashSession createSession() {
        OtaFlashSession.FrameSender sender = (msgId, data) -> {
            sentFrames.add(new int[]{msgId, data.length});
            return sentFrames.size();
        };

        OtaFlashSession.Listener listener = new OtaFlashSession.Listener() {
            @Override
            public void onPhaseChanged(OtaFlashSession.Phase phase) {
                phaseChanges.add(phase);
            }

            @Override
            public void onBlockProgress(int current, int total) {
                blockProgress.add(new int[]{current, total});
            }

            @Override
            public void onError(String error) {
                lastError = error;
            }

            @Override
            public void onComplete(boolean success) {
                completedSuccess = success;
            }
        };

        return new OtaFlashSession(firmware, sender, listener);
    }

    @Test
    public void start_sendsPermissionRequest() {
        OtaFlashSession session = createSession();
        session.start();

        assertEquals(OtaFlashSession.Phase.PERMISSION, session.getPhase());
        assertEquals(1, sentFrames.size());
        assertEquals(UcbMessageIds.OTA_SESSION_CONTROL, sentFrames.get(0)[0]);
    }

    @Test
    public void permissionAccepted_sendsErase() {
        OtaFlashSession session = createSession();
        session.start();
        sentFrames.clear();

        session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_ACCEPT);

        assertEquals(OtaFlashSession.Phase.ERASE, session.getPhase());
        assertEquals(1, sentFrames.size());
        assertEquals(UcbMessageIds.NVRAM_WRITE_LOGICAL, sentFrames.get(0)[0]);
    }

    @Test
    public void permissionRejected_fails() {
        OtaFlashSession session = createSession();
        session.start();

        session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_REJECT);

        assertEquals(OtaFlashSession.Phase.FAILED, session.getPhase());
        assertNotNull(lastError);
        assertTrue(lastError.contains("rejected"));
        assertEquals(Boolean.FALSE, completedSuccess);
    }

    @Test
    public void eraseComplete_sendsFirstBlock() {
        OtaFlashSession session = createSession();
        session.start();
        session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_ACCEPT);
        sentFrames.clear();

        session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_ERASE_COMPLETE);

        assertEquals(OtaFlashSession.Phase.WRITE, session.getPhase());
        assertEquals(1, sentFrames.size());
        assertEquals(UcbMessageIds.NVRAM_WRITE_LOGICAL, sentFrames.get(0)[0]);
        // Data: TYPE_PACKAGE(1) + CMD_WRITE(1) + block data(2048) = 2050
        assertEquals(2050, sentFrames.get(0)[1]);
    }

    @Test
    public void fullFlashSequence_3blocks() {
        OtaFlashSession session = createSession();
        assertEquals(3, firmware.getBlockCount());

        session.start();
        session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_ACCEPT);
        session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_ERASE_COMPLETE);

        // Write block 0
        session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_PACKET_WRITTEN);
        assertEquals(1, session.getCurrentBlock());

        // Write block 1
        session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_PACKET_WRITTEN);
        assertEquals(2, session.getCurrentBlock());

        // Write block 2 (last) → triggers validate
        session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_PACKET_WRITTEN);
        assertEquals(OtaFlashSession.Phase.VALIDATE, session.getPhase());

        // Validation pass → reboot
        session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_VALIDATION_PASS);
        assertEquals(OtaFlashSession.Phase.COMPLETE, session.getPhase());
        assertEquals(Boolean.TRUE, completedSuccess);
    }

    @Test
    public void validationFail_fails() {
        OtaFlashSession session = createSession();
        session.start();
        session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_ACCEPT);
        session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_ERASE_COMPLETE);
        // Write all blocks
        for (int i = 0; i < firmware.getBlockCount(); i++) {
            session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_PACKET_WRITTEN);
        }
        assertEquals(OtaFlashSession.Phase.VALIDATE, session.getPhase());

        session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_VALIDATION_FAIL);

        assertEquals(OtaFlashSession.Phase.FAILED, session.getPhase());
        assertNotNull(lastError);
        assertTrue(lastError.contains("FAILED"));
        assertEquals(Boolean.FALSE, completedSuccess);
    }

    @Test
    public void cancel_sendsCancelAndFails() {
        OtaFlashSession session = createSession();
        session.start();

        session.cancel();

        assertTrue(session.isCancelled());
        assertEquals(OtaFlashSession.Phase.FAILED, session.getPhase());
        assertEquals(Boolean.FALSE, completedSuccess);
    }

    @Test
    public void phaseChanges_trackedCorrectly() {
        OtaFlashSession session = createSession();
        session.start();
        session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_ACCEPT);
        session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_ERASE_COMPLETE);
        for (int i = 0; i < firmware.getBlockCount(); i++) {
            session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_PACKET_WRITTEN);
        }
        session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_VALIDATION_PASS);

        assertEquals(OtaFlashSession.Phase.PERMISSION, phaseChanges.get(0));
        assertEquals(OtaFlashSession.Phase.ERASE, phaseChanges.get(1));
        assertEquals(OtaFlashSession.Phase.WRITE, phaseChanges.get(2));
        assertEquals(OtaFlashSession.Phase.VALIDATE, phaseChanges.get(3));
        assertEquals(OtaFlashSession.Phase.REBOOT, phaseChanges.get(4));
        assertEquals(OtaFlashSession.Phase.COMPLETE, phaseChanges.get(5));
    }

    @Test
    public void rebootPhase_sendsResetConsole() {
        OtaFlashSession session = createSession();
        session.start();
        session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_ACCEPT);
        session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_ERASE_COMPLETE);
        for (int i = 0; i < firmware.getBlockCount(); i++) {
            session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_PACKET_WRITTEN);
        }
        sentFrames.clear();

        session.onOtaResponse(OtaFlashSession.OTA_RESPONSE_VALIDATION_PASS);

        // Should have sent RESET_CONSOLE (0x09)
        boolean foundReset = false;
        for (int[] frame : sentFrames) {
            if (frame[0] == OtaFlashSession.MSG_RESET_CONSOLE) {
                foundReset = true;
                break;
            }
        }
        assertTrue("Should send RESET_CONSOLE", foundReset);
    }

    // -- Helper --

    private byte[] buildTestFirmware(int machineClass, int imageSize) {
        byte[] imageData = new byte[imageSize];
        for (int i = 0; i < imageSize; i++) {
            imageData[i] = (byte) (i & 0xFF);
        }
        int imageCrc = OtaFirmwareFile.crc32NoFinalXor(imageData, 0, imageData.length);

        byte[] full = new byte[22 + imageSize];
        java.nio.ByteBuffer fb = java.nio.ByteBuffer.wrap(full).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        fb.putInt(4, 18);
        fb.putShort(8, (short) machineClass);
        fb.putShort(10, (short) 2048);
        fb.putShort(12, (short) 0);
        fb.putInt(14, imageSize);
        fb.putInt(18, imageCrc);
        System.arraycopy(imageData, 0, full, 22, imageSize);
        int headerCrc = OtaFirmwareFile.crc32NoFinalXor(full, 4, 14);
        fb.putInt(0, headerCrc);
        return full;
    }
}
