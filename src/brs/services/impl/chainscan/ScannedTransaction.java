package brs.services.impl.chainscan;

import brs.Transaction;

/**
 * A transaction as stored in the database, together with the transaction rebuilt from it.
 *
 * @param dbId           the row's {@code db_id}, which gives the order of the transactions in a block
 * @param storedId       the stored {@code id}
 * @param storedFullHash the stored {@code full_hash}
 * @param storedSenderId the stored {@code sender_id}
 * @param blockId        the stored {@code block_id}
 * @param height         the stored {@code height}
 * @param blockTimestamp the stored {@code block_timestamp}
 * @param signed         whether the row has a signature
 * @param transaction    the transaction rebuilt from the row without stored id and full hash, or
 *                       {@code null} if it could not be rebuilt
 * @param loadError      why the transaction could not be rebuilt, or {@code null}
 */
public record ScannedTransaction(long dbId, long storedId, byte[] storedFullHash, long storedSenderId,
        long blockId, int height, int blockTimestamp, boolean signed, Transaction transaction, String loadError) {
}
