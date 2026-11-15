package brs.services.impl.chainscan;

import java.util.List;

/**
 * What a check sees of one block.
 *
 * @param block        the block
 * @param transactions all transactions stored for the block, in {@code db_id} order
 * @param previous     the block at the height below, or {@code null} if there is none
 */
public record BlockContext(ScannedBlock block, List<ScannedTransaction> transactions, ScannedBlock previous) {

    public BlockContext {
        transactions = List.copyOf(transactions);
    }

    /** The signed transactions, which make up the block payload, in {@code db_id} order. */
    public List<ScannedTransaction> signedTransactions() {
        return transactions.stream().filter(ScannedTransaction::signed).toList();
    }
}
