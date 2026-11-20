package brs.web.api.http.handler;

import brs.services.NetworkAnalysisService;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetNetworkStatusTest {

    @Test
    void processRequest_GivenCachedSnapshot_AddsServerTimeWithoutChangingTheCache() {
        // Arrange: ages in the UI must be computed against the node's clock, not the browser's
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("cachedAt", 1_000L);
        NetworkAnalysisService service = mock(NetworkAnalysisService.class);
        when(service.getNetworkStatus()).thenReturn(snapshot);
        GetNetworkStatus handler = new GetNetworkStatus(service, () -> 5_000L);

        // Act
        JsonElement response = handler.processRequest(mock(HttpServletRequest.class));

        // Assert
        JsonObject json = response.getAsJsonObject();
        assertEquals(5_000L, json.get("serverTime").getAsLong());
        assertEquals(1_000L, json.get("cachedAt").getAsLong());
        assertFalse(snapshot.has("serverTime"));
    }

    @Test
    void processRequest_GivenNoSnapshot_ReturnsEmptyObjectWithServerTime() {
        // Arrange
        NetworkAnalysisService service = mock(NetworkAnalysisService.class);
        GetNetworkStatus handler = new GetNetworkStatus(service, () -> 5_000L);

        // Act
        JsonObject json = handler.processRequest(mock(HttpServletRequest.class)).getAsJsonObject();

        // Assert
        assertTrue(json.has("serverTime"));
    }
}
