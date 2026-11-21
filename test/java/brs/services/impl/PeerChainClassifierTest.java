package brs.services.impl;

import brs.services.impl.PeerBlockProbe.ProbeResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

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

    private static final int OUR_HEIGHT = 1000;
    private static final OptionalInt NOT_ON_OUR_CHAIN = OptionalInt.empty();

    private static OptionalInt behind(int blocks) {
        return OptionalInt.of(OUR_HEIGHT - blocks);
    }

    private static ProbeResult hasOurTip(boolean alsoTheirTip) {
        return new ProbeResult(true, alsoTheirTip, null, 10L, 1L);
    }

    private static ProbeResult lacksOurTip(String theirTipId) {
        return new ProbeResult(false, false, theirTipId, 10L, 1L);
    }

    @Test
    void classify_GivenPeerWhoseTipIsOurTip_ReturnsOnChain() {
        assertEquals(ON_CHAIN, classify(hasOurTip(true), NOT_ON_OUR_CHAIN, OUR_HEIGHT));
    }

    @Test
    void classify_GivenPeerThatHasOurTipAndMore_ReturnsAhead() {
        assertEquals(AHEAD, classify(hasOurTip(false), NOT_ON_OUR_CHAIN, OUR_HEIGHT));
    }

    @Test
    void classify_GivenPeerFourBlocksBehindOnOurChain_ReturnsStale() {
        // its newest block is on our chain, 4 blocks below our tip: same chain, really behind
        assertEquals(STALE, classify(lacksOurTip("980"), behind(4), OUR_HEIGHT));
        assertEquals(STALE, classify(lacksOurTip("980"), behind(20), OUR_HEIGHT));
    }

    @Test
    void classify_GivenPeerUpToThreeBlocksBehindOnOurChain_ReturnsOnChain() {
        // 1-3 blocks behind is usually propagation delay, not a stuck peer
        assertEquals(ON_CHAIN, classify(lacksOurTip("999"), behind(1), OUR_HEIGHT));
        assertEquals(ON_CHAIN, classify(lacksOurTip("997"), behind(3), OUR_HEIGHT));
    }

    @Test
    void classify_GivenPeerLackingOurTipWithATipWeLack_ReturnsForking() {
        assertEquals(FORKING, classify(lacksOurTip("999"), NOT_ON_OUR_CHAIN, OUR_HEIGHT));
    }

    @Test
    void classify_GivenPeerLackingOurTipWithoutATip_ReturnsUnknown() {
        // empty milestone list: nothing to judge
        assertEquals(UNKNOWN, classify(lacksOurTip(null), NOT_ON_OUR_CHAIN, OUR_HEIGHT));
    }

    @Test
    void classify_GivenNoAnswer_ReturnsUnknown() {
        assertEquals(UNKNOWN, classify(ProbeResult.noAnswer(), NOT_ON_OUR_CHAIN, OUR_HEIGHT));
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
