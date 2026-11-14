package brs.services.impl.chainscan;

import brs.Block;
import brs.crypto.Crypto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static brs.services.impl.chainscan.ChainScanFixtures.block;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainLinkCheckTest extends BlockChecksTestBase {

    private static final int HEIGHT = 500;

    private final ChainLinkCheck check = new ChainLinkCheck();

    private static Block blockAt(int height, int timestamp, byte[] previousBlockHash) throws Exception {
        return block(List.of(), ChainScanFixtures.payloadHash(List.of()), 0, 0L, 0L, 0L, null, height, timestamp,
                new byte[32], previousBlockHash);
    }

    /** A previous block and a block that links to it correctly. */
    private record Pair(ScannedBlock previous, ScannedBlock block) {
    }

    private static Pair validPair() throws Exception {
        Block previous = blockAt(HEIGHT - 1, 1000, new byte[32]);
        Block block = blockAt(HEIGHT, 1240, Crypto.sha256().digest(previous.getBytes()));
        ScannedBlock storedPrevious = ChainScanFixtures.scanned(previous, 1L, block.getId());
        return new Pair(storedPrevious, ChainScanFixtures.scanned(block, previous.getId(), 0L));
    }

    private static List<String> ids(List<Finding> findings) {
        return findings.stream().map(Finding::checkId).toList();
    }

    @Test
    void check_GivenCorrectlyLinkedBlock_ReportsNothing() throws Exception {
        Pair pair = validPair();
        assertTrue(check.check(new BlockContext(pair.block(), List.of(), pair.previous())).isEmpty());
    }

    @Test
    void check_GivenMissingPreviousBlock_ReportsChainLink() throws Exception {
        // Act
        List<Finding> findings = check.check(new BlockContext(validPair().block(), List.of(), null));

        // Assert
        assertEquals(List.of(ChainLinkCheck.CHAIN_LINK), ids(findings));
        assertTrue(findings.get(0).detail().contains("no block at height " + (HEIGHT - 1)));
    }

    @Test
    void check_GivenPreviousBlockIdDiffers_ReportsChainLink() throws Exception {
        // Arrange
        Pair pair = validPair();
        ScannedBlock block = pair.block();
        ScannedBlock wrongLink = new ScannedBlock(block.storedId(), block.height(), block.timestamp(),
                block.previousBlockId() + 1, 0L, block.generatorId(), block.block(), null);

        // Act + Assert
        assertEquals(List.of(ChainLinkCheck.CHAIN_LINK),
                ids(check.check(new BlockContext(wrongLink, List.of(), pair.previous()))));
    }

    @Test
    void check_GivenNextBlockIdOfPreviousDiffers_ReportsChainLink() throws Exception {
        // Arrange
        Pair pair = validPair();
        ScannedBlock previous = pair.previous();
        ScannedBlock wrongNext = new ScannedBlock(previous.storedId(), previous.height(), previous.timestamp(),
                previous.previousBlockId(), 12345L, previous.generatorId(), previous.block(), null);

        // Act
        List<Finding> findings = check.check(new BlockContext(pair.block(), List.of(), wrongNext));

        // Assert
        assertEquals(List.of(ChainLinkCheck.CHAIN_LINK), ids(findings));
        assertTrue(findings.get(0).detail().contains("next block id"));
    }

    @Test
    void check_GivenTimestampNotAfterPrevious_ReportsChainLink() throws Exception {
        // Arrange
        Block previous = blockAt(HEIGHT - 1, 1000, new byte[32]);
        Block block = blockAt(HEIGHT, 1000, Crypto.sha256().digest(previous.getBytes()));
        ScannedBlock storedPrevious = ChainScanFixtures.scanned(previous, 1L, block.getId());

        // Act
        List<Finding> findings = check.check(new BlockContext(ChainScanFixtures.scanned(block, previous.getId(), 0L),
                List.of(), storedPrevious));

        // Assert
        assertEquals(List.of(ChainLinkCheck.CHAIN_LINK), ids(findings));
        assertTrue(findings.get(0).detail().contains("timestamp"));
    }

    @Test
    void check_GivenWrongPreviousBlockHash_ReportsPrevBlockHash() throws Exception {
        // Arrange
        Block previous = blockAt(HEIGHT - 1, 1000, new byte[32]);
        Block block = blockAt(HEIGHT, 1240, new byte[32]);
        ScannedBlock storedPrevious = ChainScanFixtures.scanned(previous, 1L, block.getId());

        // Act
        List<Finding> findings = check.check(new BlockContext(ChainScanFixtures.scanned(block, previous.getId(), 0L),
                List.of(), storedPrevious));

        // Assert
        assertEquals(List.of(ChainLinkCheck.PREV_BLOCK_HASH), ids(findings));
    }

    @Test
    void check_GivenGenesisWithoutPrevious_ReportsNothing() {
        ScannedBlock genesis = new ScannedBlock(1L, 0, 0, 0L, 2L, 3L, null, null);
        assertTrue(check.check(new BlockContext(genesis, List.of(), null)).isEmpty());
    }

    @Test
    void check_GivenGenesisWithPrevious_ReportsChainLink() {
        ScannedBlock genesis = new ScannedBlock(1L, 0, 0, 9L, 2L, 3L, null, null);
        assertEquals(List.of(ChainLinkCheck.CHAIN_LINK), ids(check.check(new BlockContext(genesis, List.of(), null))));
    }
}
