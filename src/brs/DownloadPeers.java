package brs;

import java.util.List;

import brs.peer.Peer;

/** Source of peers that may help with a parallel block download. */
interface DownloadPeers {

    /** @return the peers currently eligible to serve blocks, in no particular order */
    List<Peer> candidates();
}
