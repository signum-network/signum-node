package brs.util;

import brs.Block;
import brs.Blockchain;
import brs.fluxcapacitor.FluxCapacitor;
import brs.props.PropertyService;
import brs.props.Props;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class DownloadCacheImplTest {

    private DownloadCacheImpl downloadCache;

    @BeforeEach
    void setUp() {
        PropertyService propertyService = mock(PropertyService.class);
        when(propertyService.getInt(Props.BRS_BLOCK_CACHE_MB)).thenReturn(40);
        downloadCache = new DownloadCacheImpl(propertyService, mock(FluxCapacitor.class), mock(Blockchain.class));
    }

    private Block addBlock(long id) {
        Block block = mock(Block.class);
        when(block.getId()).thenReturn(id);
        when(block.getPreviousBlockId()).thenReturn(id - 1);
        when(block.getHeight()).thenReturn((int) id);
        when(block.getCumulativeDifficulty()).thenReturn(BigInteger.ONE);
        downloadCache.addBlock(block);
        return block;
    }

    @Test
    void takeUnverifiedBlocks_GivenMoreBlocksThanMax_ReturnsFirstBlocksInOrderAndRemovesThem() {
        // Arrange
        Block first = addBlock(1);
        Block second = addBlock(2);
        Block third = addBlock(3);

        // Act
        List<Block> taken = downloadCache.takeUnverifiedBlocks(2);

        // Assert
        assertEquals(List.of(first, second), taken);
        assertEquals(1, downloadCache.getUnverifiedSize());
        assertEquals(List.of(third), downloadCache.takeUnverifiedBlocks(2));
    }

    @Test
    void takeUnverifiedBlocks_GivenFewerBlocksThanMax_ReturnsAll() {
        // Arrange
        Block first = addBlock(1);

        // Act
        List<Block> taken = downloadCache.takeUnverifiedBlocks(16);

        // Assert
        assertEquals(List.of(first), taken);
        assertEquals(0, downloadCache.getUnverifiedSize());
    }

    @Test
    void takeUnverifiedBlocks_GivenEmptyQueue_ReturnsEmptyList() {
        // Act + Assert: unlike getFirstUnverifiedBlock, an empty queue is no error
        assertTrue(downloadCache.takeUnverifiedBlocks(16).isEmpty());
    }

    @Test
    void takeUnverifiedBlocks_KeepsBlocksInCache() {
        // Arrange
        Block block = addBlock(1);

        // Act
        downloadCache.takeUnverifiedBlocks(16);

        // Assert: only the verification queue shrinks, the importer still needs the block
        assertEquals(block, downloadCache.getBlock(1));
        assertEquals(1, downloadCache.size());
    }

    @Test
    void getLastBlock_GivenLastBlockRemovedConcurrently_NeverReturnsNull() throws Exception {
        // Arrange: the importer removes the cached tip while a block announce reads it.
        // Stub-only mocks: millions of calls would otherwise be recorded for verify() and fill the heap.
        PropertyService propertyService = stubOnly(PropertyService.class);
        when(propertyService.getInt(Props.BRS_BLOCK_CACHE_MB)).thenReturn(40);
        Blockchain blockchain = stubOnly(Blockchain.class);
        Block chainTip = stubOnly(Block.class);
        when(chainTip.getId()).thenReturn(1L);
        when(blockchain.getLastBlock()).thenReturn(chainTip);
        DownloadCacheImpl cache = new DownloadCacheImpl(propertyService, stubOnly(FluxCapacitor.class), blockchain);
        Block cached = stubOnly(Block.class);
        when(cached.getId()).thenReturn(2L);
        when(cached.getPreviousBlockId()).thenReturn(1L);
        when(cached.getHeight()).thenReturn(2);
        when(cached.getCumulativeDifficulty()).thenReturn(BigInteger.ONE);
        AtomicBoolean running = new AtomicBoolean(true);
        Thread importer = new Thread(() -> {
            while (running.get()) {
                cache.addBlock(cached);
                cache.removeBlock(cached);
            }
        });
        AtomicInteger nulls = new AtomicInteger();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500);

        // Act
        importer.start();
        try {
            while (System.nanoTime() < deadline) {
                if (cache.getLastBlock() == null) {
                    nulls.incrementAndGet();
                }
            }
        } finally {
            running.set(false);
            importer.join();
        }

        // Assert
        assertEquals(0, nulls.get());
        assertNotNull(cache.getLastBlock());
    }

    private static <T> T stubOnly(Class<T> type) {
        return mock(type, withSettings().stubOnly());
    }
}
