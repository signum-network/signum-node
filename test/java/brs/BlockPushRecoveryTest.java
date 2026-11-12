package brs;

import brs.at.ATProcessorCache;
import brs.db.BlockDb;
import brs.db.store.Stores;
import brs.util.DownloadCacheImpl;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BlockPushRecoveryTest {

    private Stores stores;
    private BlockchainImpl blockchain;
    private BlockDb blockDb;
    private DownloadCacheImpl downloadCache;
    private ATProcessorCache atProcessorCache;
    private TransactionProcessorImpl transactionProcessor;
    private Block previousTip;
    private Block persistedTip;

    private BlockPushRecovery recovery;

    @BeforeEach
    void setUp() {
        stores = mock(Stores.class);
        blockchain = mock(BlockchainImpl.class);
        blockDb = mock(BlockDb.class);
        downloadCache = mock(DownloadCacheImpl.class);
        atProcessorCache = mock(ATProcessorCache.class);
        transactionProcessor = mock(TransactionProcessorImpl.class);
        previousTip = mock(Block.class);
        persistedTip = mock(Block.class);

        recovery = new BlockPushRecovery(stores, blockchain, blockDb, downloadCache, atProcessorCache,
                transactionProcessor);
    }

    @Test
    void testRollback_GivenRollbackSucceeds_RollsBackTransaction() {
        recovery.rollback();

        verify(stores).rollbackTransaction();
    }

    @Test
    void testRollback_GivenRollbackFails_DoesNotThrow() {
        doThrow(new RuntimeException("connection lost")).when(stores).rollbackTransaction();

        assertDoesNotThrow(() -> recovery.rollback());
    }

    @Test
    void testRestore_GivenPersistenceFailure_RestoresTipFromDatabaseBeforeResettingCaches() {
        when(blockDb.findLastBlock()).thenReturn(persistedTip);

        recovery.restore(previousTip, BlockPushRecovery.Failure.PERSISTENCE, Collections.emptyList());

        // resetCache() derives its state from the current tip, so the tip must be restored first
        InOrder order = inOrder(blockchain, downloadCache, atProcessorCache);
        order.verify(blockchain).setLastBlock(persistedTip);
        order.verify(downloadCache).resetCache();
        order.verify(atProcessorCache).reset();
        verify(blockchain, never()).setLastBlock(previousTip);
    }

    @Test
    void testRestore_GivenPersistenceFailureAndReloadFails_FallsBackToPreviousTip() {
        when(blockDb.findLastBlock()).thenThrow(new DataAccessException("connection lost"));

        recovery.restore(previousTip, BlockPushRecovery.Failure.PERSISTENCE, Collections.emptyList());

        InOrder order = inOrder(blockchain, downloadCache);
        order.verify(blockchain).setLastBlock(previousTip);
        order.verify(downloadCache).resetCache();
    }

    @Test
    void testRestore_GivenPersistenceFailureAndEmptyDatabase_FallsBackToPreviousTip() {
        when(blockDb.findLastBlock()).thenReturn(null);

        recovery.restore(previousTip, BlockPushRecovery.Failure.PERSISTENCE, Collections.emptyList());

        verify(blockchain).setLastBlock(previousTip);
    }

    @Test
    void testRestore_GivenPersistenceFailureAndNoTipAvailable_LeavesTipUntouched() {
        when(blockDb.findLastBlock()).thenReturn(null);

        recovery.restore(null, BlockPushRecovery.Failure.PERSISTENCE, Collections.emptyList());

        verify(blockchain, never()).setLastBlock(any());
        verify(downloadCache).resetCache();
        verify(atProcessorCache).reset();
    }

    @Test
    void testRestore_GivenTransientFailure_KeepsDownloadCache() {
        when(blockDb.findLastBlock()).thenReturn(persistedTip);

        recovery.restore(previousTip, BlockPushRecovery.Failure.TRANSIENT, Collections.emptyList());

        verify(blockchain).setLastBlock(persistedTip);
        verify(downloadCache, never()).resetCache();
        verify(atProcessorCache).reset();
    }

    @Test
    void testRestore_GivenRejectedBlock_RestoresPreviousTipWithoutDatabaseLookup() {
        recovery.restore(previousTip, BlockPushRecovery.Failure.REJECTED, Collections.emptyList());

        verifyNoInteractions(blockDb);
        InOrder order = inOrder(blockchain, downloadCache, atProcessorCache);
        order.verify(blockchain).setLastBlock(previousTip);
        order.verify(downloadCache).resetCache();
        order.verify(atProcessorCache).reset();
    }

    @Test
    void testRestore_GivenRemovedTransactions_ReturnsThemToPoolAfterRestoringTip() {
        List<Transaction> removed = List.of(transaction(1), transaction(2));
        when(blockDb.findLastBlock()).thenReturn(persistedTip);

        recovery.restore(previousTip, BlockPushRecovery.Failure.PERSISTENCE, removed);

        InOrder order = inOrder(blockchain, transactionProcessor);
        order.verify(blockchain).setLastBlock(persistedTip);
        order.verify(transactionProcessor).processLater(removed);
    }

    @Test
    void testRestore_GivenNoRemovedTransactions_DoesNotTouchPool() {
        recovery.restore(previousTip, BlockPushRecovery.Failure.REJECTED, Collections.emptyList());

        verifyNoInteractions(transactionProcessor);
    }

    @Test
    void testRestore_GivenRequeueFails_StillResetsCaches() {
        List<Transaction> removed = List.of(transaction(1));
        doThrow(new DataAccessException("connection lost")).when(transactionProcessor).processLater(removed);

        assertDoesNotThrow(() -> recovery.restore(previousTip, BlockPushRecovery.Failure.REJECTED, removed));

        verify(downloadCache).resetCache();
        verify(atProcessorCache).reset();
    }

    @Test
    void testWithout_GivenContainedTransaction_RemovesItById() {
        Transaction first = transaction(1);
        Transaction second = transaction(2);

        List<Transaction> remaining = BlockPushRecovery.without(List.of(first, second), transaction(2));

        assertEquals(List.of(first), remaining);
    }

    @Test
    void testWithout_GivenNullExcluded_ReturnsSameList() {
        List<Transaction> transactions = List.of(transaction(1));

        assertSame(transactions, BlockPushRecovery.without(transactions, null));
    }

    @Test
    void testWithout_GivenEmptyList_ReturnsSameList() {
        List<Transaction> transactions = Collections.emptyList();

        assertSame(transactions, BlockPushRecovery.without(transactions, transaction(1)));
    }

    @Test
    void testClassify_GivenSqliteBusy_ReturnsTransient() {
        assertEquals(BlockPushRecovery.Failure.TRANSIENT, BlockPushRecovery.classify(
                new DataAccessException("SQL [insert]", new SQLException("[SQLITE_BUSY] The database file is locked"))));
    }

    @Test
    void testClassify_GivenOtherDatabaseError_ReturnsPersistence() {
        assertEquals(BlockPushRecovery.Failure.PERSISTENCE, BlockPushRecovery.classify(
                new DataAccessException("Cannot add or update a child row: a foreign key constraint fails")));
    }

    @Test
    void testClassify_GivenNonDatabaseError_ReturnsPersistence() {
        assertEquals(BlockPushRecovery.Failure.PERSISTENCE,
                BlockPushRecovery.classify(new IllegalStateException("unexpected")));
    }

    @Test
    void testIsSqliteBusy_GivenBusyMessage_ReturnsTrue() {
        assertTrue(BlockPushRecovery.isSqliteBusy(new SQLException("[SQLITE_BUSY] The database file is locked")));
    }

    @Test
    void testIsSqliteBusy_GivenWrappedBusySnapshot_ReturnsTrue() {
        assertTrue(BlockPushRecovery.isSqliteBusy(new RuntimeException("wrapped",
                new SQLException("[SQLITE_BUSY_SNAPSHOT] Another database connection has already written"))));
    }

    @Test
    void testIsSqliteBusy_GivenOtherError_ReturnsFalse() {
        assertFalse(BlockPushRecovery.isSqliteBusy(new SQLException("[SQLITE_CONSTRAINT] FOREIGN KEY constraint failed")));
    }

    @Test
    void testIsSqliteBusy_GivenNullMessage_ReturnsFalse() {
        assertFalse(BlockPushRecovery.isSqliteBusy(new RuntimeException((String) null)));
    }

    @Test
    void testIsSqliteBusy_GivenNull_ReturnsFalse() {
        assertFalse(BlockPushRecovery.isSqliteBusy(null));
    }

    @Test
    void testIsDatabaseError_GivenDataAccessException_ReturnsTrue() {
        assertTrue(BlockPushRecovery.isDatabaseError(
                new DataAccessException("Cannot add or update a child row: a foreign key constraint fails")));
    }

    @Test
    void testIsDatabaseError_GivenSQLException_ReturnsTrue() {
        assertTrue(BlockPushRecovery.isDatabaseError(new SQLException("connection lost")));
    }

    @Test
    void testIsDatabaseError_GivenWrappedSQLException_ReturnsTrue() {
        assertTrue(BlockPushRecovery.isDatabaseError(new RuntimeException("wrapped", new SQLException("deadlock"))));
    }

    @Test
    void testIsDatabaseError_GivenNonDatabaseException_ReturnsFalse() {
        assertFalse(BlockPushRecovery.isDatabaseError(new IllegalStateException("not a db error")));
    }

    @Test
    void testIsDatabaseError_GivenNull_ReturnsFalse() {
        assertFalse(BlockPushRecovery.isDatabaseError(null));
    }

    private static Transaction transaction(long id) {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getId()).thenReturn(id);
        return transaction;
    }
}
