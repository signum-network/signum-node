package brs.services.impl.chainscan;

import brs.Account;
import brs.Transaction;
import brs.util.Convert;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code TX_LOAD}: the transaction can be rebuilt from its row. {@code TX_REBUILD_MISMATCH}: the id
 * and full hash recomputed from the rebuilt bytes, and the sender id derived from the public key,
 * match the stored values.
 *
 * <p>Only signed transactions are compared. The bytes of system transactions (AT payments, escrow
 * results, subscription payments) depend on the height the node is at, see
 * {@link Transaction#getBytes()}, so their stored ids cannot be recomputed offline.
 */
public class TransactionRebuildCheck implements ChainCheck {

    public static final String TX_LOAD = "TX_LOAD";
    public static final String TX_REBUILD_MISMATCH = "TX_REBUILD_MISMATCH";

    @Override
    public String group() {
        return "tx";
    }

    @Override
    public List<Finding> check(BlockContext context) {
        List<Finding> findings = new ArrayList<>();
        for (ScannedTransaction stored : context.transactions()) {
            if (stored.loadError() != null) {
                findings.add(Finding.ofTransaction(TX_LOAD, Severity.ERROR, stored,
                        "cannot rebuild the transaction from its row: " + stored.loadError()));
                continue;
            }
            String mismatch = mismatch(stored);
            if (mismatch != null) {
                findings.add(Finding.ofTransaction(TX_REBUILD_MISMATCH, Severity.ERROR, stored, mismatch));
            }
        }
        return findings;
    }

    /**
     * Compares a signed transaction rebuilt from its row with the stored id, full hash and sender id.
     *
     * @return a description of the mismatch, or {@code null} if the values match or the transaction
     *         is not signed
     */
    static String mismatch(ScannedTransaction stored) {
        Transaction transaction = stored.transaction();
        if (transaction == null || !stored.signed()) {
            return null;
        }
        try {
            String storedFullHash = Convert.toHexString(stored.storedFullHash());
            if (transaction.getId() != stored.storedId() || !transaction.getFullHash().equals(storedFullHash)) {
                return "recomputed full hash " + transaction.getFullHash() + " differs from stored "
                        + storedFullHash;
            }
            long senderId = Account.getId(transaction.getSenderPublicKey());
            if (senderId != stored.storedSenderId()) {
                return "sender id " + Convert.toUnsignedLong(senderId) + " of the public key differs from stored "
                        + Convert.toUnsignedLong(stored.storedSenderId());
            }
            return null;
        } catch (RuntimeException e) {
            return "cannot recompute the id: " + e;
        }
    }
}
