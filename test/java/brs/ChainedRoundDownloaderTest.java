package brs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import brs.peer.Peer;
import brs.util.DownloadCacheImpl;

class ChainedRoundDownloaderTest {

    private static final BigInteger OUR_DIFFICULTY = BigInteger.TEN;
    private static final int FEEDER_HEIGHT = 100_000;

    private ParallelBlockFetcher fetcher;
    private DownloadedBlockAppender appender;
    private DownloadCacheImpl downloadCache;
    private final AtomicLong cacheTipId = new AtomicLong();
    private final AtomicBoolean shuttingDown = new AtomicBoolean();
    private int maxBlocksAhead = Integer.MAX_VALUE;
    private final AtomicInteger importerLocks = new AtomicInteger();
    private final AtomicInteger importerReleases = new AtomicInteger();

    private Peer feeder;
    private Peer helper;
    private ChainedRoundDownloader downloader;

    @BeforeEach
    void setUp() {
        fetcher = mock(ParallelBlockFetcher.class);
        appender = mock(DownloadedBlockAppender.class);
        downloadCache = mock(DownloadCacheImpl.class);
        when(fetcher.blocksPerPeer()).thenReturn(100);
        when(fetcher.isApplicable(anyBoolean(), anyInt(), anyInt())).thenReturn(true);
        when(downloadCache.getCumulativeDifficulty()).thenReturn(OUR_DIFFICULTY);
        when(downloadCache.getLastBlockId()).thenAnswer(invocation -> cacheTipId.get());
        feeder = mock(Peer.class);
        helper = mock(Peer.class);
        downloader = new ChainedRoundDownloader(fetcher, appender, downloadCache, height -> maxBlocksAhead,
                shuttingDown::get);
    }

    @Test
    void continueAfter_GivenCompleteRound_FetchesTheNextRoundFromTheNewTip() throws Exception {
        // Arrange
        Block tip = tipAt(1200);
        List<ParallelBlockFetcher.Segment> next = round(1200, 100, 100);
        when(fetcher.fetch(feeder, OUR_DIFFICULTY, 1200, FEEDER_HEIGHT, Integer.MAX_VALUE)).thenReturn(next);
        Block newTip = block(1400);
        when(appender.append(next, tip, true)).thenReturn(new DownloadedBlockAppender.Result(newTip, false));

        // Act
        DownloadedBlockAppender.Result result = continueAfter(round(1000, 100, 100), tip);

        // Assert
        verify(fetcher).fetch(feeder, OUR_DIFFICULTY, 1200, FEEDER_HEIGHT, Integer.MAX_VALUE);
        verify(appender).append(next, tip, true);
        assertSame(newTip, result.lastBlock());
        assertFalse(result.stopped());
    }

    @Test
    void continueAfter_GivenShortLastSegment_DoesNotChain() throws Exception {
        Block tip = tipAt(1140);

        DownloadedBlockAppender.Result result = continueAfter(round(1000, 100, 40), tip);

        assertNoChaining(result, tip);
    }

    @Test
    void continueAfter_GivenStoppedAppend_DoesNotChain() throws Exception {
        Block tip = tipAt(1200);
        DownloadedBlockAppender.Result stopped = new DownloadedBlockAppender.Result(tip, true);

        DownloadedBlockAppender.Result result = downloader.continueAfter(feeder, FEEDER_HEIGHT, round(1000, 100, 100),
                stopped, importerLocks::incrementAndGet, importerReleases::incrementAndGet);

        assertSame(stopped, result);
        verify(fetcher, never()).fetch(any(), any(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void continueAfter_GivenNotAllBlocksAppended_DoesNotChain() throws Exception {
        // the window check stopped appending at 1150 although the round went up to 1200
        Block tip = tipAt(1150);

        DownloadedBlockAppender.Result result = continueAfter(round(1000, 100, 100), tip);

        assertNoChaining(result, tip);
    }

    @Test
    void continueAfter_GivenCacheTipMoved_DoesNotChain() throws Exception {
        Block tip = tipAt(1200);
        cacheTipId.set(999_999L);

        DownloadedBlockAppender.Result result = continueAfter(round(1000, 100, 100), tip);

        assertNoChaining(result, tip);
    }

    @Test
    void continueAfter_GivenFullCache_DoesNotChain() throws Exception {
        Block tip = tipAt(1200);
        when(downloadCache.isFull()).thenReturn(true);

        DownloadedBlockAppender.Result result = continueAfter(round(1000, 100, 100), tip);

        assertNoChaining(result, tip);
    }

    @Test
    void continueAfter_GivenClosedWindow_DoesNotChain() throws Exception {
        Block tip = tipAt(1200);
        maxBlocksAhead = 0;

        DownloadedBlockAppender.Result result = continueAfter(round(1000, 100, 100), tip);

        assertNoChaining(result, tip);
    }

    @Test
    void continueAfter_GivenFeederNotFarEnoughAhead_DoesNotChain() throws Exception {
        Block tip = tipAt(1200);
        when(fetcher.isApplicable(true, 1200, FEEDER_HEIGHT)).thenReturn(false);

        DownloadedBlockAppender.Result result = continueAfter(round(1000, 100, 100), tip);

        assertNoChaining(result, tip);
    }

    @Test
    void continueAfter_GivenShutdown_DoesNotChain() throws Exception {
        Block tip = tipAt(1200);
        shuttingDown.set(true);

        DownloadedBlockAppender.Result result = continueAfter(round(1000, 100, 100), tip);

        assertNoChaining(result, tip);
    }

    @Test
    void continueAfter_GivenStopRequestedDuringTheChain_StopsBeforeTheNextRound() throws Exception {
        // Arrange: endless complete rounds; a pause or shutdown is requested during the first chained round
        endlessCompleteRounds();
        when(appender.append(any(), any(), eq(true))).thenAnswer(invocation -> {
            shuttingDown.set(true);
            return completeAppend(invocation.getArgument(0));
        });

        // Act
        continueAfter(round(1000, 100, 100), tipAt(1200));

        // Assert
        verify(fetcher, times(1)).fetch(eq(feeder), any(), anyInt(), eq(FEEDER_HEIGHT), anyInt());
    }

    @Test
    void continueAfter_GivenWindowClosingDuringTheChain_StopsBeforeTheNextRound() throws Exception {
        // Arrange: the window allows the first chained round, then closes
        endlessCompleteRounds();
        when(appender.append(any(), any(), eq(true))).thenAnswer(invocation -> {
            maxBlocksAhead = 0;
            return completeAppend(invocation.getArgument(0));
        });

        // Act
        continueAfter(round(1000, 100, 100), tipAt(1200));

        // Assert
        verify(fetcher, times(1)).fetch(eq(feeder), any(), anyInt(), eq(FEEDER_HEIGHT), anyInt());
    }

    @Test
    void continueAfter_GivenEndlessCompleteRounds_StopsAfterEightChainedRounds() throws Exception {
        // Arrange: every fetch returns a full round and every append completes it
        endlessCompleteRounds();
        when(appender.append(any(), any(), eq(true))).thenAnswer(invocation -> completeAppend(invocation.getArgument(0)));

        // Act
        DownloadedBlockAppender.Result result = continueAfter(round(1000, 100, 100), tipAt(1200));

        // Assert
        verify(fetcher, times(ChainedRoundDownloader.MAX_CHAINED_ROUNDS)).fetch(eq(feeder), any(), anyInt(),
                eq(FEEDER_HEIGHT), anyInt());
        assertEquals(8, ChainedRoundDownloader.MAX_CHAINED_ROUNDS);
        assertEquals(1200 + 8 * 200, result.lastBlock().getHeight());
    }

    @Test
    void continueAfter_GivenChainedRound_TakesAndReleasesTheImporterLockAroundEachAppend() throws Exception {
        Block tip = tipAt(1200);
        AtomicBoolean lockHeldDuringFetch = new AtomicBoolean();
        List<ParallelBlockFetcher.Segment> next = round(1200, 100, 100);
        when(fetcher.fetch(eq(feeder), any(), anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            lockHeldDuringFetch.compareAndSet(false, importerLocks.get() != importerReleases.get());
            return next;
        }).thenReturn(List.of());
        when(appender.append(next, tip, true)).thenAnswer(invocation -> {
            assertEquals(importerReleases.get() + 1, importerLocks.get());
            return new DownloadedBlockAppender.Result(tipAt(1400), false);
        });

        continueAfter(round(1000, 100, 100), tip);

        assertEquals(1, importerLocks.get());
        assertEquals(1, importerReleases.get());
        assertFalse(lockHeldDuringFetch.get());
    }

    @Test
    void continueAfter_GivenUnlinkedHelperInChainedRound_SkipsItAndStops() throws Exception {
        Block tip = tipAt(1200);
        List<ParallelBlockFetcher.Segment> next = round(1200, 100, 100);
        when(fetcher.fetch(eq(feeder), any(), anyInt(), anyInt(), anyInt())).thenReturn(next);
        Block partial = block(1300);
        when(appender.append(next, tip, true)).thenReturn(new DownloadedBlockAppender.Result(partial, true, helper));

        DownloadedBlockAppender.Result result = continueAfter(round(1000, 100, 100), tip);

        verify(fetcher).skipHelper(helper);
        assertTrue(result.stopped());
        verify(fetcher, times(1)).fetch(any(), any(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void continueAfter_GivenEmptyFetch_ReturnsThePreviousResult() throws Exception {
        Block tip = tipAt(1200);
        when(fetcher.fetch(eq(feeder), any(), anyInt(), anyInt(), anyInt())).thenReturn(List.of());
        DownloadedBlockAppender.Result previous = new DownloadedBlockAppender.Result(tip, false);

        DownloadedBlockAppender.Result result = downloader.continueAfter(feeder, FEEDER_HEIGHT, round(1000, 100, 100),
                previous, importerLocks::incrementAndGet, importerReleases::incrementAndGet);

        assertSame(previous, result);
        verify(appender, never()).append(any(), any(), anyBoolean());
        assertEquals(0, importerLocks.get());
    }

    private DownloadedBlockAppender.Result continueAfter(List<ParallelBlockFetcher.Segment> round, Block tip)
            throws InterruptedException {
        return downloader.continueAfter(feeder, FEEDER_HEIGHT, round, new DownloadedBlockAppender.Result(tip, false),
                importerLocks::incrementAndGet, importerReleases::incrementAndGet);
    }

    private void endlessCompleteRounds() throws InterruptedException {
        when(fetcher.fetch(eq(feeder), any(), anyInt(), eq(FEEDER_HEIGHT), anyInt()))
                .thenAnswer(invocation -> round(invocation.getArgument(2), 100, 100));
    }

    /** The result of appending all blocks of the segments, with the cache tip at the last one. */
    private DownloadedBlockAppender.Result completeAppend(List<ParallelBlockFetcher.Segment> segments) {
        ParallelBlockFetcher.Segment last = segments.get(segments.size() - 1);
        return new DownloadedBlockAppender.Result(tipAt(last.fromHeight() + last.blocks().size()), false);
    }

    private void assertNoChaining(DownloadedBlockAppender.Result result, Block tip) throws InterruptedException {
        verify(fetcher, never()).fetch(any(), any(), anyInt(), anyInt(), anyInt());
        assertSame(tip, result.lastBlock());
        assertFalse(result.stopped());
    }

    /** A block at the given height that is also the download cache tip. */
    private Block tipAt(int height) {
        Block block = block(height);
        cacheTipId.set(height);
        return block;
    }

    private static Block block(int height) {
        Block block = mock(Block.class);
        when(block.getId()).thenReturn((long) height);
        when(block.getHeight()).thenReturn(height);
        return block;
    }

    /** Consecutive segments starting after {@code fromHeight}, with the given block counts. */
    private List<ParallelBlockFetcher.Segment> round(int fromHeight, int... counts) {
        Peer[] peers = {feeder, helper};
        List<ParallelBlockFetcher.Segment> segments = new java.util.ArrayList<>();
        int height = fromHeight;
        for (int i = 0; i < counts.length; i++) {
            JsonArray blocks = new JsonArray();
            for (int b = 0; b < counts[i]; b++) {
                blocks.add(new JsonObject());
            }
            segments.add(new ParallelBlockFetcher.Segment(peers[i % peers.length], height, blocks));
            height += counts[i];
        }
        return segments;
    }
}
