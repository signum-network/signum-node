package brs.services.impl;

import brs.services.impl.PeerBlockProbe.ProbeResult;

/**
 * Decides a peer's {@link BlockPeerState} for one block from its answer to "do you have this block?".
 * Unlike {@link PeerChainClassifier}, the block need not be our tip.
 */
final class BlockPeerClassifier {

    private BlockPeerClassifier() {
    }

    /**
     * @param result      the peer's answer for the block
     * @param peerHeight  the height the peer last reported
     * @param blockHeight the height of the asked block
     */
    static BlockPeerState classify(ProbeResult result, int peerHeight, int blockHeight) {
        if (result.hasOurTip() == null) {
            return BlockPeerState.NO_ANSWER;
        }
        if (result.hasOurTip()) {
            return BlockPeerState.HAS_IT;
        }
        return peerHeight < blockHeight ? BlockPeerState.NOT_YET : BlockPeerState.MISSING;
    }
}
