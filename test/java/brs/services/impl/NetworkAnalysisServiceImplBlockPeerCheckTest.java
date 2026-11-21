package brs.services.impl;

import brs.Block;
import brs.Blockchain;
import brs.peer.Peer;
import brs.props.PropertyService;
import brs.services.BlockPeerCheck;
import brs.services.BlockPeerCheck.Outcome;
import brs.services.impl.PeerBlockProbe.ProbeResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NetworkAnalysisServiceImplBlockPeerCheckTest {

    private static final int TIP = 1_000;
    private static final long BLOCK_ID = 77L;
    private static final long OTHER_BLOCK_ID = 78L;
    private static final ProbeResult HAS = new ProbeResult(true, false, null, 5L, 1L);
    private static final ProbeResult LACKS = new ProbeResult(false, false, "9", 5L, 1L);

    private Blockchain blockchain;
    private ExecutorService probePool;
    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private final List<Peer> peers = new ArrayList<>();
    private final Map<Peer, ProbeResult> answers = new ConcurrentHashMap<>();
    private final Map<Peer, AtomicInteger> probeCalls = new ConcurrentHashMap<>();
    private CountDownLatch hold;
    private long probeTimeoutMs = 5_000;

    @BeforeEach
    void setUp() {
        blockchain = mock(Blockchain.class);
        when(blockchain.getHeight()).thenReturn(TIP);
        stubBlock(BLOCK_ID, TIP - 10);
        stubBlock(OTHER_BLOCK_ID, TIP - 20);
        probePool = Executors.newFixedThreadPool(8, r -> new Thread(r, "test-probe"));
    }

    @AfterEach
    void tearDown() {
        if (hold != null) hold.countDown();
        probePool.shutdownNow();
    }

    private void stubBlock(long id, int height) {
        Block block = mock(Block.class);
        when(block.getId()).thenReturn(id);
        when(block.getHeight()).thenReturn(height);
        when(blockchain.getBlock(id)).thenReturn(block);
    }

    private Peer peer(int height, ProbeResult answer) {
        Peer peer = mock(Peer.class);
        when(peer.getLastKnownHeight()).thenReturn(height);
        when(peer.getPeerAddress()).thenReturn("10.0.0." + peers.size());
        peers.add(peer);
        if (answer != null) answers.put(peer, answer);
        probeCalls.put(peer, new AtomicInteger());
        return peer;
    }

    private NetworkAnalysisServiceImpl service() {
        PeerBlockProbe probe = (p, blockId) -> {
            probeCalls.get(p).incrementAndGet();
            if (hold != null) {
                try {
                    hold.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            ProbeResult answer = answers.get(p);
            if (answer == null) throw new IllegalStateException("no answer");
            return answer;
        };
        return new NetworkAnalysisServiceImpl(blockchain, mock(PropertyService.class), () -> peers, probe,
                mock(ScheduledExecutorService.class), probePool, probeTimeoutMs, clock::get);
    }

    @Test
    void checkBlockPeers_GivenUnknownBlock_ReturnsUnknownBlock() {
        assertEquals(Outcome.UNKNOWN_BLOCK, service().checkBlockPeers(5L).outcome());
    }

    @Test
    void checkBlockPeers_GivenBlockOlderThan360_ReturnsTooOld() {
        stubBlock(1L, TIP - 361);
        assertEquals(Outcome.TOO_OLD, service().checkBlockPeers(1L).outcome());
    }

    @Test
    void checkBlockPeers_GivenBlockAt360BelowTip_Checks() {
        stubBlock(2L, TIP - 360);
        peer(TIP, HAS);
        assertEquals(Outcome.OK, service().checkBlockPeers(2L).outcome());
    }

    @Test
    void checkBlockPeers_GivenPeers_CountsEachState() {
        peer(TIP, HAS);
        peer(TIP, LACKS);
        peer(TIP - 11, LACKS);
        peer(TIP, null);

        BlockPeerCheck check = service().checkBlockPeers(BLOCK_ID);

        assertEquals(new BlockPeerCheck(Outcome.OK, BLOCK_ID, TIP - 10, 1, 1, 1, 1, 1_000_000L), check);
    }

    @Test
    void checkBlockPeers_SkipsBlacklistedAndUnknownHeightPeers() {
        peer(TIP, HAS);
        when(peer(TIP, HAS).isBlacklisted()).thenReturn(true);
        peer(-1, HAS);

        BlockPeerCheck check = service().checkBlockPeers(BLOCK_ID);

        assertEquals(1, check.hasIt() + check.missing() + check.notYet() + check.noAnswer());
    }

    @Test
    void checkBlockPeers_GivenHangingPeer_CountsNoAnswerAfterDeadline() {
        probeTimeoutMs = 200;
        hold = new CountDownLatch(1);
        peer(TIP, HAS);
        NetworkAnalysisServiceImpl service = service();

        BlockPeerCheck check = assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),
                () -> service.checkBlockPeers(BLOCK_ID));

        assertEquals(1, check.noAnswer());
    }

    @Test
    void checkBlockPeers_GivenSecondCallWithinTtl_ReturnsCachedWithoutProbing() {
        Peer peer = peer(TIP, HAS);
        NetworkAnalysisServiceImpl service = service();
        BlockPeerCheck first = service.checkBlockPeers(BLOCK_ID);
        clock.addAndGet(59_999);

        BlockPeerCheck second = service.checkBlockPeers(BLOCK_ID);

        assertEquals(first, second);
        assertEquals(1, probeCalls.get(peer).get());
    }

    @Test
    void checkBlockPeers_GivenSecondCallAfterTtl_ProbesAgain() {
        Peer peer = peer(TIP, HAS);
        NetworkAnalysisServiceImpl service = service();
        service.checkBlockPeers(BLOCK_ID);
        clock.addAndGet(60_001);

        BlockPeerCheck second = service.checkBlockPeers(BLOCK_ID);

        assertEquals(1_060_001L, second.checkedAt());
        assertEquals(2, probeCalls.get(peer).get());
    }

    @Test
    void checkBlockPeers_GivenConcurrentCallsForSameBlock_ProbesEachPeerOnce() throws Exception {
        hold = new CountDownLatch(1);
        Peer peer = peer(TIP, HAS);
        NetworkAnalysisServiceImpl service = service();
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<BlockPeerCheck> a = callers.submit(() -> service.checkBlockPeers(BLOCK_ID));
            Future<BlockPeerCheck> b = callers.submit(() -> service.checkBlockPeers(BLOCK_ID));
            Thread.sleep(200);
            hold.countDown();

            assertEquals(a.get(2, TimeUnit.SECONDS), b.get(2, TimeUnit.SECONDS));
            assertEquals(1, probeCalls.get(peer).get());
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    void checkBlockPeers_GivenRoundRunningForOtherBlock_ReturnsBusy() throws Exception {
        hold = new CountDownLatch(1);
        peer(TIP, HAS);
        NetworkAnalysisServiceImpl service = service();
        ExecutorService callers = Executors.newSingleThreadExecutor();
        try {
            Future<BlockPeerCheck> running = callers.submit(() -> service.checkBlockPeers(BLOCK_ID));
            Thread.sleep(200);

            BlockPeerCheck other = assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),
                    () -> service.checkBlockPeers(OTHER_BLOCK_ID));

            assertEquals(Outcome.BUSY, other.outcome());
            hold.countDown();
            assertEquals(Outcome.OK, running.get(2, TimeUnit.SECONDS).outcome());
        } finally {
            callers.shutdownNow();
        }
    }
}
