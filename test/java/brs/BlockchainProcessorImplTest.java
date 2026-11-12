package brs;

import brs.common.QuickMocker;
import brs.crypto.Crypto;
import brs.db.BlockDb;
import brs.db.TransactionDb;
import brs.db.cache.DBCacheManagerImpl;
import brs.db.store.BlockchainStore;
import brs.db.store.DerivedTableManager;
import brs.db.store.Stores;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.AccountService;
import brs.services.AliasService;
import brs.services.BlockService;
import brs.services.EscrowService;
import brs.services.IndirectIncomingService;
import brs.services.SubscriptionService;
import brs.services.TimeService;
import brs.services.TransactionService;
import brs.statistics.StatisticsManagerImpl;
import brs.transactionduplicates.TransactionDuplicationKey;
import brs.util.DownloadCacheImpl;
import brs.util.ThreadPool;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives {@code pushBlock} through the block importer thread, with all collaborators mocked,
 * to cover how failed pushes are recovered from.
 */
class BlockchainProcessorImplTest {

    private static final long GENESIS_BLOCK_ID = 1L;
    private static final int TIP_HEIGHT = Constants.MAX_ROLLBACK - 1;
    private static final int EPOCH_TIME = 1_000_000;

    private MockedStatic<Signum> signumMock;

    private ThreadPool threadPool;
    private BlockService blockService;
    private TransactionProcessorImpl transactionProcessor;
    private BlockchainImpl blockchain;
    private PropertyService propertyService;
    private TransactionDb transactionDb;
    private BlockDb blockDb;
    private BlockchainStore blockchainStore;
    private Stores stores;
    private TransactionService transactionService;
    private DownloadCacheImpl downloadCache;
    private AccountService accountService;

    private Block tip;
    private Block persistedTip;
    private Block block;
    private Transaction firstTransaction;
    private Transaction secondTransaction;

    @BeforeEach
    void setUp() throws Exception {
        ThreadPool.running.set(true);

        propertyService = mock(PropertyService.class);
        when(propertyService.getString(Props.GENESIS_BLOCK_ID)).thenReturn(Long.toUnsignedString(GENESIS_BLOCK_ID));
        when(propertyService.getBoolean(Props.AUTO_POP_OFF_ENABLED)).thenReturn(true);
        when(propertyService.getBoolean(Props.DB_TRIM_DERIVED_TABLES)).thenReturn(true);
        configureBackoff(60_000, 60_000, 5);

        FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities();
        doReturn(1000).when(fluxCapacitor).getValue(eq(FluxValues.MAX_PAYLOAD_LENGTH), anyInt());
        signumMock = mockStatic(Signum.class);
        signumMock.when(Signum::getFluxCapacitor).thenReturn(fluxCapacitor);
        signumMock.when(Signum::getPropertyService).thenReturn(propertyService);

        threadPool = mock(ThreadPool.class);
        blockService = mock(BlockService.class);
        transactionProcessor = mock(TransactionProcessorImpl.class);
        blockchain = mock(BlockchainImpl.class);
        transactionDb = mock(TransactionDb.class);
        blockDb = mock(BlockDb.class);
        blockchainStore = mock(BlockchainStore.class);
        stores = mock(Stores.class);
        transactionService = mock(TransactionService.class);
        downloadCache = mock(DownloadCacheImpl.class);
        accountService = mock(AccountService.class);

        when(transactionProcessor.getUnconfirmedTransactionsSyncObj()).thenReturn(new Object());
        when(transactionService.verifyPublicKey(any())).thenReturn(true);
        when(transactionService.applyUnconfirmed(any())).thenReturn(true);
        when(blockService.verifyBlockSignature(any())).thenReturn(true);

        tip = block(10L, TIP_HEIGHT, 100);
        persistedTip = block(9L, TIP_HEIGHT - 1, 50);
        when(tip.getBytes()).thenReturn(new byte[] {1, 2, 3});
        when(blockchain.getLastBlock()).thenReturn(tip);
        when(blockchain.getHeight()).thenReturn(TIP_HEIGHT);
        when(blockDb.hasBlock(GENESIS_BLOCK_ID)).thenReturn(true);
        when(blockDb.findLastBlock()).thenReturn(tip);

        firstTransaction = transaction(101L);
        secondTransaction = transaction(102L);
        block = childOf(tip, 11L, List.of(firstTransaction, secondTransaction));
        when(downloadCache.size()).thenReturn(1);
        when(downloadCache.getNextBlock(tip.getId())).thenReturn(block);
    }

    @AfterEach
    void tearDown() {
        if (signumMock != null) {
            signumMock.close();
        }
    }

    @Test
    void testImportBlock_GivenPersistenceFailure_ReloadsTipFromDatabaseAfterRollback() {
        Runnable importer = createImporter();
        doThrow(new DataAccessException("foreign key constraint fails")).when(accountService).flushAccountTable();
        when(blockDb.findLastBlock()).thenReturn(persistedTip);

        assertDoesNotThrow(importer::run);

        InOrder order = inOrder(stores, blockDb, blockchain, downloadCache);
        order.verify(stores).rollbackTransaction();
        order.verify(stores).endTransaction();
        order.verify(blockDb).findLastBlock();
        order.verify(blockchain).setLastBlock(persistedTip);
        order.verify(downloadCache).resetCache();
        verify(stores, never()).commitTransaction();
    }

    @Test
    void testImportBlock_GivenPersistenceFailure_ReturnsTransactionsToPool() {
        Runnable importer = createImporter();
        doThrow(new DataAccessException("connection lost")).when(accountService).flushAccountTable();

        importer.run();

        verify(transactionProcessor).removeForgedTransactions(block.getTransactions());
        verify(transactionProcessor).processLater(block.getTransactions());
    }

    @Test
    void testImportBlock_GivenFailureBeforeTransactionsRemoved_DoesNotTouchPool() {
        Runnable importer = createImporter();
        doThrow(new DataAccessException("connection lost")).when(blockService).setPrevious(any(), any());

        importer.run();

        verify(transactionProcessor, never()).removeForgedTransactions(any());
        verify(transactionProcessor, never()).processLater(any());
        verify(stores).rollbackTransaction();
    }

    @Test
    void testImportBlock_GivenSqliteBusy_KeepsDownloadCache() {
        Runnable importer = createImporter();
        doThrow(new DataAccessException("SQL [update]", new SQLException("[SQLITE_BUSY] The database file is locked")))
                .when(accountService).flushAccountTable();

        assertDoesNotThrow(importer::run);

        verify(stores).rollbackTransaction();
        verify(downloadCache, never()).resetCache();
    }

    @Test
    void testImportBlock_GivenPersistenceFailure_DelaysNextImport() {
        Runnable importer = createImporter();
        doThrow(new DataAccessException("connection lost")).when(accountService).flushAccountTable();

        importer.run();
        importer.run();

        verify(downloadCache, times(1)).getNextBlock(anyLong());
    }

    @Test
    void testImportBlock_GivenRepeatedFailuresAtThreshold_TriggersAutoPopOff() throws InterruptedException {
        configureBackoff(1, 1, 1);
        Runnable importer = createImporter();
        doThrow(new DataAccessException("connection lost")).when(accountService).flushAccountTable();
        IllegalStateException popOffReached = new IllegalStateException("pop off reached");
        when(blockchain.getBlockAtHeight(anyInt())).thenThrow(popOffReached);

        // autoPopOff doesn't pop anything on the first failure at a height
        importer.run();
        verify(blockchain, never()).getBlockAtHeight(anyInt());
        TimeUnit.MILLISECONDS.sleep(20);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, importer::run);

        verify(blockchain).getBlockAtHeight(TIP_HEIGHT - 1);
        assertSame(popOffReached, thrown);
    }

    @Test
    void testImportBlock_GivenFailuresBelowThreshold_DoesNotPopOff() throws InterruptedException {
        configureBackoff(1, 1, 5);
        Runnable importer = createImporter();
        doThrow(new DataAccessException("connection lost")).when(accountService).flushAccountTable();

        for (int i = 0; i < 4; i++) {
            importer.run();
            TimeUnit.MILLISECONDS.sleep(10);
        }

        verify(downloadCache, times(4)).getNextBlock(anyLong());
        verify(blockchain, never()).getBlockAtHeight(anyInt());
    }

    @Test
    void testImportBlock_GivenRejectedTransaction_ReturnsOtherTransactionsToPool() {
        Runnable importer = createImporter();
        when(transactionService.applyUnconfirmed(secondTransaction)).thenReturn(false);

        assertDoesNotThrow(importer::run);

        verify(transactionProcessor).processLater(List.of(firstTransaction));
        verify(blockchain).setLastBlock(tip);
        verify(blockDb, never()).findLastBlock();
    }

    @Test
    void testImportBlock_GivenSuccessfulPush_CommitsAndRemovesBlockFromCache() {
        Runnable importer = createImporter();
        when(downloadCache.size()).thenReturn(1, 0);

        assertDoesNotThrow(importer::run);

        verify(stores).commitTransaction();
        verify(downloadCache).removeBlock(block);
        verify(stores, never()).rollbackTransaction();
        verify(transactionProcessor, never()).processLater(any());
    }

    @Test
    void testImportBlock_GivenMaintenanceFailureAfterCommit_KeepsBlockAndContinuesImporting() {
        Block maintenanceBlock = childOf(tip, 12L, List.of());
        when(maintenanceBlock.getHeight()).thenReturn(Constants.MAX_ROLLBACK);
        when(downloadCache.getNextBlock(tip.getId())).thenReturn(maintenanceBlock);
        // a second block is waiting: the importer must not stop after the maintenance failure
        when(downloadCache.size()).thenReturn(1, 1, 0);
        Runnable importer = createImporter();
        // after construction, which checks the database state as well
        when(blockchain.getTotalMined()).thenThrow(new DataAccessException("lock wait timeout exceeded"));

        assertDoesNotThrow(importer::run);

        verify(blockchain, times(2)).getTotalMined();
        verify(stores, times(2)).commitTransaction();
        verify(downloadCache, times(2)).removeBlock(maintenanceBlock);
        verify(stores, never()).rollbackTransaction();
        verify(downloadCache, never()).resetCache();
        verify(blockchain, never()).setLastBlock(tip);
    }

    private Runnable createImporter() {
        new BlockchainProcessorImpl(threadPool, blockService, transactionProcessor, blockchain, propertyService,
                mock(SubscriptionService.class), timeService(), mock(DerivedTableManager.class), blockDb,
                transactionDb, mock(EconomicClustering.class), blockchainStore, stores, mock(EscrowService.class),
                transactionService, downloadCache, mock(Generator.class), mock(StatisticsManagerImpl.class),
                mock(DBCacheManagerImpl.class), accountService, mock(IndirectIncomingService.class),
                mock(AliasService.class));
        ArgumentCaptor<Runnable> importer = ArgumentCaptor.forClass(Runnable.class);
        verify(threadPool).scheduleThread(eq("ImportBlocks"), importer.capture(), anyInt(), any());
        // ignore what happened while loading the chain on construction
        clearInvocations(blockchain, blockDb, stores, downloadCache, transactionProcessor);
        return importer.getValue();
    }

    private void configureBackoff(int baseDelayMs, int maxDelayMs, int popOffThreshold) {
        when(propertyService.getInt(Props.BLOCK_PROCESS_THREAD_DELAY)).thenReturn(baseDelayMs);
        when(propertyService.getInt(Props.PERSISTENCE_FAILURE_MAX_RETRY_DELAY)).thenReturn(maxDelayMs);
        when(propertyService.getInt(Props.PERSISTENCE_FAILURE_POP_OFF_THRESHOLD)).thenReturn(popOffThreshold);
    }

    private static TimeService timeService() {
        TimeService timeService = mock(TimeService.class);
        when(timeService.getEpochTime()).thenReturn(EPOCH_TIME);
        return timeService;
    }

    private static Block block(long id, int height, int timestamp) {
        Block block = mock(Block.class);
        when(block.getId()).thenReturn(id);
        when(block.getStringId()).thenReturn(Long.toUnsignedString(id));
        when(block.getHeight()).thenReturn(height);
        when(block.getTimestamp()).thenReturn(timestamp);
        return block;
    }

    private static Block childOf(Block parent, long id, List<Transaction> transactions) {
        // read the parent first: calling a mock while stubbing another one breaks Mockito
        int height = parent.getHeight() + 1;
        int timestamp = parent.getTimestamp() + 1;
        long previousBlockId = parent.getId();
        byte[] previousBlockHash = Crypto.sha256().digest(parent.getBytes());
        byte[] payloadHash = payloadHash(transactions);

        Block child = block(id, height, timestamp);
        when(child.getPreviousBlockId()).thenReturn(previousBlockId);
        when(child.getPreviousBlockHash()).thenReturn(previousBlockHash);
        when(child.getVersion()).thenReturn(3);
        when(child.isVerified()).thenReturn(true);
        when(child.getTransactions()).thenReturn(transactions);
        when(child.getPayloadHash()).thenReturn(payloadHash);
        return child;
    }

    private static Transaction transaction(long id) {
        Transaction transaction = mock(Transaction.class);
        TransactionType type = mock(TransactionType.class);
        when(transaction.getId()).thenReturn(id);
        when(transaction.getStringId()).thenReturn(Long.toUnsignedString(id));
        when(transaction.getExpiration()).thenReturn(Integer.MAX_VALUE);
        when(transaction.getDuplicationKey()).thenReturn(TransactionDuplicationKey.IS_NEVER_DUPLICATE);
        when(transaction.getType()).thenReturn(type);
        when(transaction.getBytes()).thenReturn(new byte[] {(byte) id});
        return transaction;
    }

    private static byte[] payloadHash(List<Transaction> transactions) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        transactions.forEach(transaction -> bytes.writeBytes(transaction.getBytes()));
        return Crypto.sha256().digest(bytes.toByteArray());
    }
}
