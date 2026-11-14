package brs.services.impl.chainscan;

import brs.Transaction;
import brs.util.Convert;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code TX_BLOCK_CONSISTENCY}: every transaction stored at a block's height belongs to that block and
 * carries its timestamp. Signed transactions also follow the timestamp rules a node applies when it
 * accepts the block: not more than {@value #MAX_TIMESTAMP_DIFFERENCE} seconds after the block, and
 * not expired at the block's timestamp.
 */
public class TransactionBlockConsistencyCheck implements ChainCheck {

    public static final String TX_BLOCK_CONSISTENCY = "TX_BLOCK_CONSISTENCY";
    /** As {@code BlockchainProcessorImpl.MAX_TIMESTAMP_DIFFERENCE}. */
    static final int MAX_TIMESTAMP_DIFFERENCE = 15;

    @Override
    public String group() {
        return "tx";
    }

    @Override
    public List<Finding> check(BlockContext context) {
        List<Finding> findings = new ArrayList<>();
        ScannedBlock block = context.block();
        for (ScannedTransaction stored : context.transactions()) {
            String problem = problem(block, stored);
            if (problem != null) {
                findings.add(new Finding(TX_BLOCK_CONSISTENCY, Severity.ERROR, block.height(), block.storedId(),
                        stored.storedId(), Convert.toHexString(stored.storedFullHash()), problem));
            }
        }
        return findings;
    }

    private static String problem(ScannedBlock block, ScannedTransaction stored) {
        if (stored.blockId() != block.storedId()) {
            return "stored at height " + block.height() + " but belongs to block "
                    + Convert.toUnsignedLong(stored.blockId());
        }
        if (stored.height() != block.height()) {
            return "stored height " + stored.height() + " differs from the block height";
        }
        if (stored.blockTimestamp() != block.timestamp()) {
            return "block timestamp " + stored.blockTimestamp() + " differs from the block's " + block.timestamp();
        }
        Transaction transaction = stored.transaction();
        if (transaction == null || !stored.signed()) {
            return null;
        }
        if (transaction.getTimestamp() > block.timestamp() + MAX_TIMESTAMP_DIFFERENCE) {
            return "timestamp " + transaction.getTimestamp() + " is after the block timestamp " + block.timestamp();
        }
        if (transaction.getExpiration() < block.timestamp()) {
            return "expired at " + transaction.getExpiration() + ", before the block timestamp " + block.timestamp();
        }
        return null;
    }
}
