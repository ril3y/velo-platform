package io.freewheel.ucb;

import static org.junit.Assert.assertEquals;

import io.freewheel.ucb.BridgeDiagnostics.LinkState;
import org.junit.Test;

public class BridgeDiagnosticsTest {

    @Test
    public void notBound_isBridgeNotRunning() {
        assertEquals(LinkState.BRIDGE_NOT_RUNNING, BridgeDiagnostics.classify(false, true, 100));
        assertEquals(LinkState.BRIDGE_NOT_RUNNING, BridgeDiagnostics.classify(false, false, -1));
    }

    @Test
    public void openWithoutFrames_isNoData() {
        assertEquals(LinkState.PORT_OPEN_NO_DATA, BridgeDiagnostics.classify(true, true, -1));
        assertEquals(LinkState.PORT_OPEN_NO_DATA, BridgeDiagnostics.classify(true, true, 5001));
    }

    @Test
    public void recentFrame_isBikeConnected() {
        assertEquals(LinkState.BIKE_CONNECTED, BridgeDiagnostics.classify(true, true, 0));
        assertEquals(LinkState.BIKE_CONNECTED, BridgeDiagnostics.classify(true, true, 5000));
    }

    @Test
    public void closedPort_isPortClosed() {
        assertEquals(LinkState.PORT_CLOSED, BridgeDiagnostics.classify(true, false, -1));
        assertEquals(LinkState.PORT_CLOSED, BridgeDiagnostics.classify(true, false, 60000));
    }

    @Test
    public void labels() {
        assertEquals("Bridge not running", LinkState.BRIDGE_NOT_RUNNING.label);
        assertEquals("Port open – no data from bike", LinkState.PORT_OPEN_NO_DATA.label);
        assertEquals("Bike connected", LinkState.BIKE_CONNECTED.label);
    }
}
