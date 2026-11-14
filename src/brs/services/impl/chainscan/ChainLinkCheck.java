package brs.services.impl.chainscan;

import brs.Block;
import brs.crypto.Crypto;
import brs.util.Convert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code CHAIN_LINK}: each block links to the block at the height below, in both directions, and is
 * later than it. {@code PREV_BLOCK_HASH}: for block versions above 1, the stored previous block hash
 * is the SHA-256 of the previous block's bytes.
 */
public class ChainLinkCheck implements ChainCheck {

    public static final String CHAIN_LINK = "CHAIN_LINK";
    public static final String PREV_BLOCK_HASH = "PREV_BLOCK_HASH";

    @Override
    public String group() {
        return "block";
    }

    @Override
    public List<Finding> check(BlockContext context) {
        ScannedBlock stored = context.block();
        ScannedBlock previous = context.previous();
        if (stored.height() == 0) {
            return stored.previousBlockId() == 0 ? List.of()
                    : List.of(Finding.ofBlock(CHAIN_LINK, Severity.ERROR, stored, "genesis block has a previous block"));
        }
        if (previous == null) {
            return List.of(Finding.ofBlock(CHAIN_LINK, Severity.ERROR, stored,
                    "no block at height " + (stored.height() - 1)));
        }
        List<Finding> findings = new ArrayList<>();
        if (stored.previousBlockId() != previous.storedId()) {
            findings.add(Finding.ofBlock(CHAIN_LINK, Severity.ERROR, stored, "previous block id "
                    + Convert.toUnsignedLong(stored.previousBlockId()) + " is not the block at the height below, "
                    + Convert.toUnsignedLong(previous.storedId())));
        }
        if (previous.nextBlockId() != stored.storedId()) {
            findings.add(Finding.ofBlock(CHAIN_LINK, Severity.ERROR, stored, "next block id "
                    + Convert.toUnsignedLong(previous.nextBlockId()) + " of the block below doesn't point to this block"));
        }
        if (stored.timestamp() <= previous.timestamp()) {
            findings.add(Finding.ofBlock(CHAIN_LINK, Severity.ERROR, stored, "timestamp " + stored.timestamp()
                    + " is not after the previous block's " + previous.timestamp()));
        }
        checkPreviousBlockHash(stored, previous, findings);
        return findings;
    }

    private static void checkPreviousBlockHash(ScannedBlock stored, ScannedBlock previous, List<Finding> findings) {
        Block block = stored.block();
        if (block == null || previous.block() == null || block.getVersion() == 1) {
            return;
        }
        try {
            byte[] expected = Crypto.sha256().digest(previous.block().getBytes());
            if (!Arrays.equals(expected, block.getPreviousBlockHash())) {
                findings.add(Finding.ofBlock(PREV_BLOCK_HASH, Severity.ERROR, stored,
                        "previous block hash doesn't match the previous block's bytes"));
            }
        } catch (RuntimeException e) {
            findings.add(Finding.ofBlock(PREV_BLOCK_HASH, Severity.ERROR, stored, "cannot hash the previous block: " + e));
        }
    }
}
