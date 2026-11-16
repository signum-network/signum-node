package brs.peer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import brs.Signum;
import brs.Version;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.ClockDriftMonitor;

class PeersAfterHandshakeTest {

    private MockedStatic<Signum> signum;
    private ClockDriftMonitor monitor;
    private ClockDriftMonitor previousMonitor;
    private Peer peer;

    @BeforeEach
    void setUp() {
        FluxCapacitor fluxCapacitor = mock(FluxCapacitor.class);
        when(fluxCapacitor.getValue(FluxValues.MIN_PEER_VERSION)).thenReturn(Version.parse("v3.0.0"));
        PropertyService propertyService = mock(PropertyService.class);
        when(propertyService.getString(Props.NETWORK_NAME)).thenReturn("Signum");
        signum = mockStatic(Signum.class);
        signum.when(Signum::getFluxCapacitor).thenReturn(fluxCapacitor);
        signum.when(Signum::getPropertyService).thenReturn(propertyService);

        monitor = mock(ClockDriftMonitor.class);
        previousMonitor = Peers.clockDriftMonitor;
        Peers.clockDriftMonitor = monitor;

        peer = mock(Peer.class);
        when(peer.getPeerAddress()).thenReturn("1.2.3.4:9000");
        when(peer.getState()).thenReturn(Peer.State.CONNECTED);
        when(peer.isHigherOrEqualVersionThan(any())).thenReturn(true);
        when(peer.getNetworkName()).thenReturn("Signum");
        when(peer.getClockOffsetMillis()).thenReturn(5000L);
    }

    @AfterEach
    void tearDown() {
        signum.close();
        Peers.clockDriftMonitor = previousMonitor;
    }

    @Test
    void keepAfterHandshake_GivenValidPeer_ReportsItsClockByHost() {
        boolean kept = Peers.keepAfterHandshake(peer);

        assertTrue(kept);
        verify(monitor).onPeerClockSample("1.2.3.4", 5000L);
    }

    @Test
    void keepAfterHandshake_GivenWrongNetwork_RemovesPeerAndForgetsItsClock() {
        when(peer.getNetworkName()).thenReturn("other-net");

        boolean kept = Peers.keepAfterHandshake(peer);

        assertFalse(kept);
        verify(monitor, never()).onPeerClockSample(anyString(), anyLong());
        verify(monitor).removePeerClockSample("1.2.3.4");
    }

    @Test
    void keepAfterHandshake_GivenTooOldVersion_DoesNotReportItsClock() {
        when(peer.isHigherOrEqualVersionThan(any())).thenReturn(false);

        Peers.keepAfterHandshake(peer);

        verify(monitor, never()).onPeerClockSample(anyString(), anyLong());
    }

    @Test
    void keepAfterHandshake_GivenPeerNotConnected_DoesNotReportItsClock() {
        when(peer.getState()).thenReturn(Peer.State.NON_CONNECTED);

        Peers.keepAfterHandshake(peer);

        verify(monitor, never()).onPeerClockSample(anyString(), anyLong());
        verify(monitor).removePeerClockSample("1.2.3.4");
    }

    @Test
    void keepAfterHandshake_GivenNoClock_ForgetsItsSample() {
        when(peer.getClockOffsetMillis()).thenReturn(null);

        boolean kept = Peers.keepAfterHandshake(peer);

        assertTrue(kept);
        verify(monitor, never()).onPeerClockSample(anyString(), anyLong());
        verify(monitor).removePeerClockSample("1.2.3.4");
    }

    @Test
    void removePeer_GivenPeer_ForgetsItsClock() {
        Peers.removePeer(peer);

        verify(monitor).removePeerClockSample("1.2.3.4");
    }
}
