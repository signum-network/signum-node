package brs.services

import brs.entity.Block

interface BlockService {
    /**
     * Checks whether a block is verified, and verifies it if it is not,
     * before removing it from the download cache unverified list.
     * @param scoopData Pre-calculated scoop data for this block to use to verify the block's PoC proof. Will be calculated if null
     * @param warnIfNotVerified Emit a logger debug warning if the block was not pre-verified yet
     */
    fun preVerify(block: Block, scoopData: ByteArray? = null, warnIfNotVerified: Boolean = false)

    /**
     * TODO
     */
    fun getBlockReward(block: Block): Long

    /**
     * TODO
     */
    fun calculateBaseTarget(block: Block, previousBlock: Block)

    /**
     * TODO
     */
    fun setPrevious(block: Block, previousBlock: Block?)

    /**
     * TODO
     */
    fun verifyGenerationSignature(block: Block): Boolean

    /**
     * TODO
     */
    fun verifyBlockSignature(block: Block): Boolean

    /**
     * TODO
     */
    fun apply(block: Block)

    /**
     * TODO
     */
    fun getScoopNum(block: Block): Int
}
