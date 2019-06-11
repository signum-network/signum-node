package brs.grpc;

import brs.Genesis;
import brs.TransactionType;
import brs.grpc.proto.BrsApi;
import com.google.protobuf.Empty;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(JUnit4.class)
public class GetConstantsHandlerTest extends AbstractGrpcTest {

    @Before
    public void setUpGetConstantsHandlerTest() throws IOException {
        defaultBrsService();
    }

    @Test
    public void testGetConstantsHandler() {
        BrsApi.Constants constants = brsService.getConstants(Empty.getDefaultInstance());
        assertEquals(Genesis.CREATOR_ID, constants.getGenesisAccount());
        assertEquals(Genesis.GENESIS_BLOCK_ID, constants.getGenesisBlock());
        // TODO check max block size / payload length
        assertEquals(TransactionType.getTransactionTypes().size(), constants.getTransactionTypesList().size());
        constants.getTransactionTypesList().forEach(transactionType -> {
            Map<Byte, TransactionType> subtypes = TransactionType.getTransactionTypes().get((byte) transactionType.getType());
            assertNotNull("Transaction type " + transactionType.getType() + " does not exist", subtypes);
            assertEquals(TransactionType.getTypeDescription((byte) transactionType.getType()), transactionType.getDescription());
            assertEquals(subtypes.size(), transactionType.getSubtypesList().size());
            transactionType.getSubtypesList().forEach(subtype -> {
                TransactionType transactionSubtype = subtypes.get((byte) subtype.getSubtype());
                assertNotNull("Transaction subtype " + transactionType.getType() + "," + subtype.getSubtype() + " does not exist", transactionSubtype);
                // Don't assume that its position in the map and its actual type are the same.
                assertEquals(transactionSubtype.getSubtype(), subtype.getSubtype());
                assertEquals(transactionSubtype.getDescription(), subtype.getDescription());
            });
        });
    }
}
