package brs.services.impl.chainscan;

import brs.Block;

/**
 * A block as stored in the database, together with the block rebuilt from it.
 *
 * @param storedId        the stored {@code id}
 * @param height          the stored {@code height}
 * @param timestamp       the stored {@code timestamp}
 * @param previousBlockId the stored {@code previous_block_id}, 0 if none
 * @param nextBlockId     the stored {@code next_block_id}, 0 if none
 * @param generatorId     the stored {@code generator_id}
 * @param block           the block rebuilt from the row and its signed transactions, without the stored
 *                        id, or {@code null} if it could not be rebuilt
 * @param loadError       why the block could not be rebuilt, or {@code null}
 */
public record ScannedBlock(long storedId, int height, int timestamp, long previousBlockId, long nextBlockId,
        long generatorId, Block block, String loadError) {
}
