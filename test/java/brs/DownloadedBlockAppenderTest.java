package brs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import brs.BlockchainProcessor.BlockOutOfOrderException;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.peer.Peer;
import brs.services.BlockService;
import brs.util.DownloadCacheImpl;

class DownloadedBlockAppenderTest {

    private DownloadCacheImpl downloadCache;
    private Blockchain blockchain;
    private BlockService blockService;
    private FluxCapacitor fluxCapacitor;
    private ReentrantReadWriteLock blockImporterLock;
    private final List<Block> parsed = new ArrayList<>();
    private final AtomicLong cacheLastBlockId = new AtomicLong(10);
    private Long failingBlockId;

    private Peer peerA;
    private Peer peerB;
    private Block commonBlock;

    @BeforeEach
    void setUp() {
        downloadCache = mock(DownloadCacheImpl.class);
        blockchain = mock(Blockchain.class);
        blockService = mock(BlockService.class);
        fluxCapacitor = mock(FluxCapacitor.class);
        when(fluxCapacitor.getValue(eq(FluxValues.POC_PLUS), anyInt())).thenReturn(false);
        blockImporterLock = new ReentrantReadWriteLock();
        when(downloadCache.getLastBlockId()).thenAnswer(invocation -> cacheLastBlockId.get());
        when(downloadCache.addBlock(any())).thenAnswer(invocation -> {
            cacheLastBlockId.set(((Block) invocation.getArgument(0)).getId());
            return true;
        });

        peerA = mock(Peer.class);
        peerB = mock(Peer.class);
        commonBlock = mock(Block.class);
        when(commonBlock.getId()).thenReturn(10L);
        when(commonBlock.getHeight()).thenReturn(1000);
    }

    @Test
    void append_GivenTwoLinkedSegments_AddsAllBlocksWithTheirSegmentPeer() {
        // Arrange
        List<ParallelBlockFetcher.Segment> segments = List.of(
                segment(peerA, 1000, 10, 11, 12),
                segment(peerB, 1002, 12, 13, 14));

        // Act
        DownloadedBlockAppender.Result result = appender(true).append(segments, commonBlock, true);

        // Assert
        assertFalse(result.stopped());
        assertEquals(4, parsed.size());
        assertSame(parsed.get(3), result.lastBlock());
        verify(parsed.get(0)).setPeer(peerA);
        verify(parsed.get(1)).setPeer(peerA);
        verify(parsed.get(2)).setPeer(peerB);
        verify(parsed.get(3)).setPeer(peerB);
        InOrder order = inOrder(downloadCache);
        parsed.forEach(block -> order.verify(downloadCache).addBlock(block));
    }

    @Test
    void append_GivenSegmentThatDoesNotLink_KeepsEarlierBlocksAndBlacklistsNobody() {
        List<ParallelBlockFetcher.Segment> segments = List.of(
                segment(peerA, 1000, 10, 11, 12),
                segment(peerB, 1002, 99, 13, 14));

        DownloadedBlockAppender.Result result = appender(true).append(segments, commonBlock, true);

        assertTrue(result.stopped());
        verify(downloadCache, times(2)).addBlock(any());
        verify(peerB, never()).blacklist(anyString());
        verify(peerB, never()).blacklist(any(Exception.class), anyString());
    }

    @Test
    void append_GivenHelperSegmentThatDoesNotLink_ReportsTheHelper() {
        List<ParallelBlockFetcher.Segment> segments = List.of(
                segment(peerA, 1000, 10, 11, 12),
                segment(peerB, 1002, 99, 13, 14));

        DownloadedBlockAppender.Result result = appender(true).append(segments, commonBlock, true);

        assertSame(peerB, result.unlinkedHelper());
    }

    @Test
    void append_GivenHelperSegmentBrokenInside_ReportsTheHelper() {
        // segment 1 starts correctly but its second block does not follow its first
        JsonArray blocks = segment(peerB, 1002, 12, 13).blocks();
        blocks.addAll(segment(peerB, 1003, 77, 14).blocks());
        List<ParallelBlockFetcher.Segment> segments = List.of(
                segment(peerA, 1000, 10, 11, 12),
                new ParallelBlockFetcher.Segment(peerB, 1002, blocks));

        DownloadedBlockAppender.Result result = appender(true).append(segments, commonBlock, true);

        assertTrue(result.stopped());
        assertSame(peerB, result.unlinkedHelper());
        verify(downloadCache, times(3)).addBlock(any());
    }

    @Test
    void append_GivenFeederSegmentThatDoesNotLink_ReportsNoHelper() {
        DownloadedBlockAppender.Result result = appender(true).append(
                List.of(segment(peerA, 1000, 99, 11, 12)), commonBlock, true);

        assertTrue(result.stopped());
        assertNull(result.unlinkedHelper());
    }

    @Test
    void append_GivenLinkedSegments_ReportsNoHelper() {
        DownloadedBlockAppender.Result result = appender(true).append(
                List.of(segment(peerA, 1000, 10, 11, 12), segment(peerB, 1002, 12, 13, 14)), commonBlock, true);

        assertNull(result.unlinkedHelper());
    }

    @Test
    void append_GivenParseError_BlacklistsPeerOfThatSegment() {
        failingBlockId = 13L;
        List<ParallelBlockFetcher.Segment> segments = List.of(
                segment(peerA, 1000, 10, 11, 12),
                segment(peerB, 1002, 12, 13, 14));

        DownloadedBlockAppender.Result result = appender(true).append(segments, commonBlock, true);

        assertTrue(result.stopped());
        verify(peerB).blacklist(any(Exception.class), anyString());
        verify(peerA, never()).blacklist(any(Exception.class), anyString());
        verify(downloadCache, times(2)).addBlock(any());
    }

    @Test
    void append_GivenBlockOutOfOrder_ResetsCacheAndStops() throws Exception {
        doThrow(new BlockOutOfOrderException("out of order")).when(blockService).calculateBaseTarget(any(), any());

        DownloadedBlockAppender.Result result = appender(true).append(
                List.of(segment(peerA, 1000, 10, 11, 12)), commonBlock, true);

        assertTrue(result.stopped());
        verify(downloadCache).resetCache();
        verify(downloadCache, never()).addBlock(any());
    }

    @Test
    void append_GivenLockedCache_Stops() {
        doReturn(false).when(downloadCache).addBlock(any());

        DownloadedBlockAppender.Result result = appender(true).append(
                List.of(segment(peerA, 1000, 10, 11, 12)), commonBlock, true);

        assertTrue(result.stopped());
        verify(downloadCache, times(1)).addBlock(any());
    }

    @Test
    void append_GivenForkRound_AddsForkBlocks() {
        DownloadedBlockAppender.Result result = appender(true).append(
                List.of(segment(peerA, 1000, 10, 11, 12)), commonBlock, false);

        assertFalse(result.stopped());
        verify(downloadCache).addForkBlock(parsed.get(0));
        verify(downloadCache).addForkBlock(parsed.get(1));
        verify(downloadCache, never()).addBlock(any());
    }

    @Test
    void append_GivenDownloadWindowReached_StopsIteratingWithoutStoppingRound() {
        // Arrange: block 1001 is still in the window, block 1002 reaches MAX_ROLLBACK ahead of the chain
        when(fluxCapacitor.getValue(eq(FluxValues.POC_PLUS), anyInt())).thenReturn(true);
        when(blockchain.getHeight()).thenReturn(1002 - Constants.MAX_ROLLBACK);

        // Act
        DownloadedBlockAppender.Result result = appender(true).append(
                List.of(segment(peerA, 1000, 10, 11, 12)), commonBlock, true);

        // Assert
        assertFalse(result.stopped());
        verify(downloadCache, times(1)).addBlock(any());
        assertSame(parsed.get(0), result.lastBlock());
    }

    @Test
    void append_GivenNonDecoupledMode_TakesImporterReadLockPerBlock() throws Exception {
        AtomicInteger readLocksDuringBaseTarget = new AtomicInteger(-1);
        doAnswer(invocation -> {
            readLocksDuringBaseTarget.set(blockImporterLock.getReadLockCount());
            return null;
        }).when(blockService).calculateBaseTarget(any(), any());

        appender(false).append(List.of(segment(peerA, 1000, 10, 11)), commonBlock, true);

        assertEquals(1, readLocksDuringBaseTarget.get());
        assertEquals(0, blockImporterLock.getReadLockCount());
    }

    @Test
    void append_GivenDecoupledMode_DoesNotTakeImporterReadLock() throws Exception {
        AtomicInteger readLocksDuringBaseTarget = new AtomicInteger(-1);
        doAnswer(invocation -> {
            readLocksDuringBaseTarget.set(blockImporterLock.getReadLockCount());
            return null;
        }).when(blockService).calculateBaseTarget(any(), any());

        appender(true).append(List.of(segment(peerA, 1000, 10, 11)), commonBlock, true);

        assertEquals(0, readLocksDuringBaseTarget.get());
    }

    private DownloadedBlockAppender appender(boolean decoupledSync) {
        return new DownloadedBlockAppender(downloadCache, blockchain, blockService, () -> fluxCapacitor,
                this::parse, blockImporterLock, decoupledSync);
    }

    private Block parse(JsonObject blockData, int height) throws SignumException.ValidationException {
        long id = blockData.get("id").getAsLong();
        if (failingBlockId != null && failingBlockId == id) {
            throw new SignumException.NotValidException("bad block " + id);
        }
        Block block = mock(Block.class);
        when(block.getId()).thenReturn(id);
        when(block.getPreviousBlockId()).thenReturn(blockData.get("prev").getAsLong());
        when(block.getHeight()).thenReturn(height);
        parsed.add(block);
        return block;
    }

    /** A segment whose blocks chain from {@code previousId} through the given ids. */
    private static ParallelBlockFetcher.Segment segment(Peer peer, int fromHeight, long previousId, long... ids) {
        JsonArray blocks = new JsonArray();
        long prev = previousId;
        for (long id : ids) {
            JsonObject block = new JsonObject();
            block.addProperty("id", id);
            block.addProperty("prev", prev);
            blocks.add(block);
            prev = id;
        }
        return new ParallelBlockFetcher.Segment(peer, fromHeight, blocks);
    }
}
