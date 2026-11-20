package brs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.peer.Peer;
import brs.peer.Peers;
import brs.props.PropertyService;
import brs.props.Props;

class ConnectedDownloadPeersTest {

    private static final Version MIN_VERSION = Version.parse("v3.9.0");

    @Test
    void candidates_GivenMixedPeers_ReturnsOnlyConnectedCurrentPeersOnOurNetwork() {
        // Arrange
        PropertyService propertyService = mock(PropertyService.class);
        when(propertyService.getString(Props.NETWORK_NAME)).thenReturn("Signum");
        FluxCapacitor fluxCapacitor = mock(FluxCapacitor.class);
        when(fluxCapacitor.getValue(FluxValues.MIN_PEER_VERSION)).thenReturn(MIN_VERSION);

        Peer ok = peer(Peer.State.CONNECTED, false, true, "Signum");
        Peer unnamedNetwork = peer(Peer.State.CONNECTED, false, true, null);
        Peer disconnected = peer(Peer.State.DISCONNECTED, false, true, "Signum");
        Peer blacklisted = peer(Peer.State.CONNECTED, true, true, "Signum");
        Peer oldVersion = peer(Peer.State.CONNECTED, false, false, "Signum");
        Peer otherNetwork = peer(Peer.State.CONNECTED, false, true, "Signum-TESTNET");
        Peer notSharing = peer(Peer.State.CONNECTED, false, true, "Signum");
        when(notSharing.shareAddress()).thenReturn(false);

        try (MockedStatic<Peers> peers = mockStatic(Peers.class)) {
            peers.when(Peers::getActivePeers)
                    .thenReturn(List.of(ok, unnamedNetwork, disconnected, blacklisted, oldVersion, otherNetwork,
                            notSharing));

            // Act
            List<Peer> candidates = new ConnectedDownloadPeers(propertyService, () -> fluxCapacitor).candidates();

            // Assert
            assertEquals(List.of(ok, unnamedNetwork), candidates);
        }
    }

    private static Peer peer(Peer.State state, boolean blacklisted, boolean currentVersion, String networkName) {
        Peer peer = mock(Peer.class);
        when(peer.getState()).thenReturn(state);
        when(peer.isBlacklisted()).thenReturn(blacklisted);
        when(peer.isHigherOrEqualVersionThan(any())).thenReturn(currentVersion);
        when(peer.getNetworkName()).thenReturn(networkName);
        when(peer.shareAddress()).thenReturn(true);
        return peer;
    }
}
