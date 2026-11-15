package brs;

import brs.BlockchainProcessor.BlockNotAcceptedException;
import brs.services.BlockService;
import brs.util.DownloadCacheImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncPreVerifierTest {

    private DownloadCacheImpl downloadCache;
    private Blockchain blockchain;
    private BlockService blockService;
    private BatchPocVerifier batchPocVerifier;
    private SyncPreVerifier syncPreVerifier;

    private Block tip;

    @BeforeEach
    void setUp() {
        downloadCache = mock(DownloadCacheImpl.class);
        blockchain = mock(Blockchain.class);
        blockService = mock(BlockService.class);
        batchPocVerifier = mock(BatchPocVerifier.class);
        syncPreVerifier = new SyncPreVerifier(downloadCache, blockchain, blockService, batchPocVerifier);

        tip = block(10L, 9L);
        when(blockchain.getLastBlock()).thenReturn(tip);
    }

    @AfterEach
    void tearDown() {
        Thread.interrupted(); // clear the flag for other tests
    }

    @Test
    void preVerifyBatch_GivenPreviousBlocksInCacheAndChain_PassesEachBlockWithItsPreviousBlock() throws Exception {
        // Arrange: block 11 follows the tip (in the chain), block 12 follows block 11 (in the cache)
        Block block11 = block(11L, 10L);
        Block block12 = block(12L, 11L);
        when(downloadCache.takeUnverifiedBlocks(16)).thenReturn(List.of(block11, block12));
        when(downloadCache.getBlock(11L)).thenReturn(block11);
        when(blockchain.getBlock(10L)).thenReturn(tip);

        // Act
        syncPreVerifier.preVerifyBatch(16);

        // Assert
        verify(batchPocVerifier).preVerify(List.of(
                new BatchPocVerifier.Task(block11, tip),
                new BatchPocVerifier.Task(block12, block11)));
    }

    @Test
    void preVerifyBatch_GivenBlockWithoutPreviousBlock_SkipsIt() throws Exception {
        Block orphan = block(20L, 19L);
        Block block11 = block(11L, 10L);
        when(downloadCache.takeUnverifiedBlocks(4)).thenReturn(List.of(orphan, block11));
        when(blockchain.getBlock(10L)).thenReturn(tip);

        syncPreVerifier.preVerifyBatch(4);

        verify(batchPocVerifier).preVerify(List.of(new BatchPocVerifier.Task(block11, tip)));
    }

    @Test
    void preVerifyBatch_GivenEmptyQueue_PassesEmptyBatch() throws Exception {
        when(downloadCache.takeUnverifiedBlocks(16)).thenReturn(List.of());

        syncPreVerifier.preVerifyBatch(16);

        verify(batchPocVerifier).preVerify(List.of());
    }

    @Test
    void preVerifyNextBlock_GivenUnverifiedNextBlock_PreVerifiesItAndRemovesItFromUnverifiedQueue() throws Exception {
        // Arrange: pre-verification marks the block as verified
        Block next = block(11L, 10L);
        when(downloadCache.getNextBlock(10L)).thenReturn(next);
        boolean[] verified = {false};
        when(next.isVerified()).thenAnswer(invocation -> verified[0]);
        doAnswer(invocation -> verified[0] = true).when(blockService).preVerify(next, tip);

        // Act
        syncPreVerifier.preVerifyNextBlock();

        // Assert
        verify(blockService).preVerify(next, tip);
        verify(downloadCache).removeUnverified(11L);
    }

    @Test
    void preVerifyNextBlock_GivenNoNextBlock_DoesNothing() throws Exception {
        when(downloadCache.getNextBlock(10L)).thenReturn(null);

        syncPreVerifier.preVerifyNextBlock();

        verify(blockService, never()).preVerify(any(), any());
    }

    @Test
    void preVerifyNextBlock_GivenAlreadyVerifiedBlock_DoesNotVerifyAgain() throws Exception {
        Block next = block(11L, 10L);
        when(next.isVerified()).thenReturn(true);
        when(downloadCache.getNextBlock(10L)).thenReturn(next);

        syncPreVerifier.preVerifyNextBlock();

        verify(blockService, never()).preVerify(any(), any());
        verify(downloadCache, never()).removeUnverified(anyLong());
    }

    @Test
    void preVerifyNextBlock_GivenInvalidBlock_LeavesItUnverifiedInTheQueue() throws Exception {
        Block next = block(11L, 10L);
        when(downloadCache.getNextBlock(10L)).thenReturn(next);
        doThrow(new BlockNotAcceptedException("bad transaction signature")).when(blockService).preVerify(next, tip);

        syncPreVerifier.preVerifyNextBlock();

        verify(downloadCache, never()).removeUnverified(anyLong());
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void preVerifyNextBlock_GivenInterrupt_KeepsInterruptFlag() throws Exception {
        Block next = block(11L, 10L);
        when(downloadCache.getNextBlock(10L)).thenReturn(next);
        doThrow(new InterruptedException()).when(blockService).preVerify(next, tip);

        syncPreVerifier.preVerifyNextBlock();

        assertTrue(Thread.currentThread().isInterrupted());
        verify(downloadCache, never()).removeUnverified(anyLong());
    }

    private static Block block(long id, long previousId) {
        Block block = mock(Block.class);
        when(block.getId()).thenReturn(id);
        when(block.getPreviousBlockId()).thenReturn(previousId);
        when(block.getStringId()).thenReturn(Long.toUnsignedString(id));
        return block;
    }
}
