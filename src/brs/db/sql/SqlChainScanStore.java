package brs.db.sql;

import brs.Block;
import brs.SignumException;
import brs.Transaction;
import brs.db.store.ChainScanStore;
import brs.schema.tables.records.BlockRecord;
import brs.schema.tables.records.TransactionRecord;
import brs.services.impl.chainscan.BlockContext;
import brs.services.impl.chainscan.ScannedBlock;
import brs.services.impl.chainscan.ScannedTransaction;
import brs.util.Convert;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static brs.schema.Tables.BLOCK;
import static brs.schema.Tables.TRANSACTION;

/**
 * {@link ChainScanStore} on its own data source, independent of the node's {@link Db}, so that the
 * scan can use a read-only connection.
 */
public class SqlChainScanStore implements ChainScanStore {

    private final DataSource dataSource;
    private final SQLDialect dialect;
    private final Settings settings = new Settings().withRenderSchema(Boolean.FALSE);

    public SqlChainScanStore(DataSource dataSource, SQLDialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    private DSLContext ctx() {
        return DSL.using(dataSource, dialect, settings);
    }

    @Override
    public int getMaxHeight() {
        Integer max = ctx().select(DSL.max(BLOCK.HEIGHT)).from(BLOCK).fetchOne(0, Integer.class);
        return max == null ? -1 : max;
    }

    @Override
    public List<BlockContext> loadBlocks(int fromHeight, int toHeight) {
        int firstHeight = Math.max(0, fromHeight - 1);
        DSLContext ctx = ctx();

        Map<Integer, List<ScannedTransaction>> transactionsByHeight = new HashMap<>();
        for (TransactionRecord record : ctx.selectFrom(TRANSACTION)
                .where(TRANSACTION.HEIGHT.between(firstHeight, toHeight))
                .orderBy(TRANSACTION.HEIGHT, TRANSACTION.DB_ID)
                .fetch()) {
            transactionsByHeight.computeIfAbsent(record.getHeight(), h -> new ArrayList<>()).add(scanTransaction(record));
        }

        Map<Integer, ScannedBlock> blocksByHeight = new HashMap<>();
        List<ScannedBlock> blocks = new ArrayList<>();
        for (BlockRecord record : ctx.selectFrom(BLOCK)
                .where(BLOCK.HEIGHT.between(firstHeight, toHeight))
                .orderBy(BLOCK.HEIGHT)
                .fetch()) {
            ScannedBlock block = scanBlock(record, transactionsByHeight.getOrDefault(record.getHeight(), List.of()));
            blocksByHeight.put(block.height(), block);
            blocks.add(block);
        }

        List<BlockContext> contexts = new ArrayList<>();
        for (ScannedBlock block : blocks) {
            if (block.height() >= fromHeight) {
                contexts.add(new BlockContext(block, transactionsByHeight.getOrDefault(block.height(), List.of()),
                        blocksByHeight.get(block.height() - 1)));
            }
        }
        return contexts;
    }

    static ScannedTransaction scanTransaction(TransactionRecord record) {
        Transaction transaction = null;
        String loadError = null;
        try {
            transaction = SqlTransactionDb.newTransactionBuilder(record).build();
        } catch (SignumException.ValidationException | RuntimeException e) {
            loadError = e.toString();
        }
        return new ScannedTransaction(record.getDbId(), record.getId(), record.getFullHash(), record.getSenderId(),
                record.getBlockId(), record.getHeight(), record.getBlockTimestamp(), record.getSignature() != null,
                transaction, loadError);
    }

    /** Rebuilds the block with the signed transactions stored for it, so that its id is recomputed. */
    static ScannedBlock scanBlock(BlockRecord record, List<ScannedTransaction> transactionsAtHeight) {
        Block block = null;
        String loadError = null;
        try {
            block = new Block(record.getVersion(), record.getTimestamp(),
                    Optional.ofNullable(record.getPreviousBlockId()).orElse(0L),
                    record.getTotalAmount(), record.getTotalFee(), record.getTotalFeeCashBack(),
                    record.getTotalFeeBurnt(), record.getPayloadLength(), record.getPayloadHash(),
                    record.getGeneratorPublicKey(), record.getGenerationSignature(), record.getBlockSignature(),
                    record.getPreviousBlockHash(), signedTransactions(record.getId(), transactionsAtHeight),
                    record.getNonce(), record.getAts(), record.getHeight(), record.getBaseTarget());
            block.setHeight(record.getHeight()); // this constructor only uses the height for its limits
        } catch (SignumException.ValidationException | RuntimeException e) {
            loadError = e.getMessage() == null ? e.toString() : e.getMessage();
        }
        return new ScannedBlock(record.getId(), record.getHeight(), record.getTimestamp(),
                Optional.ofNullable(record.getPreviousBlockId()).orElse(0L),
                Optional.ofNullable(record.getNextBlockId()).orElse(0L),
                record.getGeneratorId(), block, loadError);
    }

    private static List<Transaction> signedTransactions(long blockId, List<ScannedTransaction> transactionsAtHeight) {
        List<Transaction> transactions = new ArrayList<>();
        for (ScannedTransaction stored : transactionsAtHeight) {
            if (stored.blockId() != blockId || !stored.signed()) {
                continue;
            }
            if (stored.transaction() == null) {
                throw new IllegalStateException("signed transaction " + Convert.toUnsignedLong(stored.storedId())
                        + " cannot be rebuilt");
            }
            transactions.add(stored.transaction());
        }
        return transactions;
    }
}
