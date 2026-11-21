package brs.services.impl;

import brs.services.impl.PeerBlockProbe.ProbeResult;
import org.junit.jupiter.api.Test;

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

    @Test
    void classify_GivenNoAnswer_ReturnsNoAnswer() {
        assertEquals(NO_ANSWER, classify(ProbeResult.noAnswer(), BLOCK_HEIGHT, BLOCK_HEIGHT));
    }

    @Test
    void classify_GivenPeerHasTheBlock_ReturnsHasIt() {
        assertEquals(HAS_IT, classify(has(false), BLOCK_HEIGHT + 5, BLOCK_HEIGHT));
    }

    @Test
    void classify_GivenPeerHasTheBlockAsTip_ReturnsHasIt() {
        assertEquals(HAS_IT, classify(has(true), BLOCK_HEIGHT, BLOCK_HEIGHT));
    }

    @Test
    void classify_GivenPeerLacksTheBlockAtOrAboveItsHeight_ReturnsMissing() {
        assertEquals(MISSING, classify(lacks(), BLOCK_HEIGHT, BLOCK_HEIGHT));
        assertEquals(MISSING, classify(lacks(), BLOCK_HEIGHT + 3, BLOCK_HEIGHT));
    }

    @Test
    void classify_GivenPeerBelowTheBlock_ReturnsNotYet() {
        // a peer that is not that far yet cannot have the block; that is no sign of a fork
        assertEquals(NOT_YET, classify(lacks(), BLOCK_HEIGHT - 1, BLOCK_HEIGHT));
    }

    @Test
    void apiValue_NamesEachStateForTheResponse() {
        assertEquals("hasIt", HAS_IT.apiValue());
        assertEquals("missing", MISSING.apiValue());
        assertEquals("notYet", NOT_YET.apiValue());
        assertEquals("noAnswer", NO_ANSWER.apiValue());
    }
}
