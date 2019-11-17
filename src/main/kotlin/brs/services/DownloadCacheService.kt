package brs.services

import brs.entity.Block
import brs.util.sync.Mutex
import java.math.BigInteger

interface DownloadCacheService {
    /**
     * TODO
     */
    val mutex: Mutex

    /**
     * TODO
     */
    val isFull: Boolean

    /**
     * TODO
     */
    val cumulativeDifficulty: BigInteger

    /**
     * TODO
     */
    val firstUnverifiedBlock: Block?

    /**
     * TODO
     */
    val unverifiedSize: Int

    /**
     * TODO
     */
    val blockCacheSize: Int

    /**
     * TODO
     */
    val forkList: List<Block>

    /**
     * TODO
     */
    val lastBlock: Block?

    /**
     * TODO
     */
    fun lockCache()

    /**
     * TODO
     */
    fun unlockCache()

    /**
     * TODO
     */
    fun getUnverifiedBlockIdFromPos(pos: Int): Long

    /**
     * Removes a block from the unverified block list
     */
    fun removeUnverified(blockId: Long)

    /**
     * TODO
     */
    fun removeUnverifiedBatch(blocks: Collection<Block>)

    /**
     * TODO
     */
    fun resetCache()

    /**
     * TODO
     */
    fun getBlock(blockId: Long): Block?

    /**
     * TODO
     */
    fun getNextBlock(prevBlockId: Long): Block?

    /**
     * TODO
     */
    fun hasBlock(blockId: Long): Boolean

    /**
     * TODO
     */
    fun canBeFork(oldBlockId: Long): Boolean

    /**
     * TODO
     */
    fun addBlock(block: Block): Boolean

    /**
     * TODO
     */
    fun addForkBlock(block: Block)

    /**
     * TODO
     */
    fun resetForkBlocks()

    /**
     * TODO
     */
    fun removeBlock(block: Block)

    /**
     * TODO
     */
    fun getPoCVersion(blockId: Long): Int

    /**
     * TODO
     */
    fun getLastBlockId(): Long

    /**
     * TODO
     */
    fun size(): Int
}