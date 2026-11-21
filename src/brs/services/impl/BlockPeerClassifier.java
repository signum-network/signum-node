package brs.services.impl;

import brs.services.impl.PeerBlockProbe.ProbeResult;

import java.util.OptionalInt;

/**
 * Decides a peer's {@link BlockPeerState} for one block from its answer to "do you have this block?".
 * Unlike {@link PeerChainClassifier}, the block need not be our tip.
 */
final class BlockPeerClassifier {

    private BlockPeerClassifier() {
    }

    /**
     * A peer without the block is only "not yet" there if its own tip is a block of our chain below
     * the asked one; the height recorded for a peer can be old, so it decides only when the answer
     * names no tip.
     *
     * @param result                  the peer's answer for the block
     * @param theirTipHeightOnOurChain height of the tip the peer named, if that block is in our chain
     * @param peerHeight              the height the peer last reported
     * @param blockHeight             the height of the asked block
     */
    static BlockPeerState classify(ProbeResult result, OptionalInt theirTipHeightOnOurChain, int peerHeight,
            int blockHeight) {
        if (result.hasOurTip() == null) {
            return BlockPeerState.NO_ANSWER;
        }
        if (result.hasOurTip()) {
            return BlockPeerState.HAS_IT;
        }
        if (result.theirTipId() != null) {
            boolean behind = theirTipHeightOnOurChain.isPresent() && theirTipHeightOnOurChain.getAsInt() < blockHeight;
            return behind ? BlockPeerState.NOT_YET : BlockPeerState.MISSING;
        }
        return peerHeight < blockHeight ? BlockPeerState.NOT_YET : BlockPeerState.MISSING;
    }
}
