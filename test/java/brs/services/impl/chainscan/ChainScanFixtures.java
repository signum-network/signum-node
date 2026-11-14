package brs.services.impl.chainscan;

import brs.Attachment;
import brs.Block;
import brs.Constants;
import brs.SignumException;
import brs.Transaction;
import brs.common.TestConstants;
import brs.crypto.Crypto;
import brs.util.Convert;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

/**
 * Transactions and blocks for the chain scan tests: real transactions signed with the test
 * passphrase, stored as the database would store them.
 */
final class ChainScanFixtures {

    static final int HEIGHT = 1_000;
    static final long BLOCK_ID = 77L;
    static final int BLOCK_TIMESTAMP = 100_000;
    static final long FEE = Constants.ONE_SIGNA;

    private ChainScanFixtures() {
    }

    static Transaction.Builder payment(long amount) {
        return new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, amount, FEE,
                BLOCK_TIMESTAMP - 60, (short) 1440, Attachment.ORDINARY_PAYMENT)
                .recipientId(123L)
                .ecBlockHeight(1)
                .ecBlockId(1L);
    }

    static Transaction signedPayment(long amount) throws SignumException.NotValidException {
        Transaction transaction = payment(amount).build();
        transaction.sign(TestConstants.TEST_SECRET_PHRASE);
        return payment(amount).signature(transaction.getSignature()).build();
    }

    /** A payment whose signature has one bit flipped; id and full hash are computed from that signature. */
    static Transaction paymentWithBrokenSignature(long amount) throws SignumException.NotValidException {
        byte[] signature = signedPayment(amount).getSignature().clone();
        signature[10] ^= 0x01;
        return payment(amount).signature(signature).build();
    }

    /** The row of a transaction as the node stores it, consistent with the transaction's bytes. */
    static ScannedTransaction stored(Transaction transaction) {
        return new ScannedTransaction(1L, transaction.getId(), Convert.parseHexString(transaction.getFullHash()),
                transaction.getSenderId(), BLOCK_ID, HEIGHT, BLOCK_TIMESTAMP, transaction.getSignature() != null,
                transaction, null);
    }

    static ScannedTransaction storedWithFullHash(Transaction transaction, byte[] fullHash) {
        return new ScannedTransaction(1L, transaction.getId(), fullHash, transaction.getSenderId(), BLOCK_ID, HEIGHT,
                BLOCK_TIMESTAMP, true, transaction, null);
    }

    static ScannedBlock scannedBlock() {
        return new ScannedBlock(BLOCK_ID, HEIGHT, BLOCK_TIMESTAMP, 76L, 78L, 5L, null, null);
    }

    static BlockContext context(ScannedTransaction... transactions) {
        return new BlockContext(scannedBlock(), List.of(transactions), null);
    }

    static byte[] payloadHash(List<Transaction> transactions) {
        MessageDigest digest = Crypto.sha256();
        transactions.forEach(transaction -> digest.update(transaction.getBytes()));
        return digest.digest();
    }

    static int payloadLength(List<Transaction> transactions) {
        return transactions.stream().mapToInt(Transaction::getSize).sum();
    }

    /**
     * A version 3 block with the given transactions, payload and totals. Needs
     * {@code Signum.getFluxCapacitor()} to be mocked.
     */
    static Block block(List<Transaction> transactions, byte[] payloadHash, int payloadLength, long totalAmount,
            long totalFee, long cashBack, byte[] ats, int height, int timestamp, byte[] generationSignature,
            byte[] previousBlockHash) throws SignumException.ValidationException {
        byte[] signature = new byte[64];
        Arrays.fill(signature, (byte) 7);
        Block block = new Block(3, timestamp, 76L, totalAmount, totalFee, cashBack, 0L, payloadLength, payloadHash,
                TestConstants.TEST_PUBLIC_KEY_BYTES, generationSignature, signature, previousBlockHash,
                transactions, 42L, ats, height, 1000L);
        block.setHeight(height);
        return block;
    }

    static Block block(List<Transaction> transactions) throws SignumException.ValidationException {
        long amount = transactions.stream().mapToLong(Transaction::getAmountNqt).sum();
        long fee = transactions.stream().mapToLong(Transaction::getFeeNqt).sum();
        return block(transactions, payloadHash(transactions), payloadLength(transactions), amount, fee, 0L, null,
                HEIGHT, BLOCK_TIMESTAMP, new byte[32], new byte[32]);
    }

    /** A scanned block whose stored values match the rebuilt block. */
    static ScannedBlock scanned(Block block, long previousId, long nextId) {
        return new ScannedBlock(block.getId(), block.getHeight(), block.getTimestamp(), previousId, nextId,
                block.getGeneratorId(), block, null);
    }
}
