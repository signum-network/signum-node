package brs.services.impl;

import brs.peer.Peer;

/** Asks a peer whether it has a given block. */
interface PeerBlockProbe {

    /**
     * @param hasBlock  whether the peer has the block; {@code null} if it did not answer or
     *                  answered with an error
     * @param latencyMs round trip of the request in milliseconds; {@code null} without an answer
     * @param checkedAt when the answer arrived (epoch milliseconds); {@code null} without an answer
     */
    record ProbeResult(Boolean hasBlock, Long latencyMs, Long checkedAt) {
        static ProbeResult noAnswer() {
            return new ProbeResult(null, null, null);
        }
    }

    ProbeResult probe(Peer peer, long blockId);
}
