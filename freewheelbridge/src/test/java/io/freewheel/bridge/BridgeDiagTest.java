package io.freewheel.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class BridgeDiagTest {

    // --- Serial overrides ---

    @Test
    public void serialDev_defaultsWhenMissingOrBlank() {
        assertEquals("/dev/ttyS4", BridgeDiag.resolveSerialDev(null));
        assertEquals("/dev/ttyS4", BridgeDiag.resolveSerialDev(""));
        assertEquals("/dev/ttyS4", BridgeDiag.resolveSerialDev("   "));
        // "settings get global x" prints "null" for unset keys
        assertEquals("/dev/ttyS4", BridgeDiag.resolveSerialDev("null"));
    }

    @Test
    public void serialDev_acceptsTtyPaths() {
        assertEquals("/dev/ttyS3", BridgeDiag.resolveSerialDev("/dev/ttyS3"));
        assertEquals("/dev/ttyUSB0", BridgeDiag.resolveSerialDev(" /dev/ttyUSB0 "));
        assertEquals("/dev/ttyFIQ0", BridgeDiag.resolveSerialDev("/dev/ttyFIQ0"));
    }

    @Test
    public void serialDev_rejectsAnythingElse() {
        assertEquals("/dev/ttyS4", BridgeDiag.resolveSerialDev("/dev/block/mmcblk0"));
        assertEquals("/dev/ttyS4", BridgeDiag.resolveSerialDev("/dev/ttyS4; reboot"));
        assertEquals("/dev/ttyS4", BridgeDiag.resolveSerialDev("/dev/../dev/ttyS4"));
        assertEquals("/dev/ttyS4", BridgeDiag.resolveSerialDev("/dev/tty"));
        assertEquals("/dev/ttyS4", BridgeDiag.resolveSerialDev("ttyS4"));
        assertEquals("/dev/ttyS4", BridgeDiag.resolveSerialDev("/dev/tty S4"));
    }

    @Test
    public void baud_defaultIsCurrentConstant() {
        assertEquals(230400, BridgeDiag.DEFAULT_BAUD);
        assertEquals(230400, BridgeDiag.resolveBaud(null));
        assertEquals(230400, BridgeDiag.resolveBaud("null"));
        assertEquals(230400, BridgeDiag.resolveBaud(""));
    }

    @Test
    public void baud_onlyWhitelistedRates() {
        assertEquals(115200, BridgeDiag.resolveBaud("115200"));
        assertEquals(921600, BridgeDiag.resolveBaud(" 921600 "));
        assertEquals(9600, BridgeDiag.resolveBaud("9600"));
        assertEquals(230400, BridgeDiag.resolveBaud("12345"));
        assertEquals(230400, BridgeDiag.resolveBaud("-1"));
        assertEquals(230400, BridgeDiag.resolveBaud("fast"));
        assertEquals(230400, BridgeDiag.resolveBaud("99999999999"));
    }

    // --- /proc parsing ---

    @Test
    public void parsePid() {
        assertEquals(1234, BridgeDiag.parsePid("1234"));
        assertEquals(-1, BridgeDiag.parsePid("self"));
        assertEquals(-1, BridgeDiag.parsePid("net"));
        assertEquals(-1, BridgeDiag.parsePid(""));
        assertEquals(-1, BridgeDiag.parsePid(null));
        assertEquals(-1, BridgeDiag.parsePid("12a"));
        assertEquals(-1, BridgeDiag.parsePid("99999999999"));
    }

    @Test
    public void fdTargetMatchesExactDeviceOnly() {
        assertTrue(BridgeDiag.fdTargetMatches("/dev/ttyS4", "/dev/ttyS4"));
        assertFalse(BridgeDiag.fdTargetMatches("/dev/ttyS40", "/dev/ttyS4"));
        assertFalse(BridgeDiag.fdTargetMatches("socket:[1234]", "/dev/ttyS4"));
        assertFalse(BridgeDiag.fdTargetMatches(null, "/dev/ttyS4"));
    }

    @Test
    public void cmdlineIsFirstNulSeparatedArg() {
        byte[] raw = "com.nautilus.nautiluslauncher\0--flag\0".getBytes(StandardCharsets.UTF_8);
        assertEquals("com.nautilus.nautiluslauncher", BridgeDiag.cmdlineFromBytes(raw, raw.length));
        byte[] noNul = "system_server".getBytes(StandardCharsets.UTF_8);
        assertEquals("system_server", BridgeDiag.cmdlineFromBytes(noNul, noNul.length));
        assertEquals("?", BridgeDiag.cmdlineFromBytes(new byte[8], 8));
        assertEquals("?", BridgeDiag.cmdlineFromBytes(raw, -1));
    }

    @Test
    public void onlyNautilusProcessesAreKillable() {
        assertTrue(BridgeDiag.isKillableHolder("com.nautilus.nautiluslauncher"));
        assertTrue(BridgeDiag.isKillableHolder("com.nautilus.g4assetmanager:remote"));
        assertFalse(BridgeDiag.isKillableHolder("system_server"));
        assertFalse(BridgeDiag.isKillableHolder("io.freewheel.bridge"));
        assertFalse(BridgeDiag.isKillableHolder("com.nautilusx.evil"));
        assertFalse(BridgeDiag.isKillableHolder("?"));
        assertFalse(BridgeDiag.isKillableHolder(null));
    }

    @Test
    public void formatHolders() {
        assertEquals("none", BridgeDiag.formatHolders(new ArrayList<BridgeDiag.Holder>()));
        assertEquals("none", BridgeDiag.formatHolders(null));
        List<BridgeDiag.Holder> h = Arrays.asList(
            new BridgeDiag.Holder(812, "com.nautilus.nautiluslauncher"),
            new BridgeDiag.Holder(99, "weird name,with=stuff"));
        assertEquals("812:com.nautilus.nautiluslauncher,99:weird_name_with_stuff",
            BridgeDiag.formatHolders(h));
    }

    // --- DIAG line contract ---

    @Test
    public void diagLineMatchesContract() {
        String line = BridgeDiag.formatDiagLine("/dev/ttyS4", 230400, true, 12345, 678, 9,
            420, 8, "SELECTION", "none");
        assertEquals("DIAG port=/dev/ttyS4@230400 open=1 bytes=12345 frames=678 crc=9"
            + " lastFrameAgeMs=420 fw=8 fwName=SELECTION holders=none", line);
    }

    @Test
    public void diagLineBeforeAnyData() {
        String line = BridgeDiag.formatDiagLine("/dev/ttyS4", 230400, false, 0, 0, 0,
            -1, -1, "UNKNOWN_-1", "812:com.nautilus.nautiluslauncher");
        assertEquals("DIAG port=/dev/ttyS4@230400 open=0 bytes=0 frames=0 crc=0"
            + " lastFrameAgeMs=-1 fw=-1 fwName=Unknown holders=812:com.nautilus.nautiluslauncher", line);
    }

    @Test
    public void frameAge() {
        assertEquals(-1, BridgeDiag.frameAgeMs(0, 5000));
        assertEquals(250, BridgeDiag.frameAgeMs(4750, 5000));
        assertEquals(0, BridgeDiag.frameAgeMs(6000, 5000));
    }

    // --- Deferred streaming decision ---

    @Test
    public void deferOnlyForKnownNonStreamingStates() {
        assertFalse("unknown state keeps old behaviour", BridgeDiag.shouldDeferStreaming(-1));
        assertFalse(BridgeDiag.shouldDeferStreaming(8)); // SELECTION
        assertFalse(BridgeDiag.shouldDeferStreaming(9)); // WORKOUT
        assertTrue(BridgeDiag.shouldDeferStreaming(10)); // SLEEP
        assertTrue(BridgeDiag.shouldDeferStreaming(12)); // SBC_DISCONNECTED
        assertTrue(BridgeDiag.shouldDeferStreaming(6));  // MFG
        assertTrue(BridgeDiag.shouldDeferStreaming(0));  // BOOT_FAILSAFE
    }

    @Test
    public void deferStreamingSettingIsOptIn() {
        assertFalse(BridgeDiag.isSettingEnabled(null));
        assertFalse(BridgeDiag.isSettingEnabled(""));
        assertFalse(BridgeDiag.isSettingEnabled("null"));
        assertFalse(BridgeDiag.isSettingEnabled("0"));
        assertFalse(BridgeDiag.isSettingEnabled("yes"));
        assertTrue(BridgeDiag.isSettingEnabled("1"));
        assertTrue(BridgeDiag.isSettingEnabled(" 1 "));
        assertTrue(BridgeDiag.isSettingEnabled("TRUE"));
    }

    @Test
    public void killHoldersOnlyAfterSustainedOutageWithPortOpen() {
        long min = 30000;
        assertFalse("healthy", BridgeDiag.shouldKillHolders(true, true, 1000, 100000, min));
        assertFalse("port closed", BridgeDiag.shouldKillHolders(false, false, 1000, 100000, min));
        assertFalse("outage not tracked yet", BridgeDiag.shouldKillHolders(false, true, 0, 100000, min));
        assertFalse("outage too short", BridgeDiag.shouldKillHolders(false, true, 80000, 100000, min));
        assertTrue(BridgeDiag.shouldKillHolders(false, true, 70000, 100000, min));
    }

    @Test
    public void fireDeferredOnlyOnTransitionIntoStreamingStateWhilePending() {
        assertTrue(BridgeDiag.shouldFireDeferredStreaming(true, true, 10, 8));
        assertTrue(BridgeDiag.shouldFireDeferredStreaming(true, true, 12, 9));
        assertFalse("not pending", BridgeDiag.shouldFireDeferredStreaming(false, true, 10, 8));
        assertFalse("workout stopped", BridgeDiag.shouldFireDeferredStreaming(true, false, 10, 8));
        assertFalse("no transition", BridgeDiag.shouldFireDeferredStreaming(true, true, 8, 8));
        assertFalse("into non-streaming", BridgeDiag.shouldFireDeferredStreaming(true, true, 8, 10));
    }

    @Test
    public void onceLimiter() {
        BridgeDiag.OnceLimiter l = new BridgeDiag.OnceLimiter();
        assertTrue(l.firstTime("frame:2:5:10"));
        assertFalse(l.firstTime("frame:2:5:10"));
        assertTrue(l.firstTime("frame:2:5:11"));
        for (int i = 0; i < 1000; i++) l.firstTime("k" + i);
        assertFalse("bounded", l.firstTime("new-key-after-cap"));
    }
}
