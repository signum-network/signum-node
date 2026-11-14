package brs.services.impl.chainscan;

import brs.Transaction;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code TX_SIGNATURE}: every signed transaction has a valid signature, checked exactly as a node
 * checks it before accepting a block ({@link Transaction#verifySignature()}). These findings are the
 * input for the accept list of PR #1095.
 *
 * <p>Rows without a signature are not checked: they are transactions a node derives itself while
 * applying a block (AT payments and AT asset operations, escrow results, subscription payments). They
 * are not part of the block payload. A received transaction is stored with the signature bytes it
 * came with, so an invalid one shows up as {@code TX_SIGNATURE}, and a missing one would break
 * {@code BLOCK_PAYLOAD}.
 *
 * <p>{@code TX_UNSIGNED} ({@code INFO}): a system transaction type that carries a signature.
 *
 * <p>Transactions whose rebuilt bytes don't match the stored hash are skipped: their bytes can't be
 * trusted, and {@code TX_REBUILD_MISMATCH} reports them already.
 */
public class TransactionSignatureCheck implements ChainCheck {

    public static final String TX_SIGNATURE = "TX_SIGNATURE";
    public static final String TX_UNSIGNED = "TX_UNSIGNED";

    @Override
    public String group() {
        return "tx";
    }

    @Override
    public List<Finding> check(BlockContext context) {
        List<Finding> findings = new ArrayList<>();
        for (ScannedTransaction stored : context.transactions()) {
            Transaction transaction = stored.transaction();
            if (transaction == null) {
                continue;
            }
            if (!stored.signed()) {
                continue;
            }
            if (!transaction.getType().isSigned()) {
                findings.add(Finding.ofTransaction(TX_UNSIGNED, Severity.INFO, stored,
                        "system transaction of type " + typeName(transaction) + " has a signature"));
            } else if (TransactionRebuildCheck.mismatch(stored) == null) {
                checkSignature(stored, transaction, findings);
            }
        }
        return findings;
    }

    private static void checkSignature(ScannedTransaction stored, Transaction transaction, List<Finding> findings) {
        try {
            if (!transaction.verifySignature()) {
                findings.add(Finding.ofTransaction(TX_SIGNATURE, Severity.ERROR, stored,
                        "invalid signature, type " + typeName(transaction)));
            }
        } catch (RuntimeException e) {
            findings.add(Finding.ofTransaction(TX_SIGNATURE, Severity.ERROR, stored,
                    "signature verification failed: " + e));
        }
    }

    private static String typeName(Transaction transaction) {
        return transaction.getType().getType() + "/" + transaction.getType().getSubtype();
    }
}
