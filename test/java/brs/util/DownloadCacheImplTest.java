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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
}
