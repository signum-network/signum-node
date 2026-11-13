package brs;

import brs.BlockchainProcessor.BlockNotAcceptedException;
import brs.peer.Peer;
import brs.services.BlockService;
import brs.util.DownloadCacheImpl;
import brs.util.ThreadPool;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the fork decisions against an in-memory chain: {@link FakeChain} pops and pushes blocks,
 * and the mocked {@link Blockchain} reports its tip.
 * <p>
 * Our chain is {@code common <- own1 <- own2} (cumulative difficulty 10, 20, 30); fork blocks
 * build on {@code common}.
 */
class ForkProcessorTest {

    private static final long COMMON_ID = 1;

    private Blockchain blockchain;
    private BlockService blockService;
    private DownloadCacheImpl downloadCache;
    private TransactionProcessorImpl transactionProcessor;
    private Peer peer;
    private FakeChain chain;

    private Block common;
    private Block own1;
    private Block own2;

    private ForkProcessor forkProcessor;

    @BeforeEach
    void setUp() {
        blockchain = mock(Blockchain.class);
        blockService = mock(BlockService.class);
        downloadCache = mock(DownloadCacheImpl.class);
        transactionProcessor = mock(TransactionProcessorImpl.class);
        peer = mock(Peer.class);
        chain = new FakeChain();

        when(transactionProcessor.getUnconfirmedTransactionsSyncObj()).thenReturn(new Object());
        when(blockchain.getLastBlock()).thenAnswer(invocation -> chain.tip());
        when(blockchain.getBlock(anyLong())).thenAnswer(invocation -> chain.find(invocation.getArgument(0)));

        common = block(COMMON_ID, 0, 10);
        own1 = block(2, COMMON_ID, 20);
        own2 = block(3, 2, 30);
        chain.blocks.addAll(List.of(common, own1, own2));

        forkProcessor = new ForkProcessor(blockchain, blockService, downloadCache, transactionProcessor, chain);
    }

    @Test
    void testProcess_GivenStrongerFork_SwitchesToForkAndRequeuesOwnTransactions() {
        Block fork1 = block(12, COMMON_ID, 25);
        Block fork2 = block(13, 12, 35);

        forkProcessor.process(peer, List.of(fork1, fork2), COMMON_ID);

        assertEquals(List.of(common, fork1, fork2), chain.blocks);
        verify(transactionProcessor).processLater(own1.getTransactions());
        verify(transactionProcessor).processLater(own2.getTransactions());
        verify(peer, never()).blacklist(anyString());
    }

    @Test
    void testProcess_GivenWeakerFork_BlacklistsPeerAndRestoresOwnChain() {
        Block fork1 = block(12, COMMON_ID, 25);
        Block fork2 = block(13, 12, 28);

        forkProcessor.process(peer, List.of(fork1, fork2), COMMON_ID);

        verify(peer).blacklist("got a bad fork");
        assertEquals(List.of(common, own1, own2), chain.blocks);
        verify(transactionProcessor).processLater(fork1.getTransactions());
        verify(transactionProcessor).processLater(fork2.getTransactions());
        verify(transactionProcessor, never()).processLater(own1.getTransactions());
        verify(transactionProcessor, never()).processLater(own2.getTransactions());
    }

    @Test
    void testProcess_GivenInvalidForkBlock_BlacklistsPeerAndRestoresOwnChain() throws Exception {
        Block fork1 = block(12, COMMON_ID, 40);
        BlockNotAcceptedException invalid = new BlockNotAcceptedException("invalid");
        doThrow(invalid).when(blockService).preVerify(eq(fork1), any());

        forkProcessor.process(peer, List.of(fork1), COMMON_ID);

        verify(peer).blacklist(invalid, "during processing a fork");
        assertEquals(List.of(common, own1, own2), chain.blocks);
    }

    @Test
    void testProcess_GivenInvalidBlockAfterValidForkBlocks_KeepsStrongerPartialFork() throws Exception {
        Block fork1 = block(12, COMMON_ID, 35);
        Block fork2 = block(13, 12, 45);
        BlockNotAcceptedException invalid = new BlockNotAcceptedException("invalid");
        doThrow(invalid).when(blockService).preVerify(eq(fork2), any());

        forkProcessor.process(peer, List.of(fork1, fork2), COMMON_ID);

        verify(peer).blacklist(invalid, "during processing a fork");
        assertEquals(List.of(common, fork1), chain.blocks);
        verify(transactionProcessor).processLater(own1.getTransactions());
        verify(transactionProcessor).processLater(own2.getTransactions());
    }

    @Test
    void testProcess_GivenPersistenceFailureAndWeakerPartialFork_RestoresOwnChainWithoutBlacklisting() {
        Block fork1 = block(12, COMMON_ID, 25);
        Block fork2 = block(13, 12, 35);
        chain.failOnPush(fork2, notPersisted());

        forkProcessor.process(peer, List.of(fork1, fork2), COMMON_ID);

        verify(peer, never()).blacklist(anyString());
        verify(peer, never()).blacklist(any(), anyString());
        verify(peer, never()).blacklist();
        assertEquals(List.of(common, own1, own2), chain.blocks);
        verify(transactionProcessor).processLater(fork1.getTransactions());
    }

    @Test
    void testProcess_GivenPersistenceFailureAndStrongerPartialFork_KeepsPartialForkWithoutBlacklisting() {
        Block fork1 = block(12, COMMON_ID, 35);
        Block fork2 = block(13, 12, 45);
        chain.failOnPush(fork2, notPersisted());

        forkProcessor.process(peer, List.of(fork1, fork2), COMMON_ID);

        verify(peer, never()).blacklist(anyString());
        verify(peer, never()).blacklist(any(), anyString());
        assertEquals(List.of(common, fork1), chain.blocks);
        verify(transactionProcessor).processLater(own1.getTransactions());
        verify(transactionProcessor).processLater(own2.getTransactions());
    }

    @Test
    void testProcess_GivenPersistenceFailureOnFirstForkBlock_RestoresOwnChainWithoutBlacklisting() {
        Block fork1 = block(12, COMMON_ID, 40);
        chain.failOnPush(fork1, notPersisted());

        forkProcessor.process(peer, List.of(fork1), COMMON_ID);

        verify(peer, never()).blacklist(anyString());
        verify(peer, never()).blacklist(any(), anyString());
        assertEquals(List.of(common, own1, own2), chain.blocks);
    }

    @Test
    void testProcess_GivenPersistenceFailureWhileRestoringOwnChain_RequeuesFailedAndRemainingBlocks() {
        Block fork1 = block(12, COMMON_ID, 40);
        chain.failOnPush(fork1, notPersisted());
        chain.failOnPush(own1, notPersisted());

        forkProcessor.process(peer, List.of(fork1), COMMON_ID);

        assertEquals(List.of(common), chain.blocks);
        verify(transactionProcessor).processLater(own1.getTransactions());
        verify(transactionProcessor).processLater(own2.getTransactions());
    }

    @Test
    void testProcess_GivenPersistenceFailureOnNewestOwnBlock_RequeuesOnlyThatBlock() {
        Block fork1 = block(12, COMMON_ID, 40);
        chain.failOnPush(fork1, notPersisted());
        chain.failOnPush(own2, notPersisted());

        forkProcessor.process(peer, List.of(fork1), COMMON_ID);

        assertEquals(List.of(common, own1), chain.blocks);
        verify(transactionProcessor, never()).processLater(own1.getTransactions());
        verify(transactionProcessor).processLater(own2.getTransactions());
    }

    @Test
    void testProcess_GivenOwnBlockNoLongerAcceptable_RequeuesItAndRemainingBlocks() {
        Block fork1 = block(12, COMMON_ID, 40);
        chain.failOnPush(fork1, notPersisted());
        chain.failOnPush(own1, new BlockNotAcceptedException("no longer valid"));

        forkProcessor.process(peer, List.of(fork1), COMMON_ID);

        assertEquals(List.of(common), chain.blocks);
        verify(transactionProcessor).processLater(own1.getTransactions());
        verify(transactionProcessor).processLater(own2.getTransactions());
    }

    @Test
    void testProcess_GivenInterruptWhileRestoringOwnBlock_RequeuesItAndRemainingBlocks() throws Exception {
        Block fork1 = block(12, COMMON_ID, 40);
        chain.failOnPush(fork1, notPersisted());
        when(own1.isVerified()).thenReturn(false);
        doThrow(new InterruptedException()).when(blockService).preVerify(eq(own1), any());

        try {
            forkProcessor.process(peer, List.of(fork1), COMMON_ID);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted(); // clear the flag for other tests
        }

        assertEquals(List.of(common), chain.blocks);
        verify(transactionProcessor).processLater(own1.getTransactions());
        verify(transactionProcessor).processLater(own2.getTransactions());
    }

    @Test
    void testProcess_GivenInterruptWhilePushingFork_RestoresOwnChainWithoutBlacklisting() throws Exception {
        Block fork1 = block(12, COMMON_ID, 35);
        Block fork2 = block(13, 12, 45);
        doThrow(new InterruptedException()).when(blockService).preVerify(eq(fork1), any());

        try {
            forkProcessor.process(peer, List.of(fork1, fork2), COMMON_ID);
        } finally {
            Thread.interrupted(); // clear the flag for other tests
        }

        assertEquals(List.of(common, own1, own2), chain.blocks);
        verify(blockService, never()).preVerify(eq(fork2), any());
        verify(peer, never()).blacklist(any(), anyString());
    }

    @Test
    void testProcess_GivenUnverifiedOwnBlock_PreVerifiesItBeforeRestoring() throws Exception {
        Block fork1 = block(12, COMMON_ID, 40);
        chain.failOnPush(fork1, notPersisted());
        when(own1.isVerified()).thenReturn(false);

        forkProcessor.process(peer, List.of(fork1), COMMON_ID);

        verify(blockService).preVerify(own1, common);
        verify(blockService, never()).preVerify(eq(own2), any());
    }

    @Test
    void testProcess_GivenForkBlockNotConnectingToTip_SkipsIt() {
        Block fork1 = block(12, COMMON_ID, 35);
        Block unrelated = block(20, 99, 100);
        Block fork2 = block(13, 12, 45);

        forkProcessor.process(peer, List.of(fork1, unrelated, fork2), COMMON_ID);

        assertEquals(List.of(common, fork1, fork2), chain.blocks);
        assertFalse(chain.pushed.contains(unrelated));
    }

    @Test
    void testProcess_GivenCommonBlockNoLongerInChain_SkipsForkProcessing() {
        // e.g. removed by a manual pop off after the fork was detected
        Block fork1 = block(12, 50, 40);

        forkProcessor.process(peer, List.of(fork1), 50);

        assertEquals(List.of(common, own1, own2), chain.blocks);
        assertTrue(chain.pushed.isEmpty());
        verify(peer, never()).blacklist(anyString());
        verify(downloadCache).resetForkBlocks();
        verify(downloadCache).resetCache();
    }

    @Test
    void testProcess_GivenUnexpectedException_StillResetsForkBlocksAndCache() {
        Block fork1 = block(12, COMMON_ID, 40);
        IllegalStateException unexpected = new IllegalStateException("unexpected");
        chain.failOnPush(fork1, unexpected);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> forkProcessor.process(peer, List.of(fork1), COMMON_ID));

        assertSame(unexpected, thrown);
        verify(downloadCache).resetForkBlocks();
        verify(downloadCache).resetCache();
    }

    @Test
    void testProcess_GivenFork_LocksCacheBeforeAndResetsItAfterProcessing() {
        Block fork1 = block(12, COMMON_ID, 40);

        forkProcessor.process(peer, List.of(fork1), COMMON_ID);

        InOrder order = inOrder(downloadCache);
        order.verify(downloadCache).lockCache();
        order.verify(downloadCache).resetForkBlocks();
        order.verify(downloadCache).resetCache();
    }

    @Test
    void testProcess_GivenInterruptedThread_SkipsForkProcessing() {
        when(downloadCache.size()).thenReturn(1);
        Block fork1 = block(12, COMMON_ID, 40);

        Thread.currentThread().interrupt();
        try {
            forkProcessor.process(peer, List.of(fork1), COMMON_ID);
        } finally {
            Thread.interrupted(); // clear the flag for other tests
        }

        assertEquals(List.of(common, own1, own2), chain.blocks);
        assertTrue(chain.pushed.isEmpty());
        verify(downloadCache).resetForkBlocks();
        verify(downloadCache).resetCache();
    }

    @Test
    void testProcess_GivenShutdown_SkipsForkProcessing() {
        Block fork1 = block(12, COMMON_ID, 40);

        boolean wasRunning = ThreadPool.running.getAndSet(false);
        try {
            forkProcessor.process(peer, List.of(fork1), COMMON_ID);
        } finally {
            ThreadPool.running.set(wasRunning);
        }

        assertEquals(List.of(common, own1, own2), chain.blocks);
        assertTrue(chain.pushed.isEmpty());
        verify(downloadCache).resetForkBlocks();
        verify(downloadCache).resetCache();
    }

    private static BlockNotPersistedException notPersisted() {
        return new BlockNotPersistedException(0, BlockPushRecovery.Failure.PERSISTENCE,
                new RuntimeException("disk full"));
    }

    private static Block block(long id, long previousId, int cumulativeDifficulty) {
        Block block = mock(Block.class);
        when(block.getId()).thenReturn(id);
        when(block.getPreviousBlockId()).thenReturn(previousId);
        when(block.getCumulativeDifficulty()).thenReturn(BigInteger.valueOf(cumulativeDifficulty));
        when(block.getStringId()).thenReturn(Long.toUnsignedString(id));
        when(block.getBlockSignature()).thenReturn(new byte[0]);
        when(block.getJsonObject()).thenReturn(new JsonObject());
        when(block.isVerified()).thenReturn(true);
        List<Transaction> transactions = List.of(mock(Transaction.class));
        when(block.getTransactions()).thenReturn(transactions);
        return block;
    }

    /** An in-memory chain, tip last, that can be told to fail pushing specific blocks. */
    private static final class FakeChain implements ForkProcessor.ChainOperations {
        final List<Block> blocks = new ArrayList<>();
        final List<Block> pushed = new ArrayList<>();
        private final Map<Long, Exception> pushFailures = new HashMap<>();

        void failOnPush(Block block, Exception failure) {
            pushFailures.put(block.getId(), failure);
        }

        Block tip() {
            return blocks.get(blocks.size() - 1);
        }

        Block find(long id) {
            return blocks.stream().filter(block -> block.getId() == id).findFirst().orElse(null);
        }

        @Override
        public List<Block> popOffTo(Block commonBlock, List<Block> forkBlocks) {
            // like BlockchainProcessorImpl: fails on null, nothing to pop off if not in the chain
            Objects.requireNonNull(commonBlock, "commonBlock");
            if (!blocks.contains(commonBlock)) {
                return Collections.emptyList();
            }
            List<Block> poppedOff = new ArrayList<>();
            while (tip() != commonBlock) {
                poppedOff.add(blocks.remove(blocks.size() - 1));
            }
            return poppedOff;
        }

        @Override
        public void pushBlock(Block block) throws BlockNotAcceptedException {
            Exception failure = pushFailures.get(block.getId());
            if (failure instanceof BlockNotAcceptedException notAccepted) {
                throw notAccepted;
            }
            if (failure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            blocks.add(block);
            pushed.add(block);
        }
    }
}
