package brs.services.impl;

import brs.Block;
import brs.Blockchain;
import brs.peer.Peer;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.impl.PeerBlockProbe.ProbeResult;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetworkAnalysisServiceImplNetworkStatusTest {

    private static final int OUR_HEIGHT = 100;
    private static final long OUR_TIP = OUR_HEIGHT * 10L;
    /** The peer's tip is our tip. */
    private static final ProbeResult SAME_TIP = new ProbeResult(true, true, null, 12L, 1L);
    /** The peer has our tip and more blocks. */
    private static final ProbeResult BEYOND = new ProbeResult(true, false, null, 12L, 1L);
    /** The peer lacks our tip; its tip 980 is our block at height 90 (10 behind). */
    private static final ProbeResult BEHIND = new ProbeResult(false, false, "980", 15L, 2L);
    /** The peer lacks our tip; its tip 970 is our block at height 97 (3 behind). */
    private static final ProbeResult SLIGHTLY_BEHIND = new ProbeResult(false, false, "970", 15L, 2L);
    /** The peer lacks our tip; its tip 960 is our block at height 96 (4 behind). */
    private static final ProbeResult FOUR_BEHIND = new ProbeResult(false, false, "960", 15L, 2L);
    /** The peer lacks our tip; its tip 999 is a block we lack. */
    private static final ProbeResult FORKED = new ProbeResult(false, false, "999", 15L, 2L);

    private Blockchain blockchain;
    private ScheduledExecutorService scheduler;
    private ExecutorService probePool;
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
        when(lastBlock.getId()).thenReturn(OUR_TIP);
        when(lastBlock.getCumulativeDifficulty()).thenReturn(BigInteger.valueOf(1000));
        when(blockchain.getLastBlock()).thenReturn(lastBlock);
        ourBlock(980L, 90);
        ourBlock(970L, 97);
        ourBlock(960L, 96);

        propertyService = mock(PropertyService.class);
        when(propertyService.getInt(Props.WEB_UI_NETWORK_STATUS_INTERVAL_SECONDS)).thenReturn(120);
        scheduler = mock(ScheduledExecutorService.class);
        probePool = Executors.newFixedThreadPool(4, r -> new Thread(r, "test-probe"));
    }

    @AfterEach
    void tearDown() {
        probePool.shutdownNow();
    }

    private void ourBlock(long id, int height) {
        Block block = mock(Block.class);
        when(block.getHeight()).thenReturn(height);
        when(blockchain.getBlock(id)).thenReturn(block);
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
        return new NetworkAnalysisServiceImpl(blockchain, propertyService, () -> peers, probe, scheduler, probePool, probeTimeoutMs);
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
        peer("A", 100, SAME_TIP);
        peer("B", 98, BEHIND);
        peer("C", 101, BEYOND);
        peer("D", 100, FORKED);
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
    void getNetworkStatus_GivenPeersAFewBlocksBehind_CountsStaleOnlyFromFourBlocks() {
        // Arrange: up to 3 blocks behind is propagation delay
        peer("A", 97, SLIGHTLY_BEHIND);
        peer("B", 96, FOUR_BEHIND);

        // Act
        JsonObject status = networkStatus();

        // Assert
        assertEquals("on-chain", statusOf(status, "A"));
        assertEquals("stale", statusOf(status, "B"));
        assertEquals(1, status.get("onChainPeers").getAsInt());
        assertEquals(1, status.get("stalePeers").getAsInt());
    }

    @Test
    void getNetworkStatus_GivenPeersAtAnyHeight_AsksEachForOurTip() {
        // Arrange
        Peer behind = peer("B", 98, BEHIND);
        Peer ahead = peer("C", 101, BEYOND);

        // Act
        networkStatus();

        // Assert: the recorded height does not decide which block is asked for
        assertEquals(OUR_TIP, askedBlockIds.get(behind));
        assertEquals(OUR_TIP, askedBlockIds.get(ahead));
    }

    @Test
    void getNetworkStatus_GivenPeerThatForkedAboveItsRecordedHeight_ReportsForking() {
        // Arrange: recorded height 98 is old; the peer has since moved to another chain
        peer("B", 98, FORKED);

        // Act
        JsonObject status = networkStatus();

        // Assert: asking for our block at 98 would have said "has it" and counted it as stale
        assertEquals("forking", statusOf(status, "B"));
        assertEquals(0.0, status.get("consensusPercent").getAsDouble());
    }

    @Test
    void getNetworkStatus_GivenPeerAheadLackingOurTip_ReportsForking() {
        // Arrange
        peer("C", 105, FORKED);

        // Act
        JsonObject status = networkStatus();

        // Assert
        assertEquals("forking", statusOf(status, "C"));
        assertEquals(1, status.get("forkingPeers").getAsInt());
    }

    @Test
    void getNetworkStatus_GivenProbeThrows_ReportsUnknownAndStillListsOthers() {
        // Arrange
        Peer failing = peer("A", 100, SAME_TIP);
        failures.put(failing, new IllegalStateException("connection reset"));
        peer("B", 100, SAME_TIP);

        // Act
        JsonObject status = networkStatus();

        // Assert
        assertEquals("unknown", statusOf(status, "A"));
        assertEquals("on-chain", statusOf(status, "B"));
        assertEquals(100.0, status.get("consensusPercent").getAsDouble());
    }

    @Test
    void getNetworkStatus_GivenBlockLookupThrows_ReportsThatPeerUnknownAndKeepsOthers() {
        // Arrange: e.g. a database error while looking up the peer's tip
        when(blockchain.getBlock(980L)).thenThrow(new IllegalStateException("db closed"));
        peer("A", 100, SAME_TIP);
        peer("B", 98, BEHIND);

        // Act
        JsonObject status = networkStatus();

        // Assert
        assertEquals("on-chain", statusOf(status, "A"));
        assertEquals("unknown", statusOf(status, "B"));
    }

    @Test
    void getNetworkStatus_GivenPeerTipIdThatIsNoNumber_ReportsUnknown() {
        // Arrange
        peer("B", 98, new ProbeResult(false, false, "not-an-id", 15L, 2L));

        // Act
        JsonObject status = networkStatus();

        // Assert
        assertEquals("unknown", statusOf(status, "B"));
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
        Peer blacklisted = peer("A", 100, SAME_TIP);
        when(blacklisted.isBlacklisted()).thenReturn(true);
        Peer unreported = peer("B", -1, SAME_TIP);
        peer("C", 100, SAME_TIP);

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
        peer("A", 100, SAME_TIP);
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
        Peer hanging = peer("A", 100, SAME_TIP);
        peer("B", 100, SAME_TIP);
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
    void getNetworkStatus_GivenConcurrentFirstCalls_RunsOneCheckRound() throws Exception {
        // Arrange
        AtomicInteger probes = new AtomicInteger();
        peer("A", 100, SAME_TIP);
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

    @Test
    void getNetworkStatus_GivenInjectedProbePool_RunsChecksOnIt() {
        // Arrange
        Map<Peer, String> threads = new ConcurrentHashMap<>();
        peer("A", 100, SAME_TIP);
        probeOverride = (p, blockId) -> {
            threads.put(p, Thread.currentThread().getName());
            return answers.get(p);
        };

        // Act
        networkStatus();

        // Assert: the caller owns the pool and can shut it down
        assertEquals("test-probe", threads.get(peers.get(0)));
    }
}
