package brs.peer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import brs.Signum;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.ClockDriftMonitor;
import brs.services.TimeService;
import brs.util.JSON;

class PeerImplClockTest {

    private static final long LOCAL_MILLIS = 1_000_000_000L;

    private HttpServer server;
    private volatile String responseBody;
    private MockedStatic<Signum> signum;
    private ClockDriftMonitor monitor;
    private TimeService previousTimeService;
    private ClockDriftMonitor previousMonitor;
    private JsonElement previousRequest;
    private PeerImpl peer;
    private String address;

    @BeforeEach
    void setUp() throws IOException {
        Peers.connectTimeout = 2000;
        Peers.readTimeout = 2000;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/burst", this::handle);
        server.start();
        address = "127.0.0.1:" + server.getAddress().getPort();
        peer = new PeerImpl("127.0.0.1", address);

        PropertyService propertyService = mock(PropertyService.class);
        when(propertyService.getString(Props.APPLICATION)).thenReturn("BRS");
        signum = mockStatic(Signum.class);
        signum.when(Signum::getPropertyService).thenReturn(propertyService);

        TimeService timeService = mock(TimeService.class);
        when(timeService.getEpochTimeMillis()).thenReturn(LOCAL_MILLIS);
        monitor = mock(ClockDriftMonitor.class);
        previousTimeService = Peers.timeService;
        previousMonitor = Peers.clockDriftMonitor;
        previousRequest = Peers.myPeerInfoRequest;
        Peers.timeService = timeService;
        Peers.clockDriftMonitor = monitor;
        JsonObject request = new JsonObject();
        request.addProperty("requestType", "getInfo");
        Peers.myPeerInfoRequest = JSON.prepareRequest(request);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        signum.close();
        Peers.timeService = previousTimeService;
        Peers.clockDriftMonitor = previousMonitor;
        Peers.myPeerInfoRequest = previousRequest;
    }

    @Test
    void connect_GivenPeerWithTime_StoresOffsetAndReportsIt() {
        // Arrange: the peer's clock is 5 s behind ours
        responseBody = "{\"application\":\"X\",\"" + PeerClock.FIELD + "\":" + (LOCAL_MILLIS - 5000) + "}";

        // Act
        peer.connect(0);

        // Assert
        assertEquals(Peer.State.CONNECTED, peer.getState());
        assertEquals(5000L, peer.getClockOffsetMillis());
        verify(monitor).onPeerClockSample(eq("127.0.0.1"), eq(5000L));
    }

    @Test
    void connect_GivenOldPeerWithoutTime_ConnectsWithoutOffset() {
        responseBody = "{\"application\":\"X\"}";

        peer.connect(0);

        assertEquals(Peer.State.CONNECTED, peer.getState());
        assertNull(peer.getClockOffsetMillis());
        verify(monitor, never()).onPeerClockSample(anyString(), anyLong());
    }

    @Test
    void connect_GivenErrorResponse_ReportsNoSample() {
        responseBody = "{\"error\":\"nope\",\"" + PeerClock.FIELD + "\":" + LOCAL_MILLIS + "}";

        peer.connect(0);

        assertNull(peer.getClockOffsetMillis());
        verify(monitor, never()).onPeerClockSample(anyString(), anyLong());
    }

    private void handle(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
