package brs.services.impl.chainscan;

import brs.Transaction;
import brs.util.Convert;
import org.junit.jupiter.api.Test;

import java.util.List;

import static brs.services.impl.chainscan.ChainScanFixtures.BLOCK_ID;
import static brs.services.impl.chainscan.ChainScanFixtures.BLOCK_TIMESTAMP;
import static brs.services.impl.chainscan.ChainScanFixtures.HEIGHT;
import static brs.services.impl.chainscan.ChainScanFixtures.context;
import static brs.services.impl.chainscan.ChainScanFixtures.payment;
import static brs.services.impl.chainscan.ChainScanFixtures.signedPayment;
import static brs.services.impl.chainscan.ChainScanFixtures.stored;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionBlockConsistencyCheckTest {

    private final TransactionBlockConsistencyCheck check = new TransactionBlockConsistencyCheck();

    private static ScannedTransaction row(Transaction transaction, long blockId, int height, int blockTimestamp) {
        return new ScannedTransaction(1L, transaction.getId(), Convert.parseHexString(transaction.getFullHash()),
                transaction.getSenderId(), blockId, height, blockTimestamp, true, transaction, null);
    }

    private static Transaction signedAt(int timestamp, short deadline) throws Exception {
        Transaction unsigned = new Transaction.Builder((byte) 1, brs.common.TestConstants.TEST_PUBLIC_KEY_BYTES, 5L,
                ChainScanFixtures.FEE, timestamp, deadline, brs.Attachment.ORDINARY_PAYMENT)
                .recipientId(123L).ecBlockHeight(1).ecBlockId(1L).build();
        unsigned.sign(brs.common.TestConstants.TEST_SECRET_PHRASE);
        return unsigned;
    }

    private void assertOneFinding(ScannedTransaction stored, String detailPart) {
        List<Finding> findings = check.check(context(stored));
        assertEquals(1, findings.size(), findings.toString());
        assertEquals(TransactionBlockConsistencyCheck.TX_BLOCK_CONSISTENCY, findings.get(0).checkId());
        assertTrue(findings.get(0).detail().contains(detailPart), findings.get(0).detail());
    }

    @Test
    void check_GivenConsistentTransaction_ReportsNothing() throws Exception {
        assertTrue(check.check(context(stored(signedPayment(5L)))).isEmpty());
    }

    @Test
    void check_GivenTransactionOfAnotherBlock_ReportsIt() throws Exception {
        assertOneFinding(row(signedPayment(5L), BLOCK_ID + 1, HEIGHT, BLOCK_TIMESTAMP), "belongs to block");
    }

    @Test
    void check_GivenDifferentHeight_ReportsIt() throws Exception {
        assertOneFinding(row(signedPayment(5L), BLOCK_ID, HEIGHT + 1, BLOCK_TIMESTAMP), "stored height");
    }

    @Test
    void check_GivenDifferentBlockTimestamp_ReportsIt() throws Exception {
        assertOneFinding(row(signedPayment(5L), BLOCK_ID, HEIGHT, BLOCK_TIMESTAMP + 1), "block timestamp");
    }

    @Test
    void check_GivenTimestampMoreThan15SecondsAfterBlock_ReportsIt() throws Exception {
        Transaction late = signedAt(BLOCK_TIMESTAMP + TransactionBlockConsistencyCheck.MAX_TIMESTAMP_DIFFERENCE + 1,
                (short) 1440);
        assertOneFinding(stored(late), "is after the block timestamp");
    }

    @Test
    void check_GivenTimestampExactly15SecondsAfterBlock_ReportsNothing() throws Exception {
        Transaction edge = signedAt(BLOCK_TIMESTAMP + TransactionBlockConsistencyCheck.MAX_TIMESTAMP_DIFFERENCE,
                (short) 1440);
        assertTrue(check.check(context(stored(edge))).isEmpty());
    }

    @Test
    void check_GivenTransactionExpiredBeforeBlock_ReportsIt() throws Exception {
        // deadline of 1 minute, created 61 seconds before the block
        assertOneFinding(stored(signedAt(BLOCK_TIMESTAMP - 61, (short) 1)), "expired");
    }

    @Test
    void check_GivenUnsignedRow_ChecksOnlyItsBlock() throws Exception {
        // Arrange: derived transactions get the block's timestamp rules from the node itself
        Transaction unsigned = payment(5L).senderId(99L).build();
        ScannedTransaction stored = new ScannedTransaction(1L, 2L, new byte[32], 99L, BLOCK_ID, HEIGHT,
                BLOCK_TIMESTAMP, false, unsigned, null);

        // Act + Assert
        assertTrue(check.check(context(stored)).isEmpty());
    }
}
