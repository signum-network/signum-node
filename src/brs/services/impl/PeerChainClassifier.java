package brs.services.impl;

import brs.services.impl.PeerBlockProbe.ProbeResult;

/**
 * Decides a peer's {@link PeerChainStatus} from its answer to "do you have our tip?". Block IDs cover
 * the whole block, so the answer is exact, unlike comparing cumulative difficulty, and it uses only
 * what the peer says now, not a height recorded earlier.
 */
final class PeerChainClassifier {

    private PeerChainClassifier() {
    }

    /**
     * @param result         the peer's answer
     * @param weHaveTheirTip whether we have the peer's newest block; only read when the peer lacks
     *                       our tip and named its own
     */
    static PeerChainStatus classify(ProbeResult result, boolean weHaveTheirTip) {
        if (result.hasOurTip() == null) {
            return PeerChainStatus.UNKNOWN;
        }
        if (result.hasOurTip()) {
            return result.ourTipIsTheirs() ? PeerChainStatus.ON_CHAIN : PeerChainStatus.AHEAD;
        }
        if (result.theirTipId() == null) {
            return PeerChainStatus.UNKNOWN;
        }
        return weHaveTheirTip ? PeerChainStatus.STALE : PeerChainStatus.FORKING;
    }
}
