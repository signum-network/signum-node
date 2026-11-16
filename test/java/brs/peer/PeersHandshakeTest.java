package brs.peer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PeersHandshakeTest {

    private static final int NOW = 100_000;

    private Peer peer;

    @BeforeEach
    void setUp() {
        peer = mock(Peer.class);
        when(peer.getState()).thenReturn(Peer.State.CONNECTED);
    }

    @Test
    void isHandshakeDue_GivenInboundGetInfoKeepsPeerFresh_IsDueAfterAnHourWithoutOwnHandshake() {
        // the peer's own hourly getInfo refreshes lastUpdated; only our outbound handshake measures its clock
        when(peer.getLastUpdated()).thenReturn(NOW - 10);
        when(peer.getLastHandshake()).thenReturn(NOW - 3601);

        assertTrue(Peers.isHandshakeDue(peer, NOW));
    }

    @Test
    void isHandshakeDue_GivenRecentOwnHandshake_IsNotDue() {
        when(peer.getLastUpdated()).thenReturn(NOW - 10);
        when(peer.getLastHandshake()).thenReturn(NOW - 3600);

        assertFalse(Peers.isHandshakeDue(peer, NOW));
    }

    @Test
    void isHandshakeDue_GivenPeerNotConnected_IsNotDue() {
        when(peer.getState()).thenReturn(Peer.State.NON_CONNECTED);
        when(peer.getLastHandshake()).thenReturn(0);

        assertFalse(Peers.isHandshakeDue(peer, NOW));
    }
}
