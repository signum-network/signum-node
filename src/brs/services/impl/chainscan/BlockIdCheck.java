package brs.services.impl.chainscan;

import brs.Account;
import brs.Block;
import brs.util.Convert;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code BLOCK_LOAD}: the block can be rebuilt from its row and its signed transactions.
 * {@code BLOCK_ID}: the id recomputed from the block bytes, and the generator id derived from the
 * generator public key, match the stored values.
 */
public class BlockIdCheck implements ChainCheck {

    public static final String BLOCK_LOAD = "BLOCK_LOAD";
    public static final String BLOCK_ID = "BLOCK_ID";

    @Override
    public String group() {
        return "block";
    }

    @Override
    public List<Finding> check(BlockContext context) {
        ScannedBlock stored = context.block();
        if (stored.loadError() != null) {
            return List.of(Finding.ofBlock(BLOCK_LOAD, Severity.ERROR, stored,
                    "cannot rebuild the block from its row: " + stored.loadError()));
        }
        List<Finding> findings = new ArrayList<>();
        Block block = stored.block();
        try {
            if (block.getId() != stored.storedId()) {
                findings.add(Finding.ofBlock(BLOCK_ID, Severity.ERROR, stored,
                        "recomputed id " + Convert.toUnsignedLong(block.getId()) + " differs from the stored id"));
            }
            long generatorId = Account.getId(block.getGeneratorPublicKey());
            if (generatorId != stored.generatorId()) {
                findings.add(Finding.ofBlock(BLOCK_ID, Severity.ERROR, stored,
                        "generator id " + Convert.toUnsignedLong(generatorId)
                                + " of the public key differs from stored " + Convert.toUnsignedLong(stored.generatorId())));
            }
        } catch (RuntimeException e) {
            findings.add(Finding.ofBlock(BLOCK_ID, Severity.ERROR, stored, "cannot recompute the id: " + e));
        }
        return findings;
    }
}
