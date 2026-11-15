package brs.peer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import brs.util.JSON;

class PeerImplKeepAliveTest {

    private HttpServer server;
    private final List<Integer> clientPorts = Collections.synchronizedList(new ArrayList<>());
    /** Status codes to answer with, in order; 200 once the list is used up. */
    private final List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger closeAfterRequest = new AtomicInteger(-1);
    private PeerImpl peer;

    @BeforeEach
    void setUp() throws IOException {
        Peers.keepAlive = true;
        Peers.connectTimeout = 2000;
        Peers.readTimeout = 2000;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/burst", this::handle);
        server.start();
        peer = new PeerImpl("127.0.0.1", "127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        Peers.keepAlive = true;
    }

    @Test
    void send_GivenKeepAlive_ReusesTheConnectionForTheNextRequest() {
        // Act
        JsonObject first = peer.send(request());
        JsonObject second = peer.send(request());

        // Assert
        assertOk(first);
        assertOk(second);
        assertEquals(2, clientPorts.size());
        assertEquals(clientPorts.get(0), clientPorts.get(1));
    }

    @Test
    void send_GivenKeepAliveOff_OpensANewConnectionPerRequest() {
        Peers.keepAlive = false;

        assertOk(peer.send(request()));
        assertOk(peer.send(request()));

        assertNotEquals(clientPorts.get(0), clientPorts.get(1));
    }

    @Test
    void send_GivenNon200Response_DoesNotReuseTheConnection() {
        statuses.add(500);

        peer.send(request());
        JsonObject second = peer.send(request());

        assertOk(second);
        assertNotEquals(clientPorts.get(0), clientPorts.get(1));
    }

    @Test
    void send_GivenServerClosedTheConnection_NextRequestStillSucceeds() {
        closeAfterRequest.set(1);

        assertOk(peer.send(request()));
        JsonObject second = peer.send(request());

        assertOk(second);
        assertNotEquals(clientPorts.get(0), clientPorts.get(1));
    }

    @Test
    void send_GivenServerSilentlyClosedThePooledConnection_NextRequestStillSucceeds() throws Exception {
        // Arrange: a raw server that answers without "Connection: close" and then hangs up
        try (ServerSocket raw = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            Thread acceptor = new Thread(() -> answerAndHangUp(raw), "raw-http");
            acceptor.setDaemon(true);
            acceptor.start();
            PeerImpl rawPeer = new PeerImpl("127.0.0.1", "127.0.0.1:" + raw.getLocalPort());
            assertOk(rawPeer.send(request()));
            Thread.sleep(100); // let the close reach the client

            // Act
            JsonObject second = rawPeer.send(request());

            // Assert
            assertOk(second);
        }
    }

    private static void answerAndHangUp(ServerSocket raw) {
        while (!raw.isClosed()) {
            try (Socket socket = raw.accept()) {
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
                int contentLength = 0;
                String line;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    if (line.toLowerCase().startsWith("content-length:")) {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    }
                }
                for (int i = 0; i < contentLength; i++) {
                    in.read();
                }
                byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                OutputStream out = socket.getOutputStream();
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + body.length
                        + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
                out.write(body);
                out.flush();
            } catch (IOException e) {
                return;
            }
        }
    }

    @Test
    void applyKeepAlive_GivenKeepAliveOn_KeepsIdleConnectionsLongerThanTheJdkDefault() {
        String previous = System.clearProperty(Peers.KEEP_ALIVE_TIME_PROPERTY);
        try {
            Peers.applyKeepAlive(true);

            assertTrue(Peers.keepAlive);
            assertEquals(String.valueOf(Peers.KEEP_ALIVE_IDLE_SECONDS), System.getProperty(Peers.KEEP_ALIVE_TIME_PROPERTY));
            assertEquals(20, Peers.KEEP_ALIVE_IDLE_SECONDS);
        } finally {
            restore(previous);
        }
    }

    @Test
    void applyKeepAlive_GivenPropertySetByOperator_KeepsIt() {
        String previous = System.setProperty(Peers.KEEP_ALIVE_TIME_PROPERTY, "7");
        try {
            Peers.applyKeepAlive(true);

            assertEquals("7", System.getProperty(Peers.KEEP_ALIVE_TIME_PROPERTY));
        } finally {
            restore(previous);
        }
    }

    @Test
    void applyKeepAlive_GivenKeepAliveOff_LeavesTheJdkDefault() {
        String previous = System.clearProperty(Peers.KEEP_ALIVE_TIME_PROPERTY);
        try {
            Peers.applyKeepAlive(false);

            assertEquals(false, Peers.keepAlive);
            assertEquals(null, System.getProperty(Peers.KEEP_ALIVE_TIME_PROPERTY));
        } finally {
            restore(previous);
        }
    }

    private static void restore(String previous) {
        if (previous == null) {
            System.clearProperty(Peers.KEEP_ALIVE_TIME_PROPERTY);
        } else {
            System.setProperty(Peers.KEEP_ALIVE_TIME_PROPERTY, previous);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        clientPorts.add(exchange.getRemoteAddress().getPort());
        int status = statuses.isEmpty() ? 200 : statuses.remove(0);
        if (clientPorts.size() == closeAfterRequest.get()) {
            exchange.getResponseHeaders().add("Connection", "close");
        }
        byte[] body = gzip("{\"ok\":true}\n");
        exchange.getResponseHeaders().add("Content-Encoding", "gzip");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static byte[] gzip(String text) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    private static JsonObject request() {
        JsonObject request = new JsonObject();
        request.addProperty("requestType", "getInfo");
        return JSON.getAsJsonObject(JSON.prepareRequest(request));
    }

    private static void assertOk(JsonObject response) {
        assertTrue(response != null && response.has("ok"), "unexpected response " + response);
    }
}
