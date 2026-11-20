package brs.web.api.http.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import brs.Version;
import brs.peer.Peer;

class JSONDataPeerTest {

    private Peer peer;

    @BeforeEach
    void setUp() {
        peer = mock(Peer.class);
        when(peer.getState()).thenReturn(Peer.State.CONNECTED);
        when(peer.getVersion()).thenReturn(Version.parse("v3.9.0"));
    }

    @Test
    void peer_GivenKnownOffset_AddsClockOffsetMillis() {
        when(peer.getClockOffsetMillis()).thenReturn(-1234L);

        JsonObject json = JSONData.peer(peer);

        assertEquals(-1234L, json.get("clockOffsetMillis").getAsLong());
    }

    @Test
    void peer_GivenUnknownOffset_AddsNull() {
        when(peer.getClockOffsetMillis()).thenReturn(null);

        JsonObject json = JSONData.peer(peer);

        assertTrue(json.has("clockOffsetMillis"));
        assertTrue(json.get("clockOffsetMillis").isJsonNull());
    }
}
