package brs.services.impl.chainscan;

import brs.Block;
import org.junit.jupiter.api.Test;

import java.util.List;

import static brs.services.impl.chainscan.ChainScanFixtures.block;
import static brs.services.impl.chainscan.ChainScanFixtures.signedPayment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockIdCheckTest extends BlockChecksTestBase {

    private final BlockIdCheck check = new BlockIdCheck();

    private static BlockContext contextOf(ScannedBlock block) {
        return new BlockContext(block, List.of(), null);
    }

    @Test
    void check_GivenMatchingBlock_ReportsNothing() throws Exception {
        Block block = block(List.of(signedPayment(5L)));
        assertTrue(check.check(contextOf(ChainScanFixtures.scanned(block, 76L, 78L))).isEmpty());
    }

    @Test
    void check_GivenStoredIdDiffers_ReportsBlockId() throws Exception {
        // Arrange
        Block block = block(List.of(signedPayment(5L)));
        ScannedBlock stored = new ScannedBlock(block.getId() + 1, block.getHeight(), block.getTimestamp(), 76L, 78L,
                block.getGeneratorId(), block, null);

        // Act
        List<Finding> findings = check.check(contextOf(stored));

        // Assert
        assertEquals(1, findings.size());
        assertEquals(BlockIdCheck.BLOCK_ID, findings.get(0).checkId());
        assertTrue(findings.get(0).detail().contains("recomputed id"));
    }

    @Test
    void check_GivenStoredGeneratorIdDiffers_ReportsBlockId() throws Exception {
        // Arrange
        Block block = block(List.of());
        ScannedBlock stored = new ScannedBlock(block.getId(), block.getHeight(), block.getTimestamp(), 76L, 78L,
                block.getGeneratorId() + 1, block, null);

        // Act
        List<Finding> findings = check.check(contextOf(stored));

        // Assert
        assertEquals(1, findings.size());
        assertTrue(findings.get(0).detail().contains("generator id"));
    }

    @Test
    void check_GivenBlockThatCannotBeRebuilt_ReportsBlockLoad() {
        // Arrange
        ScannedBlock stored = new ScannedBlock(1L, 5, 100, 0L, 0L, 3L, null, "transactions are not sorted");

        // Act
        List<Finding> findings = check.check(contextOf(stored));

        // Assert
        assertEquals(1, findings.size());
        assertEquals(BlockIdCheck.BLOCK_LOAD, findings.get(0).checkId());
        assertEquals(Severity.ERROR, findings.get(0).severity());
    }
}
