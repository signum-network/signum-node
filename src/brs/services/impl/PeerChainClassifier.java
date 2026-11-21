package brs.services.impl;

import brs.services.impl.PeerBlockProbe.ProbeResult;

import java.util.OptionalInt;

/**
 * Decides a peer's {@link PeerChainStatus} from its answer to "do you have our tip?". Block IDs cover
 * the whole block, so the answer is exact, unlike comparing cumulative difficulty, and it uses only
 * what the peer says now, not a height recorded earlier.
 */
final class PeerChainClassifier {

    /**
     * A peer whose newest block lies on our chain counts as stale only from this many blocks below our
     * tip; fewer is usually propagation delay, not a stuck peer.
     */
    static final int STALE_GAP = 4;

    private PeerChainClassifier() {
    }

    /**
     * @param result                   the peer's answer
     * @param theirTipHeightOnOurChain the height of the peer's newest block on our chain, empty if we
     *                                 lack it; only read when the peer lacks our tip and named its own
     * @param ourHeight                the height of our tip
     */
    static PeerChainStatus classify(ProbeResult result, OptionalInt theirTipHeightOnOurChain, int ourHeight) {
        if (result.hasOurTip() == null) {
            return PeerChainStatus.UNKNOWN;
        }
        if (result.hasOurTip()) {
            return result.ourTipIsTheirs() ? PeerChainStatus.ON_CHAIN : PeerChainStatus.AHEAD;
        }
        if (result.theirTipId() == null) {
            return PeerChainStatus.UNKNOWN;
        }
        if (theirTipHeightOnOurChain.isEmpty()) {
            return PeerChainStatus.FORKING;
        }
        boolean farBehind = ourHeight - theirTipHeightOnOurChain.getAsInt() >= STALE_GAP;
        return farBehind ? PeerChainStatus.STALE : PeerChainStatus.ON_CHAIN;
    }
}
