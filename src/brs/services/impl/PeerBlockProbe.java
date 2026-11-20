package brs.services.impl;

import brs.peer.Peer;

/** Asks a peer whether it has a given block, and for its own newest block if not. */
interface PeerBlockProbe {

    /**
     * @param hasOurTip      whether the peer has the block asked for (our tip); {@code null} if it
     *                       did not answer or answered with an error
     * @param ourTipIsTheirs whether that block is also the peer's newest block
     * @param theirTipId     the peer's newest block ID when it lacks ours; {@code null} if it has
     *                       ours or sent no milestones
     * @param latencyMs      round trip of the request in milliseconds; {@code null} without an answer
     * @param checkedAt      when the answer arrived (epoch milliseconds); {@code null} without an answer
     */
    record ProbeResult(Boolean hasOurTip, boolean ourTipIsTheirs, String theirTipId, Long latencyMs, Long checkedAt) {
        static ProbeResult noAnswer() {
            return new ProbeResult(null, false, null, null, null);
        }
    }

    ProbeResult probe(Peer peer, long blockId);
}
