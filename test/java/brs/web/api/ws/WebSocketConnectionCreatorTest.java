package brs.web.api.ws;

import org.eclipse.jetty.ee10.websocket.server.JettyServerUpgradeRequest;
import org.eclipse.jetty.ee10.websocket.server.JettyServerUpgradeResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class WebSocketConnectionCreatorTest {

    private final Object socket = new Object();
    private JettyServerUpgradeRequest request;
    private JettyServerUpgradeResponse response;
    private final List<LogRecord> warnings = new ArrayList<>();
    private final Logger julLogger = Logger.getLogger(WebSocketConnectionCreator.class.getName());
    private final Handler warningCapture = new Handler() {
        @Override
        public void publish(LogRecord logRecord) {
            if (logRecord.getLevel().intValue() >= Level.WARNING.intValue()) {
                warnings.add(logRecord);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    @BeforeEach
    void setUp() {
        request = mock(JettyServerUpgradeRequest.class);
        response = mock(JettyServerUpgradeResponse.class);
        julLogger.addHandler(warningCapture);
    }

    @AfterEach
    void tearDown() {
        julLogger.removeHandler(warningCapture);
    }

    @Test
    void testCreateWebSocket_GivenConnectionsBelowLimit_AcceptsConnection() throws Exception {
        WebSocketConnectionCreator creator = new WebSocketConnectionCreator(500, () -> 499, () -> socket);

        Object created = creator.createWebSocket(request, response);

        assertSame(socket, created);
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void testCreateWebSocket_GivenLimitReached_RefusesWithServiceUnavailable() throws Exception {
        WebSocketConnectionCreator creator = new WebSocketConnectionCreator(500, () -> 500, () -> socket);

        Object created = creator.createWebSocket(request, response);

        assertNull(created);
        verify(response).sendError(eq(WebSocketConnectionCreator.SERVICE_UNAVAILABLE), anyString());
    }

    @Test
    void testCreateWebSocket_GivenLimitExceeded_RefusesConnection() throws Exception {
        WebSocketConnectionCreator creator = new WebSocketConnectionCreator(500, () -> 503, () -> socket);

        assertNull(creator.createWebSocket(request, response));
        verify(response).sendError(eq(WebSocketConnectionCreator.SERVICE_UNAVAILABLE), anyString());
    }

    @Test
    void testCreateWebSocket_GivenNoLimit_AcceptsAnyNumberOfConnections() throws Exception {
        WebSocketConnectionCreator creator = new WebSocketConnectionCreator(0, () -> 100_000, () -> socket);

        assertSame(socket, creator.createWebSocket(request, response));
    }

    @Test
    void testCreateWebSocket_GivenNegativeLimit_TreatsItAsNoLimit() throws Exception {
        WebSocketConnectionCreator creator = new WebSocketConnectionCreator(-1, () -> 100_000, () -> socket);

        assertSame(socket, creator.createWebSocket(request, response));
    }

    @Test
    void testCreateWebSocket_GivenRefusedConnection_DoesNotCreateEndpoint() throws Exception {
        WebSocketConnectionCreator creator = new WebSocketConnectionCreator(1, () -> 1, () -> {
            throw new AssertionError("endpoint must not be created for a refused connection");
        });

        assertNull(creator.createWebSocket(request, response));
    }

    @Test
    void testCreateWebSocket_GivenRepeatedRefusals_WarnsOnlyOnce() throws Exception {
        WebSocketConnectionCreator creator = new WebSocketConnectionCreator(1, () -> 1, () -> socket);

        creator.createWebSocket(request, response);
        creator.createWebSocket(request, response);
        creator.createWebSocket(request, response);

        assertEquals(1, warnings.size());
    }

    @Test
    void testCreateWebSocket_GivenLimitReachedAgainAfterAccepting_WarnsAgain() throws Exception {
        AtomicInteger open = new AtomicInteger(1);
        WebSocketConnectionCreator creator = new WebSocketConnectionCreator(1, open::get, () -> socket);

        creator.createWebSocket(request, response);
        open.set(0);
        creator.createWebSocket(request, response);
        open.set(1);
        creator.createWebSocket(request, response);

        assertEquals(2, warnings.size());
    }
}
