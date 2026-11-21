package brs.web.api.http.handler;

import brs.services.BlockPeerCheck;
import brs.services.BlockPeerCheck.Outcome;
import brs.services.NetworkAnalysisService;
import brs.web.api.http.common.JSONResponses;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetBlockPeerStatusTest {

    private NetworkAnalysisService service;
    private GetBlockPeerStatus handler;

    @BeforeEach
    void setUp() {
        service = mock(NetworkAnalysisService.class);
        handler = new GetBlockPeerStatus(service, () -> 9_000L);
    }

    private static HttpServletRequest request(String block) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getParameter("block")).thenReturn(block);
        return req;
    }

    @Test
    void processRequest_GivenCheckedBlock_ReturnsCounts() {
        when(service.checkBlockPeers(77L)).thenReturn(new BlockPeerCheck(Outcome.OK, 77L, 950, 5, 1, 2, 3, 8_000L));

        JsonObject json = handler.processRequest(request("77")).getAsJsonObject();

        assertEquals("ok", json.get("status").getAsString());
        assertEquals("77", json.get("block").getAsString());
        assertEquals(950, json.get("height").getAsInt());
        assertEquals(5, json.get("hasIt").getAsInt());
        assertEquals(1, json.get("missing").getAsInt());
        assertEquals(2, json.get("notYet").getAsInt());
        assertEquals(3, json.get("noAnswer").getAsInt());
        assertEquals(8_000L, json.get("checkedAt").getAsLong());
        assertEquals(9_000L, json.get("serverTime").getAsLong());
    }

    @Test
    void processRequest_GivenTooOldBlock_ReturnsTooOldStatus() {
        when(service.checkBlockPeers(77L)).thenReturn(BlockPeerCheck.without(Outcome.TOO_OLD, 77L, 10));

        JsonObject json = handler.processRequest(request("77")).getAsJsonObject();

        assertEquals("tooOld", json.get("status").getAsString());
        assertFalse(json.has("errorCode"));
    }

    @Test
    void processRequest_GivenRoundForOtherBlock_ReturnsBusyStatus() {
        when(service.checkBlockPeers(77L)).thenReturn(BlockPeerCheck.without(Outcome.BUSY, 77L, 950));

        assertEquals("busy", handler.processRequest(request("77")).getAsJsonObject().get("status").getAsString());
    }

    @Test
    void processRequest_GivenUnknownBlock_ReturnsUnknownBlock() {
        when(service.checkBlockPeers(77L)).thenReturn(BlockPeerCheck.without(Outcome.UNKNOWN_BLOCK, 77L, 0));

        assertEquals(JSONResponses.UNKNOWN_BLOCK, handler.processRequest(request("77")));
    }

    @Test
    void processRequest_GivenMissingBlock_ReturnsMissingBlock() {
        assertEquals(JSONResponses.MISSING_BLOCK, handler.processRequest(request(null)));
        verify(service, never()).checkBlockPeers(anyLong());
    }

    @Test
    void processRequest_GivenMalformedBlock_ReturnsIncorrectBlock() {
        assertEquals(JSONResponses.INCORRECT_BLOCK, handler.processRequest(request("abc")));
        verify(service, never()).checkBlockPeers(anyLong());
    }

    @Test
    void processRequest_GivenNegativeLookingId_ParsesUnsigned() {
        when(service.checkBlockPeers(-5L)).thenReturn(BlockPeerCheck.without(Outcome.BUSY, -5L, 0));

        handler.processRequest(request("18446744073709551611"));

        verify(service).checkBlockPeers(-5L);
    }
}
