package brs.db.cache;

import brs.Block;
import brs.Signum;
import brs.props.Props;

import java.util.*;

/**
 * Simple cache storing the most recent blocks.
 */
public final class BlockCache {

    private static BlockCache instance;

    public static synchronized BlockCache getInstance() {
        if (instance == null) {
            int blocks = Signum.getPropertyService().getInt(Props.BLOCK_CACHE_BLOCK_COUNT);
            instance = new BlockCache(blocks);
        }
        return instance;
    }

    private final int blocksToCache;
    private final Map<Long, Block> byId = new HashMap<>();
    private final Map<Integer, Block> byHeight = new HashMap<>();
    private final Deque<Long> blockOrder = new ArrayDeque<>();
    private final Deque<Integer> heightOrder = new ArrayDeque<>();
    private long cacheHits;

    private BlockCache(int blocksToCache) {
        this.blocksToCache = blocksToCache;
    }

    /**
     * Adds a block to the cache.
     */
    public synchronized void addBlock(Block block) {
        if (blocksToCache <= 0 || block == null) {
            return;
        }
        long id = block.getId();
        int height = block.getHeight();
        blockOrder.addLast(id);
        heightOrder.addLast(height);
        byId.put(id, block);
        byHeight.put(height, block);
        while (blockOrder.size() > blocksToCache) {
            Long oldId = blockOrder.removeFirst();
            Integer oldHeight = heightOrder.removeFirst();
            byId.remove(oldId);
            byHeight.remove(oldHeight);
        }
    }

    public synchronized Block getById(long id) {
        Block b = byId.get(id);
        if (b != null) {
            cacheHits++;
        }
        return b;
    }

    public synchronized Block getByHeight(int height) {
        Block b = byHeight.get(height);
        if (b != null) {
            cacheHits++;
        }
        return b;
    }

    public synchronized Block getLastBlock() {
        if (blockOrder.isEmpty()) {
            return null;
        }
        Block b = byId.get(blockOrder.getLast());
        if (b != null) {
            cacheHits++;
        }
        return b;
    }

    /**
     * Removes a block from the cache.
     */
    public synchronized void removeBlock(long blockId) {
        Block b = byId.remove(blockId);
        if (b != null) {
            blockOrder.remove(blockId);
            int h = b.getHeight();
            byHeight.remove(h);
            heightOrder.remove(h);
        }
    }

    /**
     * Removes all blocks from the specified height onward.
     */
    public synchronized void removeBlocksFromHeight(int height) {
        while (!heightOrder.isEmpty() && heightOrder.getLast() >= height) {
            int h = heightOrder.removeLast();
            Block b = byHeight.remove(h);
            if (b != null) {
                long id = b.getId();
                byId.remove(id);
                blockOrder.remove(id);
            }
        }
    }

    /**
     * Clears the entire block cache.
     */
    public synchronized void clear() {
        byId.clear();
        byHeight.clear();
        blockOrder.clear();
        heightOrder.clear();
        cacheHits = 0;
    }

    public synchronized int getBlockCount() {
        return byId.size();
    }

    public synchronized int getMinHeight() {
        return heightOrder.isEmpty() ? 0 : heightOrder.getFirst();
    }

    public synchronized int getMaxHeight() {
        return heightOrder.isEmpty() ? 0 : heightOrder.getLast();
    }

    public synchronized long getAndResetCacheHits() {
        long hits = cacheHits;
        cacheHits = 0;
        return hits;
    }
}
