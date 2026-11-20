package brs.services.impl.chainscan;

import brs.Attachment;
import brs.Transaction;
import brs.common.TestConstants;
import org.junit.jupiter.api.Test;

import java.util.List;

import static brs.services.impl.chainscan.ChainScanFixtures.context;
import static brs.services.impl.chainscan.ChainScanFixtures.payment;
import static brs.services.impl.chainscan.ChainScanFixtures.paymentWithBrokenSignature;
import static brs.services.impl.chainscan.ChainScanFixtures.signedPayment;
import static brs.services.impl.chainscan.ChainScanFixtures.stored;
import static brs.services.impl.chainscan.ChainScanFixtures.storedWithFullHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionSignatureCheckTest {

    private final TransactionSignatureCheck check = new TransactionSignatureCheck();

    @Test
    void check_GivenValidSignedTransaction_ReportsNothing() throws Exception {
        assertTrue(check.check(context(stored(signedPayment(5L)))).isEmpty());
    }

    @Test
    void check_GivenBrokenSignature_ReportsTxSignatureError() throws Exception {
        // Arrange: the block carries the transaction as received, id and full hash match its bytes
        ScannedTransaction stored = stored(paymentWithBrokenSignature(5L));

        // Act
        List<Finding> findings = check.check(context(stored));

        // Assert
        assertEquals(1, findings.size());
        Finding finding = findings.get(0);
        assertEquals(TransactionSignatureCheck.TX_SIGNATURE, finding.checkId());
        assertEquals(Severity.ERROR, finding.severity());
        assertEquals(stored.storedId(), finding.transactionId());
    }

    @Test
    void check_GivenTamperedAmountWithOriginalSignature_ReportsTxSignatureError() throws Exception {
        // Arrange
        byte[] signature = signedPayment(5L).getSignature();
        Transaction tampered = payment(6L).signature(signature).build();

        // Act
        List<Finding> findings = check.check(context(stored(tampered)));

        // Assert
        assertEquals(List.of(TransactionSignatureCheck.TX_SIGNATURE), findings.stream().map(Finding::checkId).toList());
    }

    @Test
    void check_GivenRebuildMismatch_SkipsTheSignature() throws Exception {
        // Arrange: bytes don't match the stored hash, TX_REBUILD_MISMATCH reports it
        ScannedTransaction stored = storedWithFullHash(paymentWithBrokenSignature(5L), new byte[32]);

        // Act + Assert
        assertTrue(check.check(context(stored)).isEmpty());
    }

    @Test
    void check_GivenUnsignedRow_ReportsNothing() throws Exception {
        // Arrange: e.g. an asset transfer an AT made, derived by the node, not part of the payload
        Transaction transaction = payment(5L).senderId(99L).build();
        ScannedTransaction stored = new ScannedTransaction(1L, 2L, new byte[32], 99L, ChainScanFixtures.BLOCK_ID,
                ChainScanFixtures.HEIGHT, ChainScanFixtures.BLOCK_TIMESTAMP, false, transaction, null);

        // Act + Assert
        assertTrue(check.check(context(stored)).isEmpty());
    }

    @Test
    void check_GivenSystemTransactionWithSignature_ReportsInfo() throws Exception {
        // Arrange
        Transaction atPayment = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, 5L, 0L,
                1000, (short) 1440, Attachment.AT_PAYMENT).signature(new byte[64]).senderId(99L).build();
        ScannedTransaction stored = new ScannedTransaction(1L, 2L, new byte[32], 99L, ChainScanFixtures.BLOCK_ID,
                ChainScanFixtures.HEIGHT, ChainScanFixtures.BLOCK_TIMESTAMP, true, atPayment, null);

        // Act
        List<Finding> findings = check.check(context(stored));

        // Assert
        assertEquals(1, findings.size());
        assertEquals(TransactionSignatureCheck.TX_UNSIGNED, findings.get(0).checkId());
        assertEquals(Severity.INFO, findings.get(0).severity());
    }

    @Test
    void check_GivenTransactionThatCouldNotBeRebuilt_ReportsNothing() {
        // Arrange: TX_LOAD reports it
        ScannedTransaction stored = new ScannedTransaction(1L, 2L, new byte[32], 3L, ChainScanFixtures.BLOCK_ID,
                ChainScanFixtures.HEIGHT, ChainScanFixtures.BLOCK_TIMESTAMP, true, null, "broken");

        // Act + Assert
        assertTrue(check.check(context(stored)).isEmpty());
    }
}
