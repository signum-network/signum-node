package brs.services.impl;

/**
 * Decides a peer's {@link PeerChainStatus} from the heights and the peer's answer to "do you have
 * our block at height min(peerHeight, ourHeight)?". The block ID covers the whole block, so the
 * answer is exact, unlike comparing cumulative difficulty.
 */
final class PeerChainClassifier {

    private PeerChainClassifier() {
    }

    /**
     * @param ourHeight   our current height
     * @param peerHeight  the height the peer last reported
     * @param hasOurBlock whether the peer has our block at {@code min(peerHeight, ourHeight)};
     *                    {@code null} if it did not answer or answered with an error
     */
    static PeerChainStatus classify(int ourHeight, int peerHeight, Boolean hasOurBlock) {
        if (hasOurBlock == null) {
            return PeerChainStatus.UNKNOWN;
        }
        if (!hasOurBlock) {
            return PeerChainStatus.FORKING;
        }
        if (peerHeight > ourHeight) {
            return PeerChainStatus.AHEAD;
        }
        return peerHeight < ourHeight ? PeerChainStatus.STALE : PeerChainStatus.ON_CHAIN;
    }
}
