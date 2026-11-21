package brs.web.api.http.handler;

import brs.services.NodeHealthService;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetNodeHealthTest {

    @Test
    void processRequest_AddsServerTimeToACopy() {
        // the service's object is shared between requests and must stay unchanged
        JsonObject shared = new JsonObject();
        shared.addProperty("databaseType", "sqlite");
        NodeHealthService service = mock(NodeHealthService.class);
        when(service.getNodeHealth()).thenReturn(shared);
        GetNodeHealth handler = new GetNodeHealth(service, () -> 42L);

        JsonObject response = (JsonObject) handler.processRequest(mock(HttpServletRequest.class));

        assertEquals(42L, response.get("serverTime").getAsLong());
        assertEquals("sqlite", response.get("databaseType").getAsString());
        assertFalse(shared.has("serverTime"));
    }
}
