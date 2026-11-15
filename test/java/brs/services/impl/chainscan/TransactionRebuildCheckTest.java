package brs.services.impl.chainscan;

import brs.Transaction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static brs.services.impl.chainscan.ChainScanFixtures.BLOCK_ID;
import static brs.services.impl.chainscan.ChainScanFixtures.BLOCK_TIMESTAMP;
import static brs.services.impl.chainscan.ChainScanFixtures.HEIGHT;
import static brs.services.impl.chainscan.ChainScanFixtures.context;
import static brs.services.impl.chainscan.ChainScanFixtures.payment;
import static brs.services.impl.chainscan.ChainScanFixtures.signedPayment;
import static brs.services.impl.chainscan.ChainScanFixtures.stored;
import static brs.services.impl.chainscan.ChainScanFixtures.storedWithFullHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionRebuildCheckTest {

    private final TransactionRebuildCheck check = new TransactionRebuildCheck();

    @Test
    void check_GivenMatchingRow_ReportsNothing() throws Exception {
        assertTrue(check.check(context(stored(signedPayment(5L)))).isEmpty());
    }

    @Test
    void check_GivenStoredFullHashDiffers_ReportsMismatch() throws Exception {
        // Act
        List<Finding> findings = check.check(context(storedWithFullHash(signedPayment(5L), new byte[32])));

        // Assert
        assertEquals(1, findings.size());
        assertEquals(TransactionRebuildCheck.TX_REBUILD_MISMATCH, findings.get(0).checkId());
        assertEquals(Severity.ERROR, findings.get(0).severity());
    }

    @Test
    void check_GivenStoredSenderIdDiffers_ReportsMismatch() throws Exception {
        // Arrange
        Transaction transaction = signedPayment(5L);
        ScannedTransaction stored = new ScannedTransaction(1L, transaction.getId(),
                brs.util.Convert.parseHexString(transaction.getFullHash()), 12345L, BLOCK_ID, HEIGHT, BLOCK_TIMESTAMP,
                true, transaction, null);

        // Act
        List<Finding> findings = check.check(context(stored));

        // Assert
        assertEquals(1, findings.size());
        assertTrue(findings.get(0).detail().contains("sender id"));
    }

    @Test
    void check_GivenRowThatCannotBeRebuilt_ReportsTxLoad() {
        // Arrange
        ScannedTransaction stored = new ScannedTransaction(1L, 2L, new byte[32], 3L, BLOCK_ID, HEIGHT,
                BLOCK_TIMESTAMP, true, null, "unknown transaction type");

        // Act
        List<Finding> findings = check.check(context(stored));

        // Assert
        assertEquals(1, findings.size());
        assertEquals(TransactionRebuildCheck.TX_LOAD, findings.get(0).checkId());
        assertEquals(2L, findings.get(0).transactionId());
    }

    @Test
    void check_GivenUnsignedRow_DoesNotCompare() throws Exception {
        // Arrange: system transaction bytes depend on the node's height, the stored id can't be recomputed
        Transaction unsigned = payment(5L).senderId(99L).build();
        ScannedTransaction stored = new ScannedTransaction(1L, 2L, new byte[32], 99L, BLOCK_ID, HEIGHT,
                BLOCK_TIMESTAMP, false, unsigned, null);

        // Act + Assert
        assertTrue(check.check(context(stored)).isEmpty());
    }
}
