package brs.db.cache;

import brs.Transaction;
import brs.props.Props;
import brs.Signum;

import java.util.*;

/**
 * Simple cache storing transactions for the most recent blocks.
 */
public final class TransactionCache {

    private static TransactionCache instance;

    public static synchronized TransactionCache getInstance() {
        if (instance == null) {
            int blocks = Signum.getPropertyService().getInt(Props.TRANSACTION_CACHE_BLOCK_COUNT);
            instance = new TransactionCache(blocks);
        }
        return instance;
    }

    private final int blocksToCache;
    private final Map<Long, Transaction> byId = new HashMap<>();
    private final Map<String, Transaction> byHash = new HashMap<>();
    private final Map<Long, List<Transaction>> byBlock = new HashMap<>();
    private final Deque<Long> blockOrder = new ArrayDeque<>();

    private TransactionCache(int blocksToCache) {
        this.blocksToCache = blocksToCache;
    }

    /**
     * Adds the transactions for a newly processed block.
     */
    public synchronized void addBlockTransactions(long blockId, List<Transaction> txs) {
        if (blocksToCache <= 0 || txs == null) {
            return;
        }
        blockOrder.addLast(blockId);
        byBlock.put(blockId, txs);
        for (Transaction t : txs) {
            byId.put(t.getId(), t);
            byHash.put(t.getFullHash(), t);
        }
        while (blockOrder.size() > blocksToCache) {
            Long oldBlockId = blockOrder.removeFirst();
            List<Transaction> oldTxs = byBlock.remove(oldBlockId);
            if (oldTxs != null) {
                for (Transaction t : oldTxs) {
                    byId.remove(t.getId());
                    byHash.remove(t.getFullHash());
                }
            }
        }
    }

    public synchronized Transaction getById(long id) {
        return byId.get(id);
    }

    public synchronized Transaction getByHash(String hash) {
        return byHash.get(hash);
    }

    public synchronized List<Transaction> getBlockTransactions(long blockId) {
        List<Transaction> txs = byBlock.get(blockId);
        if (txs == null) {
            return null;
        }
        return Collections.unmodifiableList(txs);
    }

    /**
     * Removes all cached transactions for a popped off block.
     */
    public synchronized void removeBlockTransactions(long blockId) {
        List<Transaction> txs = byBlock.remove(blockId);
        if (txs != null) {
            blockOrder.remove(blockId);
            for (Transaction t : txs) {
                byId.remove(t.getId());
                byHash.remove(t.getFullHash());
            }
        }
    }

    /**
     * Clears the entire transaction cache.
     */
    public synchronized void clear() {
        byId.clear();
        byHash.clear();
        byBlock.clear();
        blockOrder.clear();
    }
}
