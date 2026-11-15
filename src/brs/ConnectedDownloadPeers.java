package brs;

import java.util.List;
import java.util.function.Supplier;

import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.peer.Peer;
import brs.peer.Peers;
import brs.props.PropertyService;
import brs.props.Props;

/**
 * The active peers that may help with a parallel block download: connected, not blacklisted, at
 * least the minimum peer version and on our network. Same filter as the feeder selection in the
 * download thread.
 */
final class ConnectedDownloadPeers implements DownloadPeers {

    private final PropertyService propertyService;
    private final Supplier<FluxCapacitor> fluxCapacitor;

    ConnectedDownloadPeers(PropertyService propertyService, Supplier<FluxCapacitor> fluxCapacitor) {
        this.propertyService = propertyService;
        this.fluxCapacitor = fluxCapacitor;
    }

    @Override
    public List<Peer> candidates() {
        Version minVersion = fluxCapacitor.get().getValue(FluxValues.MIN_PEER_VERSION);
        String networkName = propertyService.getString(Props.NETWORK_NAME);
        return Peers.getActivePeers().stream()
                .filter(peer -> peer.getState() == Peer.State.CONNECTED)
                .filter(peer -> !peer.isBlacklisted())
                .filter(peer -> peer.isHigherOrEqualVersionThan(minVersion))
                .filter(peer -> peer.getNetworkName() == null || peer.getNetworkName().equals(networkName))
                .toList();
    }
}
