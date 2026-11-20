package brs.services.impl;

import brs.Block;
import brs.Blockchain;
import brs.peer.Peer;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.impl.PeerBlockProbe.ProbeResult;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetworkAnalysisServiceImplNetworkStatusTest {

    private static final int OUR_HEIGHT = 100;
    private static final ProbeResult HAS = new ProbeResult(true, 12L, 1L);
    private static final ProbeResult LACKS = new ProbeResult(false, 15L, 2L);

    private Blockchain blockchain;
    private ScheduledExecutorService scheduler;
    private long probeTimeoutMs = 5_000;
    private PeerBlockProbe probeOverride;
    private PropertyService propertyService;
    private final List<Peer> peers = new ArrayList<>();
    private final Map<Peer, ProbeResult> answers = new HashMap<>();
    private final Map<Peer, RuntimeException> failures = new HashMap<>();
    private final Map<Peer, Long> askedBlockIds = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        blockchain = mock(Blockchain.class);
        Block lastBlock = mock(Block.class);
        when(lastBlock.getHeight()).thenReturn(OUR_HEIGHT);
        when(lastBlock.getStringId()).thenReturn("tip");
        when(lastBlock.getId()).thenReturn(OUR_HEIGHT * 10L);
        when(lastBlock.getCumulativeDifficulty()).thenReturn(BigInteger.valueOf(1000));
        when(blockchain.getLastBlock()).thenReturn(lastBlock);
        when(blockchain.getBlockIdAtHeight(anyInt())).thenAnswer(inv -> inv.<Integer>getArgument(0) * 10L);

        propertyService = mock(PropertyService.class);
        when(propertyService.getInt(Props.WEB_UI_NETWORK_STATUS_INTERVAL_SECONDS)).thenReturn(120);
        scheduler = mock(ScheduledExecutorService.class);
    }

    private Peer peer(String address, int height, ProbeResult answer) {
        Peer peer = mock(Peer.class);
        when(peer.getAnnouncedAddress()).thenReturn(address);
        when(peer.getLastKnownHeight()).thenReturn(height);
        when(peer.getLastKnownCumulativeDifficulty()).thenReturn("1");
        peers.add(peer);
        answers.put(peer, answer);
        return peer;
    }

    private NetworkAnalysisServiceImpl service() {
        PeerBlockProbe probe = probeOverride != null ? probeOverride : (p, blockId) -> {
            askedBlockIds.put(p, blockId);
            if (failures.containsKey(p)) {
                throw failures.get(p);
            }
            return answers.get(p);
        };
        return new NetworkAnalysisServiceImpl(blockchain, propertyService, () -> peers, probe, scheduler, probeTimeoutMs);
    }

    private JsonObject networkStatus() {
        return service().getNetworkStatus();
    }

    private static JsonObject peerEntry(JsonObject status, String address) {
        for (JsonElement e : status.getAsJsonArray("peers")) {
            if (address.equals(e.getAsJsonObject().get("address").getAsString())) {
                return e.getAsJsonObject();
            }
        }
        throw new AssertionError("no entry for " + address);
    }

    private static String statusOf(JsonObject status, String address) {
        return peerEntry(status, address).get("status").getAsString();
    }

    @Test
    void getNetworkStatus_GivenOnePeerPerStatus_CountsEachAndComputesConsensus() {
        // Arrange
        peer("A", 100, HAS);
        peer("B", 98, HAS);
        peer("C", 101, HAS);
        peer("D", 100, LACKS);
        peer("E", 100, ProbeResult.noAnswer());

        // Act
        JsonObject status = networkStatus();

        // Assert
        assertEquals(1, status.get("onChainPeers").getAsInt());
        assertEquals(1, status.get("stalePeers").getAsInt());
        assertEquals(1, status.get("aheadPeers").getAsInt());
        assertEquals(1, status.get("forkingPeers").getAsInt());
        assertEquals(1, status.get("unknownPeers").getAsInt());
        assertEquals(5, status.get("totalPeers").getAsInt());
        assertEquals(75.0, status.get("consensusPercent").getAsDouble());
        assertEquals("on-chain", statusOf(status, "A"));
        assertEquals("stale", statusOf(status, "B"));
        assertEquals("ahead", statusOf(status, "C"));
        assertEquals("forking", statusOf(status, "D"));
        assertEquals("unknown", statusOf(status, "E"));
        assertTrue(peerEntry(status, "C").get("onOurChain").getAsBoolean());
        assertFalse(peerEntry(status, "D").get("onOurChain").getAsBoolean());
        assertFalse(peerEntry(status, "E").get("onOurChain").getAsBoolean());
    }

    @Test
    void getNetworkStatus_GivenPeersBehindAndAhead_AsksForOurBlockAtTheLowerHeight() {
        // Arrange
        Peer behind = peer("B", 98, HAS);
        Peer ahead = peer("C", 101, HAS);

        // Act
        networkStatus();

        // Assert: our block at the peer's height, or at our tip when the peer is ahead
        assertEquals(980L, askedBlockIds.get(behind));
        assertEquals(1000L, askedBlockIds.get(ahead));
    }

    @Test
    void getNetworkStatus_GivenPeerAheadLackingOurTip_ReportsForking() {
        // Arrange
        peer("C", 105, LACKS);

        // Act
        JsonObject status = networkStatus();

        // Assert
        assertEquals("forking", statusOf(status, "C"));
        assertEquals(1, status.get("forkingPeers").getAsInt());
        assertEquals(0.0, status.get("consensusPercent").getAsDouble());
    }

    @Test
    void getNetworkStatus_GivenProbeThrows_ReportsUnknownAndStillListsOthers() {
        // Arrange
        Peer failing = peer("A", 100, HAS);
        failures.put(failing, new IllegalStateException("connection reset"));
        peer("B", 100, HAS);

        // Act
        JsonObject status = networkStatus();

        // Assert
        assertEquals("unknown", statusOf(status, "A"));
        assertEquals("on-chain", statusOf(status, "B"));
        assertEquals(100.0, status.get("consensusPercent").getAsDouble());
    }

    @Test
    void getNetworkStatus_GivenOnlyUnknownPeers_ReportsZeroConsensus() {
        // Arrange
        peer("A", 100, ProbeResult.noAnswer());
        peer("B", 99, ProbeResult.noAnswer());

        // Act
        JsonObject status = networkStatus();

        // Assert
        assertEquals(0.0, status.get("consensusPercent").getAsDouble());
        assertEquals(2, status.get("unknownPeers").getAsInt());
        assertEquals(2, status.get("totalPeers").getAsInt());
    }

    @Test
    void getNetworkStatus_GivenBlacklistedOrUnreportedPeers_LeavesThemOut() {
        // Arrange
        Peer blacklisted = peer("A", 100, HAS);
        when(blacklisted.isBlacklisted()).thenReturn(true);
        Peer unreported = peer("B", -1, HAS);
        peer("C", 100, HAS);

        // Act
        JsonObject status = networkStatus();

        // Assert
        assertEquals(1, status.get("totalPeers").getAsInt());
        assertEquals("on-chain", statusOf(status, "C"));
        assertFalse(askedBlockIds.containsKey(blacklisted));
        assertFalse(askedBlockIds.containsKey(unreported));
    }

    @Test
    void getNetworkStatus_GivenAnsweredAndUnansweredPeers_ReportsLatencyAndCheckTime() {
        // Arrange
        peer("A", 100, HAS);
        peer("B", 100, ProbeResult.noAnswer());

        // Act
        JsonObject status = networkStatus();

        // Assert
        assertEquals(12L, peerEntry(status, "A").get("latencyMs").getAsLong());
        assertEquals(1L, peerEntry(status, "A").get("checkedAt").getAsLong());
        assertTrue(peerEntry(status, "B").get("latencyMs").isJsonNull());
        assertTrue(peerEntry(status, "B").get("checkedAt").isJsonNull());
    }

    @Test
    void getNetworkStatus_GivenProbeThatNeverReturns_ReportsUnknownAfterDeadline() {
        // Arrange: a peer that drips its answer forever must not freeze the snapshot
        CountDownLatch never = new CountDownLatch(1);
        Peer hanging = peer("A", 100, HAS);
        peer("B", 100, HAS);
        probeOverride = (p, blockId) -> {
            if (p == hanging) {
                try {
                    never.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return answers.get(p);
        };
        probeTimeoutMs = 200;

        try {
            // Act
            JsonObject status = assertTimeoutPreemptively(Duration.ofSeconds(5), this::networkStatus);

            // Assert
            assertEquals("unknown", statusOf(status, "A"));
            assertEquals("on-chain", statusOf(status, "B"));
        } finally {
            never.countDown();
        }
    }

    @Test
    void constructor_GivenWebUiDisabled_SchedulesNoNetworkStatusRefresh() {
        // Arrange
        when(propertyService.getBoolean(Props.WEB_UI_ENABLED)).thenReturn(false);

        // Act
        service();

        // Assert: no background probing for a snapshot nobody can read
        verify(scheduler, never()).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void constructor_GivenWebUiEnabled_SchedulesNetworkStatusRefresh() {
        // Arrange
        when(propertyService.getBoolean(Props.WEB_UI_ENABLED)).thenReturn(true);

        // Act
        service();

        // Assert
        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(30L), eq(120L), eq(TimeUnit.SECONDS));
    }

    @Test
    void getNetworkStatus_GivenBlockLookupFailsForOnePeer_ReportsItUnknownAndKeepsOthers() {
        // Arrange: e.g. the tip was popped between reading it and looking up a height
        when(blockchain.getBlockIdAtHeight(98)).thenThrow(new IllegalArgumentException("Invalid height 98"));
        peer("A", 100, HAS);
        peer("B", 98, HAS);

        // Act
        JsonObject status = networkStatus();

        // Assert
        assertEquals("on-chain", statusOf(status, "A"));
        assertEquals("unknown", statusOf(status, "B"));
    }

    @Test
    void getNetworkStatus_GivenPeerAtOrAboveOurHeight_AsksForOurTipWithoutLookup() {
        // Arrange
        when(blockchain.getBlockIdAtHeight(OUR_HEIGHT)).thenThrow(new IllegalArgumentException("Invalid height"));
        Peer same = peer("A", 100, HAS);
        Peer ahead = peer("C", 101, HAS);

        // Act
        JsonObject status = networkStatus();

        // Assert
        assertEquals(1000L, askedBlockIds.get(same));
        assertEquals(1000L, askedBlockIds.get(ahead));
        assertEquals("on-chain", statusOf(status, "A"));
        assertEquals("ahead", statusOf(status, "C"));
    }

    @Test
    void getNetworkStatus_GivenConcurrentFirstCalls_RunsOneCheckRound() throws Exception {
        // Arrange
        AtomicInteger probes = new AtomicInteger();
        Peer a = peer("A", 100, HAS);
        probeOverride = (p, blockId) -> {
            probes.incrementAndGet();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return answers.get(p);
        };
        NetworkAnalysisServiceImpl service = service();
        ExecutorService callers = Executors.newFixedThreadPool(2);

        try {
            // Act
            List<Future<JsonObject>> results = callers.invokeAll(List.of(service::getNetworkStatus, service::getNetworkStatus));

            // Assert
            assertEquals(1, probes.get());
            for (Future<JsonObject> result : results) {
                assertEquals("on-chain", statusOf(result.get(), "A"));
            }
        } finally {
            callers.shutdownNow();
        }
    }
}
