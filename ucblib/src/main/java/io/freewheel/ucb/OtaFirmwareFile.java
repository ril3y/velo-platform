package io.freewheel.ucb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Parser for UCB hex-encoded firmware files (.txt format).
 *
 * File format: hex-encoded text, each line contains uppercase hex chars.
 * Binary header (22 bytes LE): headerCrc(4) + headerLen(4) + machineClass(2)
 *   + sectorSize(2) + imageSector(2) + imageLength(4) + imageCrc(4)
 *
 * Ported from hardware/ucb-firmware/ucb_flash.py parse_firmware_file().
 */
public class OtaFirmwareFile {
    public static final int HEADER_SIZE = 22;
    public static final int BLOCK_SIZE = 2048;

    private final int headerCrc;
    private final int headerLength;
    private final int machineClass;
    private final int sectorSize;
    private final int imageSector;
    private final int imageLength;
    private final int imageCrc;
    private final List<byte[]> blocks;

    private OtaFirmwareFile(int headerCrc, int headerLength, int machineClass,
                            int sectorSize, int imageSector, int imageLength,
                            int imageCrc, List<byte[]> blocks) {
        this.headerCrc = headerCrc;
        this.headerLength = headerLength;
        this.machineClass = machineClass;
        this.sectorSize = sectorSize;
        this.imageSector = imageSector;
        this.imageLength = imageLength;
        this.imageCrc = imageCrc;
        this.blocks = blocks;
    }

    /**
     * Parse a hex-encoded firmware file from its text content.
     * @param textContent the full text content of the .txt firmware file
     * @return parsed firmware file
     * @throws FirmwareParseException on validation failure
     */
    public static OtaFirmwareFile parse(String textContent) throws FirmwareParseException {
        // Extract hex characters from all lines
        StringBuilder hexChars = new StringBuilder();
        for (int i = 0; i < textContent.length(); i++) {
            char c = textContent.charAt(i);
            if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f')) {
                hexChars.append(c);
            }
        }

        String hex = hexChars.toString();
        if (hex.length() % 2 != 0) {
            throw new FirmwareParseException("Odd number of hex characters");
        }

        byte[] binary = hexStringToBytes(hex);
        if (binary.length < HEADER_SIZE) {
            throw new FirmwareParseException("File too short for header: " + binary.length + " bytes");
        }

        // Parse header — all fields little-endian (matching ucb_flash.py)
        ByteBuffer hdr = ByteBuffer.wrap(binary, 0, HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        int headerCrc = hdr.getInt(0);
        int headerLength = hdr.getInt(4);
        int machineClass = hdr.getShort(8) & 0xFFFF;
        int sectorSize = hdr.getShort(10) & 0xFFFF;
        int imageSector = hdr.getShort(12) & 0xFFFF;
        int imageLength = hdr.getInt(14);
        int imageCrc = hdr.getInt(18);

        // Validate header CRC (covers bytes 4 through headerLength)
        int calcHeaderCrc = crc32NoFinalXor(binary, 4, headerLength - 4);
        if (calcHeaderCrc != headerCrc) {
            throw new FirmwareParseException(String.format(
                "Header CRC mismatch: calc=0x%08X file=0x%08X", calcHeaderCrc, headerCrc));
        }

        // Validate image data
        if (binary.length < HEADER_SIZE + imageLength) {
            throw new FirmwareParseException(String.format(
                "Image truncated: expected=%d got=%d", imageLength, binary.length - HEADER_SIZE));
        }

        int calcImageCrc = crc32NoFinalXor(binary, HEADER_SIZE, imageLength);
        if (calcImageCrc != imageCrc) {
            throw new FirmwareParseException(String.format(
                "Image CRC mismatch: calc=0x%08X file=0x%08X", calcImageCrc, imageCrc));
        }

        // Split image into 2048-byte blocks
        List<byte[]> blocks = new ArrayList<>();
        for (int offset = 0; offset < imageLength; offset += BLOCK_SIZE) {
            int chunkLen = Math.min(BLOCK_SIZE, imageLength - offset);
            byte[] chunk = new byte[chunkLen];
            System.arraycopy(binary, HEADER_SIZE + offset, chunk, 0, chunkLen);
            blocks.add(chunk);
        }

        return new OtaFirmwareFile(headerCrc, headerLength, machineClass,
            sectorSize, imageSector, imageLength, imageCrc, blocks);
    }

    /**
     * Parse from raw binary bytes (already hex-decoded).
     */
    public static OtaFirmwareFile parseFromBinary(byte[] binary) throws FirmwareParseException {
        if (binary.length < HEADER_SIZE) {
            throw new FirmwareParseException("File too short for header: " + binary.length + " bytes");
        }

        ByteBuffer hdr = ByteBuffer.wrap(binary, 0, HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        int headerCrc = hdr.getInt(0);
        int headerLength = hdr.getInt(4);
        int machineClass = hdr.getShort(8) & 0xFFFF;
        int sectorSize = hdr.getShort(10) & 0xFFFF;
        int imageSector = hdr.getShort(12) & 0xFFFF;
        int imageLength = hdr.getInt(14);
        int imageCrc = hdr.getInt(18);

        int calcHeaderCrc = crc32NoFinalXor(binary, 4, headerLength - 4);
        if (calcHeaderCrc != headerCrc) {
            throw new FirmwareParseException(String.format(
                "Header CRC mismatch: calc=0x%08X file=0x%08X", calcHeaderCrc, headerCrc));
        }

        if (binary.length < HEADER_SIZE + imageLength) {
            throw new FirmwareParseException(String.format(
                "Image truncated: expected=%d got=%d", imageLength, binary.length - HEADER_SIZE));
        }

        int calcImageCrc = crc32NoFinalXor(binary, HEADER_SIZE, imageLength);
        if (calcImageCrc != imageCrc) {
            throw new FirmwareParseException(String.format(
                "Image CRC mismatch: calc=0x%08X file=0x%08X", calcImageCrc, imageCrc));
        }

        List<byte[]> blocks = new ArrayList<>();
        for (int offset = 0; offset < imageLength; offset += BLOCK_SIZE) {
            int chunkLen = Math.min(BLOCK_SIZE, imageLength - offset);
            byte[] chunk = new byte[chunkLen];
            System.arraycopy(binary, HEADER_SIZE + offset, chunk, 0, chunkLen);
            blocks.add(chunk);
        }

        return new OtaFirmwareFile(headerCrc, headerLength, machineClass,
            sectorSize, imageSector, imageLength, imageCrc, blocks);
    }

    public int getVersion() { return machineClass; }
    public int getMachineClass() { return machineClass; }
    public int getBlockCount() { return blocks.size(); }
    public byte[] getBlock(int index) { return blocks.get(index); }
    public int getImageLength() { return imageLength; }

    public int getImageCrc32() {
        return imageCrc;
    }

    /**
     * CRC32 without final XOR — matches ucb_flash.py crc32_fw().
     * This is the standard CRC32 with init 0xFFFFFFFF but WITHOUT the final invert.
     */
    static int crc32NoFinalXor(byte[] data, int offset, int length) {
        CRC32 crc = new CRC32();
        crc.update(data, offset, length);
        // java.util.zip.CRC32 returns the standard CRC32 (with final XOR).
        // ucb_flash.py crc32_fw does NOT do final XOR, so we undo Java's.
        return (int) (crc.getValue() ^ 0xFFFFFFFFL);
    }

    private static byte[] hexStringToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    public static class FirmwareParseException extends Exception {
        public FirmwareParseException(String message) {
            super(message);
        }
    }
}
