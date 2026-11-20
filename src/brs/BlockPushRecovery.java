package brs;

import brs.at.ATProcessorCache;
import brs.db.BlockDb;
import brs.db.store.Stores;
import brs.util.DownloadCacheImpl;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Restores a consistent in-memory chain state after a block could not be pushed.
 * <p>
 * The database is the source of truth: after a persistence failure the in-memory tip is
 * reloaded from it, because the block the push was based on may itself never have been
 * committed (#1079). Caches are reset only after the tip is restored, since
 * {@link DownloadCacheImpl#resetCache()} derives its state from the current tip.
 * Transactions that were already taken out of the unconfirmed pool are put back, so a failed
 * push does not silently drop them.
 */
final class BlockPushRecovery {

    private static final Logger logger = LoggerFactory.getLogger(BlockPushRecovery.class);

    /** How a block push failed, which determines how much state has to be restored. */
    enum Failure {
        /** The block was rejected by validation; the previous tip is still valid. */
        REJECTED,
        /** A transient database error (e.g. SQLite busy); the downloaded blocks can be retried. */
        TRANSIENT,
        /** The block could not be persisted; the tip is reloaded from the database. */
        PERSISTENCE
    }

    private final Stores stores;
    private final BlockchainImpl blockchain;
    private final BlockDb blockDb;
    private final DownloadCacheImpl downloadCache;
    private final ATProcessorCache atProcessorCache;
    private final TransactionProcessorImpl transactionProcessor;

    BlockPushRecovery(Stores stores, BlockchainImpl blockchain, BlockDb blockDb,
            DownloadCacheImpl downloadCache, ATProcessorCache atProcessorCache,
            TransactionProcessorImpl transactionProcessor) {
        this.stores = stores;
        this.blockchain = blockchain;
        this.blockDb = blockDb;
        this.downloadCache = downloadCache;
        this.atProcessorCache = atProcessorCache;
        this.transactionProcessor = transactionProcessor;
    }

    static Failure classify(RuntimeException exception) {
        return isSqliteBusy(exception) ? Failure.TRANSIENT : Failure.PERSISTENCE;
    }

    /**
     * Rolls back the open transaction without letting a failed rollback mask the exception
     * that caused it. Must be called while the transaction is still open.
     */
    void rollback() {
        try {
            stores.rollbackTransaction();
        } catch (RuntimeException rollbackFailure) {
            logger.error("Rollback after failed block push failed", rollbackFailure);
        }
    }

    /**
     * Restores the tip, returns transactions to the unconfirmed pool and resets the caches.
     * Should be called after the transaction has ended, so the tip is reloaded through a fresh
     * connection.
     *
     * @param previousLastBlock the tip the failed push was based on, may be {@code null}
     * @param removedFromPool transactions the failed push had already removed from the unconfirmed pool
     */
    void restore(Block previousLastBlock, Failure failure, List<Transaction> removedFromPool) {
        Block tip = failure == Failure.REJECTED ? previousLastBlock : loadPersistedTip(previousLastBlock);
        if (tip != null) {
            blockchain.setLastBlock(tip);
        }
        requeue(removedFromPool);
        if (failure != Failure.TRANSIENT) {
            downloadCache.resetCache();
        }
        atProcessorCache.reset();
    }

    private void requeue(List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return;
        }
        try {
            transactionProcessor.processLater(transactions);
        } catch (RuntimeException e) {
            logger.warn("Could not return {} transactions to the unconfirmed pool after failed block push",
                    transactions.size(), e);
        }
    }

    private Block loadPersistedTip(Block fallback) {
        try {
            Block persistedTip = blockDb.findLastBlock();
            return persistedTip != null ? persistedTip : fallback;
        } catch (RuntimeException e) {
            logger.warn("Could not reload tip from database after failed block push, keeping previous tip", e);
            return fallback;
        }
    }

    /** Returns {@code transactions} without the one with the id of {@code excluded}. */
    static List<Transaction> without(List<Transaction> transactions, Transaction excluded) {
        if (excluded == null || transactions.isEmpty()) {
            return transactions;
        }
        List<Transaction> remaining = new ArrayList<>(transactions.size());
        for (Transaction transaction : transactions) {
            if (transaction.getId() != excluded.getId()) {
                remaining.add(transaction);
            }
        }
        return remaining;
    }

    static boolean isDatabaseError(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException || cause instanceof DataAccessException) {
                return true;
            }
        }
        return false;
    }

    static boolean isSqliteBusy(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains("[SQLITE_BUSY")) {
                return true;
            }
        }
        return false;
    }
}
