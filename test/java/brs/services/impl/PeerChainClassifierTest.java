package brs.services.impl;

import brs.services.impl.PeerBlockProbe.ProbeResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static brs.services.impl.PeerChainClassifier.classify;
import static brs.services.impl.PeerChainStatus.AHEAD;
import static brs.services.impl.PeerChainStatus.FORKING;
import static brs.services.impl.PeerChainStatus.ON_CHAIN;
import static brs.services.impl.PeerChainStatus.STALE;
import static brs.services.impl.PeerChainStatus.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeerChainClassifierTest {

    private static ProbeResult hasOurTip(boolean alsoTheirTip) {
        return new ProbeResult(true, alsoTheirTip, null, 10L, 1L);
    }

    private static ProbeResult lacksOurTip(String theirTipId) {
        return new ProbeResult(false, false, theirTipId, 10L, 1L);
    }

    @Test
    void classify_GivenPeerWhoseTipIsOurTip_ReturnsOnChain() {
        assertEquals(ON_CHAIN, classify(hasOurTip(true), false));
    }

    @Test
    void classify_GivenPeerThatHasOurTipAndMore_ReturnsAhead() {
        assertEquals(AHEAD, classify(hasOurTip(false), false));
    }

    @Test
    void classify_GivenPeerLackingOurTipWithATipWeHave_ReturnsStale() {
        // its newest block is on our chain: same chain, behind
        assertEquals(STALE, classify(lacksOurTip("980"), true));
    }

    @Test
    void classify_GivenPeerLackingOurTipWithATipWeLack_ReturnsForking() {
        assertEquals(FORKING, classify(lacksOurTip("999"), false));
    }

    @Test
    void classify_GivenPeerLackingOurTipWithoutATip_ReturnsUnknown() {
        // empty milestone list: nothing to judge
        assertEquals(UNKNOWN, classify(lacksOurTip(null), false));
    }

    @Test
    void classify_GivenNoAnswer_ReturnsUnknown() {
        assertEquals(UNKNOWN, classify(ProbeResult.noAnswer(), false));
    }

    @Test
    void apiValue_GivenEachStatus_ReturnsSpecString() {
        assertEquals(List.of("on-chain", "stale", "ahead", "forking", "unknown"),
                Arrays.stream(PeerChainStatus.values()).map(PeerChainStatus::apiValue).toList());
    }

    @Test
    void isOnOurChain_GivenEachStatus_TrueOnlyForSameChain() {
        assertTrue(ON_CHAIN.isOnOurChain());
        assertTrue(STALE.isOnOurChain());
        assertTrue(AHEAD.isOnOurChain());
        assertFalse(FORKING.isOnOurChain());
        assertFalse(UNKNOWN.isOnOurChain());
    }
}
