package brs.services.impl.chainscan;

import brs.util.Convert;

/**
 * One rule violation found by the chain scan.
 *
 * @param checkId       the id of the rule, e.g. {@code TX_SIGNATURE}
 * @param severity      how serious it is
 * @param height        height of the block
 * @param blockId       id of the block as stored in the database
 * @param transactionId id of the transaction as stored, or {@code null} for block findings
 * @param fullHash      full hash of the transaction as stored (hex), or {@code null} for block findings
 * @param detail        a human readable description
 */
public record Finding(String checkId, Severity severity, int height, long blockId, Long transactionId,
        String fullHash, String detail) {

    public static Finding ofBlock(String checkId, Severity severity, ScannedBlock block, String detail) {
        return new Finding(checkId, severity, block.height(), block.storedId(), null, null, detail);
    }

    public static Finding ofTransaction(String checkId, Severity severity, ScannedTransaction transaction,
            String detail) {
        return new Finding(checkId, severity, transaction.height(), transaction.blockId(), transaction.storedId(),
                Convert.toHexString(transaction.storedFullHash()), detail);
    }
}
