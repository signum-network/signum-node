package brs.services.impl;

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

    @Test
    void classify_GivenSameHeightAndHasBlock_ReturnsOnChain() {
        assertEquals(ON_CHAIN, classify(100, 100, true));
    }

    @Test
    void classify_GivenPeerBehindAndHasBlock_ReturnsStale() {
        assertEquals(STALE, classify(100, 98, true));
    }

    @Test
    void classify_GivenPeerAheadAndHasBlock_ReturnsAhead() {
        assertEquals(AHEAD, classify(100, 101, true));
    }

    @Test
    void classify_GivenSameHeightAndLacksBlock_ReturnsForking() {
        assertEquals(FORKING, classify(100, 100, false));
    }

    @Test
    void classify_GivenPeerBehindAndLacksBlock_ReturnsForking() {
        assertEquals(FORKING, classify(100, 50, false));
    }

    @Test
    void classify_GivenPeerAheadAndLacksBlock_ReturnsForking() {
        // the peer is ahead but lacks our tip: we may be the ones on the fork
        assertEquals(FORKING, classify(100, 105, false));
    }

    @Test
    void classify_GivenNoAnswer_ReturnsUnknown() {
        assertEquals(UNKNOWN, classify(100, 100, null));
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
