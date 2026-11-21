package brs.peer;

import brs.services.PeerServerFacts;

import java.util.OptionalLong;

/** {@link PeerServerFacts} from the running {@link Peers} networking. */
public final class PeersServerFacts implements PeerServerFacts {

    @Override
    public boolean peerServerRunning() {
        return Peers.isPeerServerRunning();
    }

    @Override
    public boolean announcesAddress() {
        return Peers.announcesAddress();
    }

    @Override
    public OptionalLong lastPublicInboundMillis() {
        return Peers.getInboundContacts().lastPublicMillis();
    }

    @Override
    public OptionalLong lastLocalInboundMillis() {
        return Peers.getInboundContacts().lastLocalMillis();
    }
}
