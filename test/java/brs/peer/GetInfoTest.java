package brs.peer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import brs.services.TimeService;

class GetInfoTest {

    private JsonElement previousPeerInfo;
    private TimeService timeService;
    private GetInfo getInfo;

    @BeforeEach
    void setUp() {
        previousPeerInfo = Peers.myPeerInfoResponse;
        JsonObject peerInfo = new JsonObject();
        peerInfo.addProperty("application", "BRS");
        Peers.myPeerInfoResponse = peerInfo;
        timeService = mock(TimeService.class);
        when(timeService.getEpochTimeMillis()).thenReturn(123456789L);
        getInfo = new GetInfo(timeService, true);
    }

    @AfterEach
    void tearDown() {
        Peers.myPeerInfoResponse = previousPeerInfo;
    }

    @Test
    void processRequest_GivenRequest_RepliesWithEpochTimeRoundedToSeconds() {
        // Act
        JsonObject response = getInfo.processRequest(new JsonObject(), mock(PeerImpl.class)).getAsJsonObject();

        // Assert
        assertEquals(123457000L, response.get(PeerClock.FIELD).getAsLong());
        assertEquals("BRS", response.get("application").getAsString());
    }

    @Test
    void processRequest_GivenClockSharingDisabled_RepliesWithoutEpochTimeMillis() {
        // Arrange
        GetInfo notSharing = new GetInfo(timeService, false);

        // Act
        JsonObject response = notSharing.processRequest(new JsonObject(), mock(PeerImpl.class)).getAsJsonObject();

        // Assert
        assertFalse(response.has(PeerClock.FIELD));
        assertEquals("BRS", response.get("application").getAsString());
    }

    @Test
    void processRequest_GivenTwoRequests_DoesNotChangeSharedPeerInfo() {
        getInfo.processRequest(new JsonObject(), mock(PeerImpl.class));
        when(timeService.getEpochTimeMillis()).thenReturn(987654321L);

        JsonObject second = getInfo.processRequest(new JsonObject(), mock(PeerImpl.class)).getAsJsonObject();

        assertEquals(987654000L, second.get(PeerClock.FIELD).getAsLong());
        assertFalse(Peers.myPeerInfoResponse.getAsJsonObject().has(PeerClock.FIELD));
    }
}
