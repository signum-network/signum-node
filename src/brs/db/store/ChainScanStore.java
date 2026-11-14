package brs.db.store;

import brs.services.impl.chainscan.BlockContext;

import java.util.List;

/** Read-only access to the stored chain for the chain scan. */
public interface ChainScanStore {

    /** The height of the highest stored block, or -1 if there is none. */
    int getMaxHeight();

    /**
     * Loads the blocks of a height range with all their transactions, in one query per table.
     *
     * @return one context per stored block from {@code fromHeight} to {@code toHeight}, ordered by
     *         height; each holds the block's transactions in {@code db_id} order and the block at the
     *         height below, if stored
     */
    List<BlockContext> loadBlocks(int fromHeight, int toHeight);
}
