package brs.db.sql;

import brs.Blockchain;
import brs.Signum;
import brs.TransactionType;
import brs.common.QuickMocker;
import brs.fluxcapacitor.FluxCapacitor;
import brs.props.PropertyService;
import brs.props.Props;
import brs.schema.tables.records.BlockRecord;
import brs.schema.tables.records.TransactionRecord;
import brs.services.impl.chainscan.BlockContext;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static brs.schema.Tables.BLOCK;
import static brs.schema.Tables.TRANSACTION;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** {@link SqlChainScanStore} and {@link ChainScanDatabase} against a SQLite file. */
class SqlChainScanStoreTest {

    @TempDir
    Path folder;

    private String url;
    private MockedStatic<Signum> signum;

    @BeforeEach
    void setUp() throws SQLException {
        FluxCapacitor fluxCapacitor = QuickMocker.latestValueFluxCapacitor();
        signum = mockStatic(Signum.class);
        signum.when(Signum::getFluxCapacitor).thenReturn(fluxCapacitor);
        TransactionType.init(mock(Blockchain.class), fluxCapacitor, null, null, null, null, null, null);
        url = "jdbc:sqlite:file:" + folder.resolve("chain.db");
        try (Connection connection = DriverManager.getConnection(url)) {
            DSLContext ctx = DSL.using(connection, SQLDialect.SQLITE, new Settings().withRenderSchema(false));
            ctx.createTable(BLOCK).columns(BLOCK.fields()).execute();
            ctx.createTable(TRANSACTION).columns(TRANSACTION.fields()).execute();
            for (int height = 0; height <= 4; height++) {
                ctx.executeInsert(block(ctx, height));
            }
            // inserted out of db_id order on purpose: the store must order by db_id
            ctx.executeInsert(transaction(ctx, 21L, 2, 201L));
            ctx.executeInsert(transaction(ctx, 20L, 2, 200L));
            ctx.executeInsert(transaction(ctx, 30L, 3, 300L));
        }
    }

    @AfterEach
    void tearDown() {
        signum.close();
    }

    private static long blockId(int height) {
        return 1000L + height;
    }

    private static BlockRecord block(DSLContext ctx, int height) {
        BlockRecord record = ctx.newRecord(BLOCK);
        record.setDbId((long) height + 1);
        record.setId(blockId(height));
        record.setVersion(3);
        record.setTimestamp(height * 240);
        record.setPreviousBlockId(height == 0 ? null : blockId(height - 1));
        record.setNextBlockId(height == 4 ? null : blockId(height + 1));
        record.setTotalAmount(0L);
        record.setTotalFee(0L);
        record.setTotalFeeCashBack(0L);
        record.setTotalFeeBurnt(0L);
        record.setPayloadLength(0);
        record.setGeneratorPublicKey(new byte[32]);
        record.setPreviousBlockHash(new byte[32]);
        record.setCumulativeDifficulty(BigInteger.ONE.toByteArray());
        record.setBaseTarget(1000L);
        record.setHeight(height);
        record.setGenerationSignature(new byte[32]);
        record.setBlockSignature(new byte[64]);
        record.setPayloadHash(sha256());
        record.setGeneratorId(5L);
        record.setNonce(0L);
        return record;
    }

    /** An unsigned AT payment row: it is no part of the payload, so the block still rebuilds. */
    private static TransactionRecord transaction(DSLContext ctx, long dbId, int height, long id) {
        TransactionRecord record = ctx.newRecord(TRANSACTION);
        record.setDbId(dbId);
        record.setId(id);
        record.setDeadline((short) 1440);
        record.setSenderPublicKey(new byte[32]);
        record.setRecipientId(7L);
        record.setAmount(1L);
        record.setFee(0L);
        record.setHeight(height);
        record.setBlockId(blockId(height));
        record.setTimestamp(height * 240);
        record.setType((byte) 22);
        record.setSubtype((byte) 1);
        record.setSenderId(99L);
        record.setBlockTimestamp(height * 240);
        record.setFullHash(new byte[32]);
        record.setVersion((byte) 1);
        record.setHasMessage(false);
        record.setHasEncryptedMessage(false);
        record.setHasPublicKeyAnnouncement(false);
        record.setHasEncrypttoselfMessage(false);
        record.setEcBlockHeight(0);
        record.setCashBackId(0L);
        return record;
    }

    private static byte[] sha256() {
        try {
            return MessageDigest.getInstance("SHA-256").digest();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ChainScanDatabase openDatabase() {
        PropertyService properties = mock(PropertyService.class);
        when(properties.getString(Props.DB_URL)).thenReturn(url);
        return ChainScanDatabase.open(properties, 2);
    }

    @Test
    void getMaxHeight_ReturnsHighestStoredHeight() {
        try (ChainScanDatabase database = openDatabase()) {
            assertEquals(4, new SqlChainScanStore(database.getDataSource(), database.getDialect()).getMaxHeight());
        }
    }

    @Test
    void loadBlocks_GivenRange_ReturnsBlocksInOrderWithTheirPreviousBlock() {
        try (ChainScanDatabase database = openDatabase()) {
            // Act
            List<BlockContext> contexts = new SqlChainScanStore(database.getDataSource(), database.getDialect())
                    .loadBlocks(2, 3);

            // Assert
            assertEquals(List.of(2, 3), contexts.stream().map(c -> c.block().height()).toList());
            assertEquals(blockId(1), contexts.get(0).previous().storedId());
            assertEquals(blockId(2), contexts.get(1).previous().storedId());
            assertNull(contexts.get(0).block().loadError());
            assertEquals(2, contexts.get(0).block().block().getHeight());
        }
    }

    @Test
    void loadBlocks_OrdersTransactionsByDbId() {
        try (ChainScanDatabase database = openDatabase()) {
            // Act
            BlockContext context = new SqlChainScanStore(database.getDataSource(), database.getDialect())
                    .loadBlocks(2, 2).get(0);

            // Assert
            assertEquals(List.of(200L, 201L), context.transactions().stream().map(t -> t.storedId()).toList());
            assertTrue(context.transactions().stream().noneMatch(t -> t.signed()));
            assertTrue(context.transactions().stream().allMatch(t -> t.loadError() == null));
        }
    }

    @Test
    void loadBlocks_GivenGenesis_HasNoPreviousBlock() {
        try (ChainScanDatabase database = openDatabase()) {
            List<BlockContext> contexts = new SqlChainScanStore(database.getDataSource(), database.getDialect())
                    .loadBlocks(0, 0);
            assertEquals(1, contexts.size());
            assertNull(contexts.get(0).previous());
        }
    }

    @Test
    void open_GivenSqlite_RefusesWrites() {
        try (ChainScanDatabase database = openDatabase(); Connection connection = database.getDataSource().getConnection()) {
            assertThrows(SQLException.class,
                    () -> connection.createStatement().execute("DELETE FROM block WHERE height = 0"));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void open_LeavesTheDatabaseFileUnchanged() throws Exception {
        // Arrange
        byte[] before = Files.readAllBytes(folder.resolve("chain.db"));

        // Act
        try (ChainScanDatabase database = openDatabase()) {
            new SqlChainScanStore(database.getDataSource(), database.getDialect()).loadBlocks(0, 4);
        }

        // Assert
        assertArrayEquals(before, Files.readAllBytes(folder.resolve("chain.db")));
    }

    @Test
    void pendingMigrations_GivenDatabaseWithoutSchemaHistory_ReportsAllMigrations() {
        try (ChainScanDatabase database = openDatabase()) {
            assertTrue(database.pendingMigrations().length > 0);
        }
    }
}
