package brs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import brs.peer.Peer;

class ParallelBlockFetcherTest {

    private static final BigInteger OUR_DIFFICULTY = BigInteger.TEN;
    private static final BigInteger BETTER_DIFFICULTY = BigInteger.valueOf(100);
    private static final int BLOCKS_PER_PEER = 100;
    private static final int PEERS = 4;
    private static final long TIMEOUT_MS = 500;
    private static final long PROBE_TTL_MS = 60_000;

    private final List<Peer> candidates = new ArrayList<>();
    private final CountDownLatch neverReleased = new CountDownLatch(1);
    private final AtomicLong clock = new AtomicLong();
    private ParallelBlockFetcher fetcher;
    private Peer feeder;

    @BeforeEach
    void setUp() {
        fetcher = new ParallelBlockFetcher(() -> candidates, PEERS, BLOCKS_PER_PEER, TIMEOUT_MS);
        feeder = peer(BETTER_DIFFICULTY, 5000, requested -> blocks(requested));
    }

    @AfterEach
    void tearDown() {
        neverReleased.countDown();
        fetcher.shutdown();
        Thread.interrupted();
    }

    @Test
    void fetch_GivenThreeQualifiedHelpers_ReturnsFourSegmentsInHeightOrder() throws Exception {
        // Arrange
        addHelpers(3);

        // Act
        List<ParallelBlockFetcher.Segment> segments = fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE);

        // Assert
        assertEquals(List.of(1000, 1100, 1200, 1300), fromHeights(segments));
        assertSame(feeder, segments.get(0).peer());
        Set<Peer> peers = new HashSet<>();
        segments.forEach(segment -> peers.add(segment.peer()));
        assertEquals(4, peers.size());
        segments.forEach(segment -> assertEquals(BLOCKS_PER_PEER, segment.blocks().size()));
    }

    @Test
    void fetch_GivenResponsesArriveOutOfOrder_KeepsHeightOrder() throws Exception {
        feeder = peer(BETTER_DIFFICULTY, 5000, requested -> {
            sleep(100);
            return blocks(requested);
        });
        addHelpers(3);

        List<ParallelBlockFetcher.Segment> segments = fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE);

        assertEquals(List.of(1000, 1100, 1200, 1300), fromHeights(segments));
        assertSame(feeder, segments.get(0).peer());
    }

    @Test
    void fetch_GivenHelperWithLowerCumulativeDifficulty_CutsBeforeItsSegmentAndDoesNotRequestBlocks() throws Exception {
        List<Integer> requestedHeights = Collections.synchronizedList(new ArrayList<>());
        Peer weakHelper = peer(BigInteger.ONE, 5000, requested -> blocks(requested), requestedHeights);
        candidates.add(weakHelper);

        List<ParallelBlockFetcher.Segment> segments = fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE);

        assertEquals(List.of(1000), fromHeights(segments));
        assertTrue(requestedHeights.isEmpty());
        verify(weakHelper, never()).blacklist(anyString());
        verify(weakHelper, never()).blacklist(any(Exception.class), anyString());
    }

    @Test
    void fetch_GivenHelperBelowSegmentEnd_CutsBeforeItsSegment() throws Exception {
        List<Integer> requestedHeights = Collections.synchronizedList(new ArrayList<>());
        candidates.add(peer(BETTER_DIFFICULTY, 1150, requested -> blocks(requested), requestedHeights));

        List<ParallelBlockFetcher.Segment> segments = fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE);

        assertEquals(List.of(1000), fromHeights(segments));
        assertTrue(requestedHeights.isEmpty());
    }

    @Test
    void fetch_GivenNoCandidates_ReturnsEmptyAndSendsNothing() throws Exception {
        List<ParallelBlockFetcher.Segment> segments = fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE);

        assertTrue(segments.isEmpty());
        verify(feeder, never()).send(any());
    }

    @Test
    void fetch_GivenNullResponse_CutsBeforeTheSegment() throws Exception {
        assertCutAfterFeederSegmentFor(null);
    }

    @Test
    void fetch_GivenErrorResponse_CutsBeforeTheSegment() throws Exception {
        JsonObject error = new JsonObject();
        error.addProperty("error", "x");
        assertCutAfterFeederSegmentFor(error);
    }

    @Test
    void fetch_GivenEmptySegment_CutsBeforeIt() throws Exception {
        JsonObject empty = new JsonObject();
        empty.add("nextBlocks", new JsonArray());
        assertCutAfterFeederSegmentFor(empty);
    }

    @Test
    void fetch_GivenShortSegmentFollowedByFullOne_CutsAfterTheShortOne() throws Exception {
        // whichever helper gets segment 1 (after height 1100) sends a short segment
        for (int i = 0; i < 2; i++) {
            candidates.add(peerByHeight(BETTER_DIFFICULTY, 5000,
                    (height, requested) -> blocks(height == 1100 ? 40 : requested)));
        }
        fetcher = new ParallelBlockFetcher(() -> candidates, 3, BLOCKS_PER_PEER, TIMEOUT_MS);

        List<ParallelBlockFetcher.Segment> segments = fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE);

        assertEquals(2, segments.size());
        assertEquals(40, segments.get(1).blocks().size());
    }

    @Test
    void fetch_GivenTooManyBlocks_BlacklistsPeerAndCutsBeforeIt() throws Exception {
        Peer greedy = peer(BETTER_DIFFICULTY, 5000, requested -> blocks(requested + 1));
        candidates.add(greedy);

        List<ParallelBlockFetcher.Segment> segments = fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE);

        assertEquals(List.of(1000), fromHeights(segments));
        verify(greedy).blacklist(anyString());
    }

    @Test
    void fetch_GivenMaxBlocksBelowRound_LimitsSegments() throws Exception {
        List<Integer> requestedCounts = Collections.synchronizedList(new ArrayList<>());
        addHelpers(3, requestedCounts);

        List<ParallelBlockFetcher.Segment> segments = fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, 250);

        assertEquals(List.of(1000, 1100, 1200), fromHeights(segments));
        assertEquals(50, segments.get(2).blocks().size());
        assertTrue(requestedCounts.contains(50));
    }

    @Test
    void fetch_GivenFeederHeightBelowRound_LimitsSegmentsToFeederHeight() throws Exception {
        addHelpers(3);

        List<ParallelBlockFetcher.Segment> segments = fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 1220, Integer.MAX_VALUE);

        assertEquals(List.of(1000, 1100, 1200), fromHeights(segments));
        assertEquals(20, segments.get(2).blocks().size());
    }

    @Test
    void fetch_GivenMaxBlocksZero_ReturnsEmptyAndSendsNothing() throws Exception {
        addHelpers(3);

        List<ParallelBlockFetcher.Segment> segments = fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, 0);

        assertTrue(segments.isEmpty());
        verify(feeder, never()).send(any());
    }

    @Test
    void fetch_GivenHelperThatNeverAnswers_ReturnsWithinTimeout() throws Exception {
        candidates.add(peer(BETTER_DIFFICULTY, 5000, requested -> {
            awaitForever();
            return blocks(requested);
        }));

        long start = System.nanoTime();
        List<ParallelBlockFetcher.Segment> segments = fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(List.of(1000), fromHeights(segments));
        assertTrue(elapsedMs < 1000, "took " + elapsedMs + " ms");
    }

    @Test
    void fetch_GivenThreadsStillBusyFromPreviousRound_NextRoundReturnsWithinTimeout() throws Exception {
        // Arrange: every helper hangs, so after the first round all helper threads stay busy
        for (int i = 0; i < 3; i++) {
            candidates.add(peer(BETTER_DIFFICULTY, 5000, requested -> {
                awaitForever();
                return blocks(requested);
            }));
        }
        fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE);

        // Act
        long start = System.nanoTime();
        fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // Assert
        assertTrue(elapsedMs < 1000, "took " + elapsedMs + " ms");
    }

    @Test
    void fetch_GivenAllPoolThreadsHeldByHungHelpers_StillReturnsFeederSegment() throws Exception {
        // Arrange: every helper hangs; after a few rounds every pool thread is held by a hung request
        for (int i = 0; i < 3; i++) {
            candidates.add(peer(BETTER_DIFFICULTY, 5000, requested -> {
                awaitForever();
                return blocks(requested);
            }));
        }
        fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE);
        fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE);

        // Act
        List<ParallelBlockFetcher.Segment> segments = fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE);

        // Assert
        assertEquals(List.of(1000), fromHeights(segments));
        assertSame(feeder, segments.get(0).peer());
    }

    @Test
    void fetch_GivenUnqualifiedHelpers_QualifiedHelpersStillFillConsecutiveSegments() throws Exception {
        // Arrange: two of three helpers do not qualify; whatever their order, the one that does takes segment 1
        candidates.add(peer(BigInteger.ONE, 5000, requested -> blocks(requested)));
        candidates.add(peer(BETTER_DIFFICULTY, 900, requested -> blocks(requested)));
        Peer good = peer(BETTER_DIFFICULTY, 5000, requested -> blocks(requested));
        candidates.add(good);

        for (int round = 0; round < 5; round++) {
            // Act
            List<ParallelBlockFetcher.Segment> segments = fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000,
                    Integer.MAX_VALUE);

            // Assert
            assertEquals(List.of(1000, 1100), fromHeights(segments));
            assertSame(good, segments.get(1).peer());
        }
    }

    @Test
    void fetch_GivenHelperProbedInPreviousRound_DoesNotProbeAgainWithinTtl() throws Exception {
        // Arrange
        Peer helper = peer(BETTER_DIFFICULTY, 5000, requested -> blocks(requested));
        candidates.add(helper);
        fetcher = fetcherWithClock();
        fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE);
        clock.addAndGet(TimeUnit.SECONDS.toNanos(59));

        // Act
        List<ParallelBlockFetcher.Segment> segments = fetcher.fetch(feeder, OUR_DIFFICULTY, 1200, 5000,
                Integer.MAX_VALUE);

        // Assert
        assertEquals(List.of(1200, 1300), fromHeights(segments));
        verify(helper, times(1)).send(argThat(ParallelBlockFetcherTest::isProbe));
    }

    @Test
    void fetch_GivenProbeOlderThanTtl_ProbesAgain() throws Exception {
        Peer helper = peer(BETTER_DIFFICULTY, 5000, requested -> blocks(requested));
        candidates.add(helper);
        fetcher = fetcherWithClock();
        fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE);
        clock.addAndGet(TimeUnit.SECONDS.toNanos(61));

        fetcher.fetch(feeder, OUR_DIFFICULTY, 1200, 5000, Integer.MAX_VALUE);

        verify(helper, times(2)).send(argThat(ParallelBlockFetcherTest::isProbe));
    }

    @Test
    void fetch_GivenCachedHeightBelowNewRound_ProbesAgain() throws Exception {
        // the cached height (1300) covers the first round but not the second (1300..1500)
        List<Integer> heights = new ArrayList<>(List.of(1300, 5000));
        Peer helper = mock(Peer.class);
        when(helper.send(any())).thenAnswer(invocation -> {
            JsonObject request = invocation.getArgument(0);
            if (isProbe(request)) {
                return difficultyResponse(BETTER_DIFFICULTY, heights.remove(0));
            }
            JsonObject response = new JsonObject();
            response.add("nextBlocks", blocks(request.get("numBlocks").getAsInt()));
            return response;
        });
        candidates.add(helper);
        fetcher = fetcherWithClock();
        fetcher.fetch(feeder, OUR_DIFFICULTY, 1100, 5000, 200);

        List<ParallelBlockFetcher.Segment> segments = fetcher.fetch(feeder, OUR_DIFFICULTY, 1300, 5000, 200);

        assertEquals(List.of(1300, 1400), fromHeights(segments));
        verify(helper, times(2)).send(argThat(ParallelBlockFetcherTest::isProbe));
    }

    @Test
    void fetch_GivenHelperSegmentFailed_SkipsItInLaterRounds() throws Exception {
        Peer helper = mock(Peer.class);
        when(helper.send(any())).thenAnswer(invocation -> isProbe(invocation.getArgument(0))
                ? difficultyResponse(BETTER_DIFFICULTY, 5000)
                : null);
        candidates.add(helper);
        fetcher = fetcherWithClock();
        fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE);

        fetcher.fetch(feeder, OUR_DIFFICULTY, 1100, 5000, Integer.MAX_VALUE);

        verify(helper, times(1)).send(argThat(ParallelBlockFetcherTest::isProbe));
        verify(helper, times(2)).send(any());
    }

    @Test
    void fetch_GivenHelperThatMissedTheDeadline_SkipsItInLaterRounds() throws Exception {
        // Arrange: the hung helper answers its probe but never delivers its segment
        List<Integer> hungRequests = Collections.synchronizedList(new ArrayList<>());
        Peer hung = peer(BETTER_DIFFICULTY, 5000, requested -> {
            awaitForever();
            return blocks(requested);
        }, hungRequests);
        candidates.add(hung);
        fetcher = fetcherWithClock();
        fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE);

        // Act
        fetcher.fetch(feeder, OUR_DIFFICULTY, 1100, 5000, Integer.MAX_VALUE);

        // Assert
        assertEquals(1, hungRequests.size());
    }

    @Test
    void fetch_GivenHelperWhoseProbeFails_SkipsItInLaterRounds() throws Exception {
        Peer silent = mock(Peer.class);
        candidates.add(silent);
        fetcher = fetcherWithClock();
        fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE);

        fetcher.fetch(feeder, OUR_DIFFICULTY, 1100, 5000, Integer.MAX_VALUE);

        verify(silent, times(1)).send(any());
    }

    @Test
    void fetch_GivenSkippedHelperAfterTtl_UsesItAgain() throws Exception {
        Peer silent = mock(Peer.class);
        candidates.add(silent);
        fetcher = fetcherWithClock();
        fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE);
        clock.addAndGet(TimeUnit.SECONDS.toNanos(61));

        fetcher.fetch(feeder, OUR_DIFFICULTY, 1100, 5000, Integer.MAX_VALUE);

        verify(silent, times(2)).send(any());
    }

    @Test
    void fetch_GivenFeederSegmentArrived_WaitsForHelpersOnlyAGraceAfterIt() throws Exception {
        // Arrange: a long overall timeout, but helpers only get a short grace after the feeder's segment
        candidates.add(peer(BETTER_DIFFICULTY, 5000, requested -> {
            awaitForever();
            return blocks(requested);
        }));
        fetcher.shutdown();
        fetcher = new ParallelBlockFetcher(() -> candidates, PEERS, BLOCKS_PER_PEER, 10_000, PROBE_TTL_MS, 300,
                clock::get);

        // Act
        long start = System.nanoTime();
        List<ParallelBlockFetcher.Segment> segments = fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000,
                Integer.MAX_VALUE);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // Assert
        assertEquals(List.of(1000), fromHeights(segments));
        assertTrue(elapsedMs < 2000, "took " + elapsedMs + " ms");
    }

    @Test
    void fetch_GivenPeersAboveMaximum_UsesAtMostSixteenPeers() throws Exception {
        addHelpers(30);
        fetcher.shutdown();
        fetcher = new ParallelBlockFetcher(() -> candidates, 100, BLOCKS_PER_PEER, TIMEOUT_MS);

        List<ParallelBlockFetcher.Segment> segments = fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 100_000,
                Integer.MAX_VALUE);

        assertEquals(ParallelBlockFetcher.MAX_PEERS, segments.size());
        assertEquals(16, ParallelBlockFetcher.MAX_PEERS);
    }

    @Test
    void skipHelper_GivenHelperWithUnlinkedSegment_DoesNotPickItWithinTtl() throws Exception {
        // Arrange
        List<Integer> requests = Collections.synchronizedList(new ArrayList<>());
        Peer forked = peer(BETTER_DIFFICULTY, 5000, requested -> blocks(requested), requests);
        candidates.add(forked);
        fetcher = fetcherWithClock();
        fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE);

        // Act
        fetcher.skipHelper(forked);
        List<ParallelBlockFetcher.Segment> segments = fetcher.fetch(feeder, OUR_DIFFICULTY, 1100, 5000,
                Integer.MAX_VALUE);

        // Assert
        assertTrue(segments.isEmpty());
        assertEquals(1, requests.size());
    }

    @Test
    void fetch_GivenInterrupt_ThrowsInterruptedException() {
        addHelpers(3);
        Thread.currentThread().interrupt();

        assertThrows(InterruptedException.class,
                () -> fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE));
    }

    @Test
    void isApplicable_GivenForkOrSmallGapOrSinglePeer_ReturnsFalse() {
        assertFalse(fetcher.isApplicable(false, 1000, 5000));
        assertFalse(fetcher.isApplicable(true, 1000, 1199));
        ParallelBlockFetcher singlePeer = new ParallelBlockFetcher(() -> candidates, 1, BLOCKS_PER_PEER, TIMEOUT_MS);
        assertFalse(singlePeer.isApplicable(true, 1000, 5000));
        singlePeer.shutdown();
    }

    @Test
    void isApplicable_GivenLinearExtensionWithLargeGap_ReturnsTrue() {
        assertTrue(fetcher.isApplicable(true, 1000, 1200));
    }

    @Test
    void maxBlocksAhead_GivenPocPlus_ReturnsHalfRollbackWindow() {
        assertEquals(220, ParallelBlockFetcher.maxBlocksAhead(true, 1000, 1500));
        assertEquals(Integer.MAX_VALUE, ParallelBlockFetcher.maxBlocksAhead(false, 1000, 1500));
    }

    private ParallelBlockFetcher fetcherWithClock() {
        fetcher.shutdown();
        return new ParallelBlockFetcher(() -> candidates, PEERS, BLOCKS_PER_PEER, TIMEOUT_MS, PROBE_TTL_MS,
                TIMEOUT_MS, clock::get);
    }

    private static boolean isProbe(JsonElement request) {
        return request != null && "getCumulativeDifficulty".equals(
                request.getAsJsonObject().get("requestType").getAsString());
    }

    private void assertCutAfterFeederSegmentFor(JsonObject helperResponse) throws Exception {
        Peer helper = mock(Peer.class);
        when(helper.send(any())).thenAnswer(invocation -> {
            JsonObject request = invocation.getArgument(0);
            if ("getCumulativeDifficulty".equals(request.get("requestType").getAsString())) {
                return difficultyResponse(BETTER_DIFFICULTY, 5000);
            }
            return helperResponse;
        });
        candidates.add(helper);

        List<ParallelBlockFetcher.Segment> segments = fetcher.fetch(feeder, OUR_DIFFICULTY, 1000, 5000, Integer.MAX_VALUE);

        assertEquals(List.of(1000), fromHeights(segments));
        verify(helper, never()).blacklist(anyString());
        verify(helper, never()).blacklist(any(Exception.class), anyString());
    }

    private void addHelpers(int count) {
        addHelpers(count, Collections.synchronizedList(new ArrayList<>()));
    }

    private void addHelpers(int count, List<Integer> requestedCounts) {
        for (int i = 0; i < count; i++) {
            candidates.add(peer(BETTER_DIFFICULTY, 5000, requested -> {
                requestedCounts.add(requested);
                return blocks(requested);
            }));
        }
    }

    private static Peer peer(BigInteger difficulty, int height, Function<Integer, JsonArray> blocksForCount) {
        return peer(difficulty, height, blocksForCount, Collections.synchronizedList(new ArrayList<>()));
    }

    private static Peer peer(BigInteger difficulty, int height, Function<Integer, JsonArray> blocksForCount,
            List<Integer> requestedHeights) {
        return peerByHeight(difficulty, height, (fromHeight, requested) -> {
            requestedHeights.add(fromHeight);
            return blocksForCount.apply(requested);
        });
    }

    private static Peer peerByHeight(BigInteger difficulty, int height,
            BiFunction<Integer, Integer, JsonArray> blocksForHeightAndCount) {
        Peer peer = mock(Peer.class);
        when(peer.send(any())).thenAnswer(invocation -> {
            JsonObject request = invocation.getArgument(0);
            String requestType = request.get("requestType").getAsString();
            if ("getCumulativeDifficulty".equals(requestType)) {
                return difficultyResponse(difficulty, height);
            }
            if ("getBlocksFromHeight".equals(requestType)) {
                JsonObject response = new JsonObject();
                response.add("nextBlocks", blocksForHeightAndCount.apply(request.get("height").getAsInt(),
                        request.get("numBlocks").getAsInt()));
                return response;
            }
            return null;
        });
        return peer;
    }

    private static JsonObject difficultyResponse(BigInteger difficulty, int height) {
        JsonObject response = new JsonObject();
        response.addProperty("cumulativeDifficulty", difficulty.toString());
        response.addProperty("blockchainHeight", height);
        return response;
    }

    private static JsonArray blocks(int count) {
        JsonArray blocks = new JsonArray();
        for (int i = 0; i < count; i++) {
            JsonObject block = new JsonObject();
            block.addProperty("index", i);
            blocks.add(block);
        }
        return blocks;
    }

    private static List<Integer> fromHeights(List<ParallelBlockFetcher.Segment> segments) {
        List<Integer> heights = new ArrayList<>();
        segments.forEach(segment -> heights.add(segment.fromHeight()));
        return heights;
    }

    /** Like a blocking socket read: ignores interrupts until the test releases it. */
    private void awaitForever() {
        while (neverReleased.getCount() > 0) {
            try {
                neverReleased.await();
            } catch (InterruptedException ignored) {
                // keep hanging
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
