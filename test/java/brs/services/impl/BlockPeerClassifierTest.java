package brs.services.impl;

import brs.services.impl.PeerBlockProbe.ProbeResult;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static brs.services.impl.BlockPeerClassifier.classify;
import static brs.services.impl.BlockPeerState.HAS_IT;
import static brs.services.impl.BlockPeerState.MISSING;
import static brs.services.impl.BlockPeerState.NOT_YET;
import static brs.services.impl.BlockPeerState.NO_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockPeerClassifierTest {

    private static final int BLOCK_HEIGHT = 1_000;

    private static ProbeResult has(boolean asTip) {
        return new ProbeResult(true, asTip, null, 10L, 1L);
    }

    private static ProbeResult lacks() {
        return new ProbeResult(false, false, "123", 10L, 1L);
    }

    /** Lacks the block and named no tip of its own. */
    private static ProbeResult lacksWithoutTip() {
        return new ProbeResult(false, false, null, 10L, 1L);
    }

    @Test
    void classify_GivenNoAnswer_ReturnsNoAnswer() {
        assertEquals(NO_ANSWER, classify(ProbeResult.noAnswer(), OptionalInt.empty(), BLOCK_HEIGHT, BLOCK_HEIGHT));
    }

    @Test
    void classify_GivenPeerHasTheBlock_ReturnsHasIt() {
        assertEquals(HAS_IT, classify(has(false), OptionalInt.empty(), BLOCK_HEIGHT + 5, BLOCK_HEIGHT));
    }

    @Test
    void classify_GivenPeerHasTheBlockAsTip_ReturnsHasIt() {
        assertEquals(HAS_IT, classify(has(true), OptionalInt.empty(), BLOCK_HEIGHT, BLOCK_HEIGHT));
    }

    @Test
    void classify_GivenPeerTipOnOurChainBelowTheBlock_ReturnsNotYet() {
        // its tip is a block we have, lower than the asked one: same chain, just behind
        assertEquals(NOT_YET, classify(lacks(), OptionalInt.of(BLOCK_HEIGHT - 2), BLOCK_HEIGHT + 9, BLOCK_HEIGHT));
    }

    @Test
    void classify_GivenPeerTipNotOnOurChain_ReturnsMissingEvenWithStaleLowHeight() {
        // the height we recorded for the peer is old; its tip, a block we lack, shows it is on a fork
        assertEquals(MISSING, classify(lacks(), OptionalInt.empty(), BLOCK_HEIGHT - 3, BLOCK_HEIGHT));
    }

    @Test
    void classify_GivenPeerTipOnOurChainAtOrAboveTheBlock_ReturnsMissing() {
        assertEquals(MISSING, classify(lacks(), OptionalInt.of(BLOCK_HEIGHT), BLOCK_HEIGHT, BLOCK_HEIGHT));
    }

    @Test
    void classify_GivenNoTipInTheAnswer_FallsBackToTheReportedHeight() {
        assertEquals(NOT_YET, classify(lacksWithoutTip(), OptionalInt.empty(), BLOCK_HEIGHT - 1, BLOCK_HEIGHT));
        assertEquals(MISSING, classify(lacksWithoutTip(), OptionalInt.empty(), BLOCK_HEIGHT, BLOCK_HEIGHT));
    }

    @Test
    void apiValue_NamesEachStateForTheResponse() {
        assertEquals("hasIt", HAS_IT.apiValue());
        assertEquals("missing", MISSING.apiValue());
        assertEquals("notYet", NOT_YET.apiValue());
        assertEquals("noAnswer", NO_ANSWER.apiValue());
    }
}
