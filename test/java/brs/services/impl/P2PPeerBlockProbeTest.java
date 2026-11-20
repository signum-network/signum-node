package brs.services.impl;

import brs.peer.Peer;
import brs.services.impl.PeerBlockProbe.ProbeResult;
import brs.util.Convert;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class P2PPeerBlockProbeTest {

    private final P2PPeerBlockProbe probe = new P2PPeerBlockProbe();

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static Peer answering(JsonObject response) {
        Peer peer = mock(Peer.class);
        when(peer.send(any())).thenReturn(response);
        return peer;
    }

    @Test
    void probe_GivenPeerHasBlock_ReturnsTrueWithTiming() {
        // Act
        ProbeResult result = probe.probe(answering(json("{\"milestoneBlockIds\":[\"123\"],\"last\":true}")), 123L);

        // Assert
        assertEquals(Boolean.TRUE, result.hasOurTip());
        assertTrue(result.ourTipIsTheirs());
        assertNotNull(result.latencyMs());
        assertTrue(result.latencyMs() >= 0);
        assertNotNull(result.checkedAt());
    }

    @Test
    void probe_GivenPeerHasBlockButMore_ReportsItIsNotTheirTip() {
        // without "last" the block is not the peer's newest one
        ProbeResult result = probe.probe(answering(json("{\"milestoneBlockIds\":[\"123\"]}")), 123L);

        assertEquals(Boolean.TRUE, result.hasOurTip());
        assertFalse(result.ourTipIsTheirs());
    }

    @Test
    void probe_GivenPeerAnswersWithOwnMilestones_ReturnsFalseWithTheirTip() {
        // a peer without the block answers with milestones of its own chain, newest first
        ProbeResult result = probe.probe(answering(json("{\"milestoneBlockIds\":[\"999\",\"888\"]}")), 123L);

        assertEquals(Boolean.FALSE, result.hasOurTip());
        assertEquals("999", result.theirTipId());
    }

    @Test
    void probe_GivenEmptyMilestones_ReturnsFalseWithoutTip() {
        ProbeResult result = probe.probe(answering(json("{\"milestoneBlockIds\":[]}")), 123L);

        assertEquals(Boolean.FALSE, result.hasOurTip());
        assertNull(result.theirTipId());
    }

    @Test
    void probe_GivenNullResponse_ReturnsNoAnswer() {
        assertEquals(ProbeResult.noAnswer(), probe.probe(answering(null), 123L));
    }

    @Test
    void probe_GivenErrorResponse_ReturnsNoAnswer() {
        assertEquals(ProbeResult.noAnswer(), probe.probe(answering(json("{\"error\":\"x\"}")), 123L));
    }

    @Test
    void probe_GivenResponseWithoutArray_ReturnsNoAnswer() {
        assertEquals(ProbeResult.noAnswer(), probe.probe(answering(json("{}")), 123L));
    }

    @Test
    void probe_GivenNegativeBlockId_SendsUnsignedLastBlockId() {
        // Arrange: a block ID with the high bit set
        long blockId = -5L;
        String unsigned = Convert.toUnsignedLong(blockId);
        Peer peer = answering(json("{\"milestoneBlockIds\":[\"" + unsigned + "\"]}"));

        // Act
        ProbeResult result = probe.probe(peer, blockId);

        // Assert
        ArgumentCaptor<JsonElement> request = ArgumentCaptor.forClass(JsonElement.class);
        verify(peer).send(request.capture());
        JsonObject sent = request.getValue().getAsJsonObject();
        assertEquals("getMilestoneBlockIds", sent.get("requestType").getAsString());
        assertEquals("18446744073709551611", sent.get("lastBlockId").getAsString());
        assertEquals(Boolean.TRUE, result.hasOurTip());
    }
}
