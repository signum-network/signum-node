package brs.services.impl.chainscan;

import brs.Block;
import brs.Transaction;
import brs.common.QuickMocker;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static brs.services.impl.chainscan.ChainScanFixtures.BLOCK_TIMESTAMP;
import static brs.services.impl.chainscan.ChainScanFixtures.HEIGHT;
import static brs.services.impl.chainscan.ChainScanFixtures.block;
import static brs.services.impl.chainscan.ChainScanFixtures.payloadHash;
import static brs.services.impl.chainscan.ChainScanFixtures.payloadLength;
import static brs.services.impl.chainscan.ChainScanFixtures.signedPayment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockPayloadCheckTest extends BlockChecksTestBase {

    private static final int CASH_BACK_FACTOR = 4;

    private final FluxCapacitor noSmartFees = QuickMocker.fluxCapacitorEnabledFunctionalities();
    private final FluxCapacitor smartFees = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.SMART_FEES);

    /** Two signed payments in id order, as a block holds them. */
    private static List<Transaction> transactions() throws Exception {
        List<Transaction> transactions = new ArrayList<>(List.of(signedPayment(5L), signedPayment(7L)));
        transactions.sort(Comparator.comparingLong(Transaction::getId));
        return transactions;
    }

    private static BlockContext contextOf(Block block, List<Transaction> transactions) {
        return new BlockContext(ChainScanFixtures.scanned(block, 76L, 78L),
                transactions.stream().map(ChainScanFixtures::stored).toList(), null);
    }

    private static List<String> ids(List<Finding> findings) {
        return findings.stream().map(Finding::checkId).toList();
    }

    @Test
    void check_GivenMatchingPayload_ReportsNothing() throws Exception {
        List<Transaction> transactions = transactions();
        Block block = block(transactions);
        assertTrue(new BlockPayloadCheck(noSmartFees, CASH_BACK_FACTOR).check(contextOf(block, transactions)).isEmpty());
    }

    @Test
    void check_GivenTransactionsStoredInAnotherOrder_ReportsPayloadHash() throws Exception {
        // Arrange: the payload hash covers the transactions in block order
        List<Transaction> transactions = transactions();
        Block block = block(transactions);
        List<Transaction> reversed = new ArrayList<>(transactions);
        java.util.Collections.reverse(reversed);
        BlockContext context = new BlockContext(ChainScanFixtures.scanned(block, 76L, 78L),
                reversed.stream().map(ChainScanFixtures::stored).toList(), null);

        // Act + Assert
        assertEquals(List.of(BlockPayloadCheck.BLOCK_PAYLOAD),
                ids(new BlockPayloadCheck(noSmartFees, CASH_BACK_FACTOR).check(context)));
    }

    @Test
    void check_GivenTotalsBelowTransactionSums_ReportsPayload() throws Exception {
        // Arrange
        List<Transaction> transactions = transactions();
        Block block = block(transactions, payloadHash(transactions), payloadLength(transactions), 11L,
                2 * ChainScanFixtures.FEE, 0L, null, HEIGHT, BLOCK_TIMESTAMP, new byte[32], new byte[32]);

        // Act
        List<Finding> findings = new BlockPayloadCheck(noSmartFees, CASH_BACK_FACTOR).check(contextOf(block, transactions));

        // Assert
        assertEquals(List.of(BlockPayloadCheck.BLOCK_PAYLOAD), ids(findings));
        assertTrue(findings.get(0).detail().contains("exceed"));
    }

    @Test
    void check_GivenWrongCashBackWithSmartFees_ReportsPayload() throws Exception {
        // Arrange
        List<Transaction> transactions = transactions();
        Block block = block(transactions);

        // Act
        List<Finding> findings = new BlockPayloadCheck(smartFees, CASH_BACK_FACTOR).check(contextOf(block, transactions));

        // Assert: the block has no cash back, but 2 fees / 4 are due
        assertEquals(List.of(BlockPayloadCheck.BLOCK_PAYLOAD), ids(findings));
        assertTrue(findings.get(0).detail().contains("cash back"));
    }

    @Test
    void check_GivenCorrectCashBackWithSmartFees_ReportsNothing() throws Exception {
        // Arrange
        List<Transaction> transactions = transactions();
        long cashBack = 2 * (ChainScanFixtures.FEE / CASH_BACK_FACTOR);
        Block block = block(transactions, payloadHash(transactions), payloadLength(transactions), 12L,
                2 * ChainScanFixtures.FEE, cashBack, null, HEIGHT, BLOCK_TIMESTAMP, new byte[32], new byte[32]);

        // Act + Assert
        assertTrue(new BlockPayloadCheck(smartFees, CASH_BACK_FACTOR).check(contextOf(block, transactions)).isEmpty());
    }

    @Test
    void check_GivenPayloadLengthWithAtBytes_ReportsNothing() throws Exception {
        // Arrange: one AT entry of 24 bytes counts into the payload length
        byte[] ats = new byte[24];
        Block block = block(List.of(), payloadHash(List.of()), 24, 0L, 0L, 0L, ats, HEIGHT, BLOCK_TIMESTAMP,
                new byte[32], new byte[32]);

        // Act + Assert
        assertTrue(new BlockPayloadCheck(noSmartFees, CASH_BACK_FACTOR).check(contextOf(block, List.of())).isEmpty());
    }

    @Test
    void check_GivenWrongPayloadLength_ReportsWarning() throws Exception {
        // Arrange
        Block block = block(List.of(), payloadHash(List.of()), 24, 0L, 0L, 0L, null, HEIGHT, BLOCK_TIMESTAMP,
                new byte[32], new byte[32]);

        // Act
        List<Finding> findings = new BlockPayloadCheck(noSmartFees, CASH_BACK_FACTOR).check(contextOf(block, List.of()));

        // Assert: nodes only check the length against the maximum
        assertEquals(List.of(BlockPayloadCheck.BLOCK_PAYLOAD_LENGTH), ids(findings));
        assertEquals(Severity.WARN, findings.get(0).severity());
    }

    @Test
    void check_GivenUnsignedRows_IgnoresThemForThePayload() throws Exception {
        // Arrange: derived transactions are stored for the block but are not part of its payload
        List<Transaction> transactions = transactions();
        Block block = block(transactions);
        List<ScannedTransaction> rows = new ArrayList<>(transactions.stream().map(ChainScanFixtures::stored).toList());
        rows.add(new ScannedTransaction(9L, 2L, new byte[32], 99L, ChainScanFixtures.BLOCK_ID, HEIGHT, BLOCK_TIMESTAMP,
                false, ChainScanFixtures.payment(1L).senderId(99L).build(), null));
        BlockContext context = new BlockContext(ChainScanFixtures.scanned(block, 76L, 78L), rows, null);

        // Act + Assert
        assertTrue(new BlockPayloadCheck(noSmartFees, CASH_BACK_FACTOR).check(context).isEmpty());
    }

    @Test
    void check_GivenSignedTransactionThatCouldNotBeRebuilt_ReportsNothing() throws Exception {
        // Arrange: TX_LOAD and BLOCK_LOAD report it
        Block block = block(List.of());
        ScannedTransaction broken = new ScannedTransaction(1L, 2L, new byte[32], 3L, ChainScanFixtures.BLOCK_ID,
                HEIGHT, BLOCK_TIMESTAMP, true, null, "broken");
        BlockContext context = new BlockContext(ChainScanFixtures.scanned(block, 76L, 78L), List.of(broken), null);

        // Act + Assert
        assertTrue(new BlockPayloadCheck(noSmartFees, CASH_BACK_FACTOR).check(context).isEmpty());
    }
}
