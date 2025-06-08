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
    private final Map<Integer, List<Transaction>> byHeight = new HashMap<>();
    private final Deque<Integer> heightOrder = new ArrayDeque<>();
    private final Map<Long, Integer> blockHeight = new HashMap<>();
    private long cacheHits;

    private TransactionCache(int blocksToCache) {
        this.blocksToCache = blocksToCache;
    }

    /**
     * Adds the transactions for a newly processed block.
     */
    public synchronized void addBlockTransactions(long blockId, int height, List<Transaction> txs) {
        if (blocksToCache <= 0 || txs == null) {
            return;
        }
        blockOrder.addLast(blockId);
        byBlock.put(blockId, txs);
        blockHeight.put(blockId, height);
        heightOrder.addLast(height);
        byHeight.put(height, txs);
        for (Transaction t : txs) {
            byId.put(t.getId(), t);
            byHash.put(t.getFullHash(), t);
        }
        while (blockOrder.size() > blocksToCache) {
            Long oldBlockId = blockOrder.removeFirst();
            List<Transaction> oldTxs = byBlock.remove(oldBlockId);
            Integer oldHeight = blockHeight.remove(oldBlockId);
            if (oldHeight != null) {
                heightOrder.removeFirst();
                byHeight.remove(oldHeight);
            }
            if (oldTxs != null) {
                for (Transaction t : oldTxs) {
                    byId.remove(t.getId());
                    byHash.remove(t.getFullHash());
                }
            }
        }
    }

    public synchronized Transaction getById(long id) {
        Transaction t = byId.get(id);
        if (t != null) {
            cacheHits++;
        }
        return t;
    }

    public synchronized Transaction getByHash(String hash) {
        Transaction t = byHash.get(hash);
        if (t != null) {
            cacheHits++;
        }
        return t;
    }

    public synchronized List<Transaction> getBlockTransactions(long blockId) {
        List<Transaction> txs = byBlock.get(blockId);
        if (txs != null) {
            cacheHits++;
            return Collections.unmodifiableList(txs);
        }
        return null;
    }

    /**
     * Removes all cached transactions for a popped off block.
     */
    public synchronized void removeBlockTransactions(long blockId) {
        List<Transaction> txs = byBlock.remove(blockId);
        if (txs != null) {
            blockOrder.remove(blockId);
            Integer height = blockHeight.remove(blockId);
            if (height != null) {
                byHeight.remove(height);
                heightOrder.remove(height);
            }
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
        blockHeight.clear();
        byHeight.clear();
        heightOrder.clear();
        cacheHits = 0;
    }

    public synchronized int getTransactionCount() {
        return byId.size();
    }

    public synchronized long getMinBlockId() {
        return blockOrder.isEmpty() ? 0 : blockOrder.getFirst();
    }

    public synchronized long getMaxBlockId() {
        return blockOrder.isEmpty() ? 0 : blockOrder.getLast();
    }

    public synchronized int getMinTxHeight() {
        return heightOrder.isEmpty() ? 0 : heightOrder.getFirst();
    }

    public synchronized int getMaxTxHeight() {
        return heightOrder.isEmpty() ? 0 : heightOrder.getLast();
    }

    public synchronized long getAndResetCacheHits() {
        long hits = cacheHits;
        cacheHits = 0;
        return hits;
    }
}
