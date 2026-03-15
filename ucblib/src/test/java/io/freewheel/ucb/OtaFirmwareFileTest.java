package io.freewheel.ucb;

import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;

public class OtaFirmwareFileTest {

    /**
     * Build a synthetic firmware file with valid CRCs for testing.
     */
    private byte[] buildTestFirmware(int machineClass, int imageSize) {
        // Create image data (just incrementing bytes)
        byte[] imageData = new byte[imageSize];
        for (int i = 0; i < imageSize; i++) {
            imageData[i] = (byte) (i & 0xFF);
        }

        // Compute image CRC (no final XOR)
        int imageCrc = crc32NoFinalXor(imageData, 0, imageData.length);

        // Build header bytes 4-17 (14 bytes)
        byte[] headerBody = new byte[14];
        ByteBuffer hdr = ByteBuffer.wrap(headerBody).order(ByteOrder.LITTLE_ENDIAN);
        hdr.putInt(0, 18);             // headerLength = 18 (bytes 4-17 = 14 bytes, but field is total: 4+14=18)
        hdr.putShort(4, (short) machineClass);
        hdr.putShort(6, (short) 0);    // sectorSize
        hdr.putShort(8, (short) 0);    // imageSector
        hdr.putInt(10, imageSize);     // imageLength

        // Wait — header structure: headerCrc(4) + headerLen(4) + machineClass(2) + sectorSize(2) + imageSector(2) + imageLength(4) + imageCrc(4) = 22
        // headerLen covers bytes 4 to headerLen, so if headerLen=18, CRC covers bytes 4 to 4+18-4=18 (14 bytes: 4..17)
        // Actually looking at ucb_flash.py: calc_header_crc = crc32_fw(binary[4:header_len])
        // So header_len is the END index, and CRC covers bytes 4 to header_len (exclusive)

        // Let's redo properly with ByteBuffer for the full 22-byte header
        byte[] full = new byte[22 + imageSize];
        ByteBuffer fb = ByteBuffer.wrap(full).order(ByteOrder.LITTLE_ENDIAN);
        // offset 0: headerCrc (fill later)
        fb.putInt(4, 18);               // headerLength — ucb_flash.py uses binary[4:header_len]
        fb.putShort(8, (short) machineClass);
        fb.putShort(10, (short) 2048);  // sectorSize
        fb.putShort(12, (short) 0);     // imageSector
        fb.putInt(14, imageSize);       // imageLength
        fb.putInt(18, imageCrc);        // imageCrc

        // Copy image data
        System.arraycopy(imageData, 0, full, 22, imageSize);

        // Compute header CRC: covers bytes 4 to headerLength (=18), so bytes 4..17 (14 bytes)
        int headerCrc = crc32NoFinalXor(full, 4, 14);
        fb.putInt(0, headerCrc);

        return full;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    private int crc32NoFinalXor(byte[] data, int offset, int length) {
        CRC32 crc = new CRC32();
        crc.update(data, offset, length);
        return (int) (crc.getValue() ^ 0xFFFFFFFFL);
    }

    @Test
    public void parse_validFirmware_succeeds() throws OtaFirmwareFile.FirmwareParseException {
        byte[] binary = buildTestFirmware(87, 4096);
        String hex = bytesToHex(binary);
        OtaFirmwareFile fw = OtaFirmwareFile.parse(hex);

        assertEquals(87, fw.getMachineClass());
        assertEquals(4096, fw.getImageLength());
        assertEquals(2, fw.getBlockCount()); // 4096 / 2048 = 2 blocks
    }

    @Test
    public void parse_blockBoundaries_correct() throws OtaFirmwareFile.FirmwareParseException {
        // 5000 bytes = 2 full blocks (2048) + 1 partial (904)
        byte[] binary = buildTestFirmware(87, 5000);
        OtaFirmwareFile fw = OtaFirmwareFile.parseFromBinary(binary);

        assertEquals(3, fw.getBlockCount());
        assertEquals(2048, fw.getBlock(0).length);
        assertEquals(2048, fw.getBlock(1).length);
        assertEquals(904, fw.getBlock(2).length);
    }

    @Test
    public void parse_singleBlock_works() throws OtaFirmwareFile.FirmwareParseException {
        byte[] binary = buildTestFirmware(87, 1000);
        OtaFirmwareFile fw = OtaFirmwareFile.parseFromBinary(binary);

        assertEquals(1, fw.getBlockCount());
        assertEquals(1000, fw.getBlock(0).length);
    }

    @Test
    public void parse_exactBlockSize_noPartial() throws OtaFirmwareFile.FirmwareParseException {
        byte[] binary = buildTestFirmware(87, 2048);
        OtaFirmwareFile fw = OtaFirmwareFile.parseFromBinary(binary);

        assertEquals(1, fw.getBlockCount());
        assertEquals(2048, fw.getBlock(0).length);
    }

    @Test(expected = OtaFirmwareFile.FirmwareParseException.class)
    public void parse_corruptHeaderCrc_throws() throws OtaFirmwareFile.FirmwareParseException {
        byte[] binary = buildTestFirmware(87, 4096);
        binary[0] ^= 0xFF; // corrupt header CRC
        OtaFirmwareFile.parseFromBinary(binary);
    }

    @Test(expected = OtaFirmwareFile.FirmwareParseException.class)
    public void parse_corruptImageCrc_throws() throws OtaFirmwareFile.FirmwareParseException {
        byte[] binary = buildTestFirmware(87, 4096);
        binary[binary.length - 1] ^= 0xFF; // corrupt image data → CRC mismatch
        OtaFirmwareFile.parseFromBinary(binary);
    }

    @Test(expected = OtaFirmwareFile.FirmwareParseException.class)
    public void parse_tooShort_throws() throws OtaFirmwareFile.FirmwareParseException {
        OtaFirmwareFile.parseFromBinary(new byte[10]);
    }

    @Test
    public void parse_machineClass_preserved() throws OtaFirmwareFile.FirmwareParseException {
        byte[] binary = buildTestFirmware(42, 2048);
        OtaFirmwareFile fw = OtaFirmwareFile.parseFromBinary(binary);
        assertEquals(42, fw.getMachineClass());
    }

    @Test
    public void getImageCrc32_matchesComputed() throws OtaFirmwareFile.FirmwareParseException {
        byte[] binary = buildTestFirmware(87, 4096);
        OtaFirmwareFile fw = OtaFirmwareFile.parseFromBinary(binary);
        // The CRC should match what we compute for the image data
        int expectedCrc = crc32NoFinalXor(binary, 22, 4096);
        assertEquals(expectedCrc, fw.getImageCrc32());
    }

    @Test
    public void parse_hexWithWhitespace_works() throws OtaFirmwareFile.FirmwareParseException {
        byte[] binary = buildTestFirmware(87, 2048);
        String hex = bytesToHex(binary);
        // Add newlines every 64 chars (simulates real firmware file format)
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 64) {
            formatted.append(hex, i, Math.min(i + 64, hex.length()));
            formatted.append('\n');
        }
        OtaFirmwareFile fw = OtaFirmwareFile.parse(formatted.toString());
        assertEquals(87, fw.getMachineClass());
    }
}
