package brs.services;

import java.util.OptionalLong;

/** What the peer server knows about this node's reachability, for the web UI's node health. */
public interface PeerServerFacts {

    /** @return true if the peer server runs, so other nodes can connect at all */
    boolean peerServerRunning();

    /** @return true if the node has a public address to announce to other nodes */
    boolean announcesAddress();

    /** @return the time of the last P2P request from a public address */
    OptionalLong lastPublicInboundMillis();

    /** @return the time of the last P2P request from a local (non-loopback) address */
    OptionalLong lastLocalInboundMillis();
}
