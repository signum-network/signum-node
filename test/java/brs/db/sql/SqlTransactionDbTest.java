package brs.db.sql;

import brs.Blockchain;
import brs.Transaction;
import brs.TransactionType;
import brs.common.QuickMocker;
import brs.common.TestConstants;
import brs.schema.tables.records.TransactionRecord;
import brs.util.Convert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

class SqlTransactionDbTest {

    private static final long STORED_ID = 12_345L;
    private static final byte[] STORED_FULL_HASH = filled(32, (byte) 0x0a);

    @BeforeEach
    void setUp() {
        TransactionType.init(mock(Blockchain.class), QuickMocker.latestValueFluxCapacitor(), null, null, null, null,
                null, null);
    }

    private static byte[] filled(int length, byte value) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, value);
        return bytes;
    }

    /** A signed ordinary payment row whose stored id and full hash don't match its bytes. */
    private static TransactionRecord paymentRecord() {
        TransactionRecord record = new TransactionRecord();
        record.setId(STORED_ID);
        record.setFullHash(STORED_FULL_HASH);
        record.setVersion((byte) 1);
        record.setType((byte) 0);
        record.setSubtype((byte) 0);
        record.setSenderPublicKey(TestConstants.TEST_PUBLIC_KEY_BYTES);
        record.setSenderId(99L);
        record.setRecipientId(7L);
        record.setAmount(5L);
        record.setFee(100_000_000L);
        record.setTimestamp(1000);
        record.setDeadline((short) 1440);
        record.setSignature(filled(64, (byte) 1));
        record.setBlockId(3L);
        record.setHeight(10);
        record.setBlockTimestamp(1010);
        record.setHasMessage(false);
        record.setHasEncryptedMessage(false);
        record.setHasPublicKeyAnnouncement(false);
        record.setHasEncrypttoselfMessage(false);
        record.setEcBlockHeight(1);
        record.setEcBlockId(1L);
        return record;
    }

    @Test
    void loadTransaction_UsesStoredIdAndFullHash() throws Exception {
        // Act
        Transaction transaction = new SqlTransactionDb().loadTransaction(paymentRecord());

        // Assert
        assertEquals(STORED_ID, transaction.getId());
        assertEquals(Convert.toHexString(STORED_FULL_HASH), transaction.getFullHash());
        assertEquals(99L, transaction.getSenderId());
    }

    @Test
    void newTransactionBuilder_RecomputesIdAndFullHashFromTheBytes() throws Exception {
        // Act
        Transaction transaction = SqlTransactionDb.newTransactionBuilder(paymentRecord()).build();

        // Assert
        assertNotEquals(STORED_ID, transaction.getId());
        assertNotEquals(Convert.toHexString(STORED_FULL_HASH), transaction.getFullHash());
        assertEquals(Convert.fullHashToId(transaction.getFullHash()), transaction.getId());
    }

    @Test
    void loadTransaction_GivenNull_ReturnsNull() throws Exception {
        assertEquals(null, new SqlTransactionDb().loadTransaction(null));
    }
}
