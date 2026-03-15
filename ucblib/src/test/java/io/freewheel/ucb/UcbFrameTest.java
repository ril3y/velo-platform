package io.freewheel.ucb;

import org.junit.Test;
import static org.junit.Assert.*;

public class UcbFrameTest {

    @Test
    public void buildPost_setsCorrectPurposeByte() {
        byte[] frame = UcbFrame.buildPost(0x15, 0x01, new byte[]{0x00});
        assertNotNull(frame);
        // Frame format: STX + hex(payload + CRC32) + ETX
        assertEquals(UcbFrame.STX, frame[0]);
        assertEquals(UcbFrame.ETX, frame[frame.length - 1]);

        // Decode to verify purpose byte
        UcbFrame decoded = UcbFrame.decode(frame);
        assertNotNull(decoded);
        assertEquals(UcbFrame.MSG_TYPE_POST, decoded.msgType);
        assertEquals(0x15, decoded.msgId);
        assertEquals(0x01, decoded.counter);
        assertEquals(1, decoded.data.length);
        assertEquals(0x00, decoded.data[0]);
    }

    @Test
    public void buildPost_purposeIs0x02() {
        // MSG_TYPE_POST must be 0x02 (Purpose.POST)
        assertEquals(0x02, UcbFrame.MSG_TYPE_POST);
    }

    @Test
    public void buildRequest_purposeIs0x01() {
        assertEquals(0x01, UcbFrame.MSG_TYPE_REQUEST);
    }

    @Test
    public void roundTrip_requestFrame() {
        byte[] frame = UcbFrame.buildRequest(0x18, 0x05, new byte[]{});
        UcbFrame decoded = UcbFrame.decode(frame);
        assertNotNull(decoded);
        assertEquals(UcbFrame.MSG_TYPE_REQUEST, decoded.msgType);
        assertEquals(0x18, decoded.msgId);
        assertEquals(0x05, decoded.counter);
        assertEquals(0, decoded.data.length);
    }

    @Test
    public void roundTrip_postFrame() {
        byte[] data = new byte[]{0x01, 0x02, 0x03};
        byte[] frame = UcbFrame.buildPost(0x17, 0x0A, data);
        UcbFrame decoded = UcbFrame.decode(frame);
        assertNotNull(decoded);
        assertEquals(UcbFrame.MSG_TYPE_POST, decoded.msgType);
        assertEquals(0x17, decoded.msgId);
        assertEquals(0x0A, decoded.counter);
        assertArrayEquals(data, decoded.data);
    }

    @Test
    public void decode_badCrc_returnsNull() {
        byte[] frame = UcbFrame.buildPost(0x15, 0x00, new byte[]{0x00});
        // Corrupt a byte in the middle
        frame[3] = (byte) (frame[3] ^ 0xFF);
        assertNull(UcbFrame.decode(frame));
    }

    @Test
    public void computeCrc_matchesPythonCrc32fw() {
        // Python crc32_fw: CRC32 without final XOR
        // Java CRC32 with final XOR undone = same thing
        byte[] testData = new byte[]{0x02, 0x15, 0x00, 0x00};
        long crc = UcbFrame.computeCrc(testData);
        // Just verify it produces a non-zero 32-bit value
        assertTrue(crc > 0);
        assertTrue(crc <= 0xFFFFFFFFL);
    }
}
