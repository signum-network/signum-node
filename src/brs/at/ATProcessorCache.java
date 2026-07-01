package brs.at;

import brs.Signum;
import brs.SignumException;
import brs.Transaction;
import brs.db.TransactionDb;
import brs.db.sql.Db;
import brs.props.PropertyService;
import brs.props.Props;
import org.jooq.Cursor;
import brs.schema.tables.records.TransactionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.stream.Collectors;

import static brs.schema.Tables.TRANSACTION;

/**
 * This class is used to cache the transactions of past x
 * (Props.BRS_AT_PROCESSOR_CACHE_BLOCK_COUNT) blocks
 * to reduce database access as much as possible while AT processing.
 */
public final class ATProcessorCache {

    public static class CacheMissException extends Exception {
    }

    private static final Logger logger = LoggerFactory.getLogger(ATProcessorCache.class);

    private static ATProcessorCache instance;
    private static final int CostOfOneAT = AtConstants.AT_ID_SIZE + 16;
    private final LinkedHashMap<Long, ATContext> atMap = new LinkedHashMap<>();
    private int currentBlockHeight = Integer.MIN_VALUE;
    private final LinkedHashSet<Long> currentBlockAtIds = new LinkedHashSet<>();
    private int startBlockHeight = Integer.MAX_VALUE;
    private long minimumActivationAmount = Long.MAX_VALUE;
    private final int numberOfBlocksToCache;
    private int lastLoadedBlockHeight = 0;

    public static class ATContext {
        public byte[] md5;
        public AT at;
        public ArrayList<Transaction> transactions = new ArrayList<>();
    }

    private ATProcessorCache(PropertyService propertyService) {
        this.numberOfBlocksToCache = propertyService.getInt(Props.BRS_AT_PROCESSOR_CACHE_BLOCK_COUNT);
    }

    public boolean isEnabled() {
        return numberOfBlocksToCache > 0;
    }

    public HashMap<Long, ATContext> getAtMap() {
        return this.atMap;
    }

    public static ATProcessorCache getInstance() {
        if (instance == null) {
            instance = new ATProcessorCache(Signum.getPropertyService());
        }
        return instance;
    }

    public void reset() {
        logger.debug("Resetting AT Processor Cache");
        atMap.clear();
        currentBlockHeight = Integer.MIN_VALUE;
        startBlockHeight = Integer.MAX_VALUE;
        minimumActivationAmount = Long.MAX_VALUE;
        lastLoadedBlockHeight = 0;
    }

    public LinkedHashSet<Long> getCurrentBlockAtIds() {
        return this.currentBlockAtIds;
    }

    public ATContext getATContext(Long atId) {
        return this.atMap.get(atId);
    }

    public void loadBlock(byte[] ats, int blockHeight) throws AtException {
        this.currentBlockHeight = blockHeight;
        this.startBlockHeight = blockHeight - this.numberOfBlocksToCache;
        this.currentBlockAtIds.clear();
        if (ats == null || ats.length == 0) {
            return;
        }
        long startTime = System.nanoTime();
        loadAtBytesIntoAtMap(ats);
        loadATsforBlock(blockHeight);
        if (isEnabled()) {
            loadTransactions();
        }
        logger.debug("AT Processor Cache: current size = {}", atMap.size());
        long executionTime = (System.nanoTime() - startTime) / 1000000;
        logger.debug("Cache Duration: {} milliseconds", executionTime);
    }

    private void loadATsforBlock(int blockHeight) {
        logger.debug("Loading {} ATs for block height {}", getCurrentBlockAtIds().size(), blockHeight);
        Signum.getStores().getAtStore().getATs(getCurrentBlockAtIds()).forEach(at -> {
            Long atId = AtApiHelper.getLong(at.getId());
            this.minimumActivationAmount = Math.min(this.minimumActivationAmount, at.minActivationAmount());
            ATContext atContext = atMap.get(atId);
            atContext.at = at;
            logger.debug("Cached AT {}", atId);
        });
    }

    private void loadAtBytesIntoAtMap(byte[] ats) throws AtException {
        if (ats.length % (CostOfOneAT) != 0) {
            throw new AtException("ATs must be a multiple of cost of one AT ( " + CostOfOneAT + " )");
        }

        ByteBuffer b = ByteBuffer.wrap(ats);
        b.order(ByteOrder.LITTLE_ENDIAN);

        byte[] atId = new byte[AtConstants.AT_ID_SIZE];
        byte[] md5 = new byte[16];

        while (b.remaining() >= CostOfOneAT) {
            b.get(atId);
            b.get(md5);
            long atIdLong = AtApiHelper.getLong(atId);
            ATContext existingAtContext = atMap.get(atIdLong);
            if (existingAtContext == null) {
                ATContext atContext = new ATContext();
                atContext.md5 = md5.clone();
                atMap.put(atIdLong, atContext);
            } else {
                existingAtContext.md5 = md5.clone();
            }
            this.currentBlockAtIds.add(atIdLong);
        }
    }

    private void loadTransactions() {
        if (lastLoadedBlockHeight == 0) {
            loadTransactionsFromHeightUntilCurrentBlock(startBlockHeight, false);
        } else {
            loadTransactionsFromHeightUntilCurrentBlock(lastLoadedBlockHeight, true);
        }
        lastLoadedBlockHeight = currentBlockHeight;
    }

    private void loadTransactionsFromHeightUntilCurrentBlock(int startHeight, boolean shallRemoveOldest) {
        logger.debug("Loading AT transactions for heights from {} to {}", startHeight, currentBlockHeight - 1);

        Db.useDSLContext(ctx -> {
            try (Cursor<TransactionRecord> cursor = ctx.selectFrom(TRANSACTION)
                    .where(TRANSACTION.HEIGHT.between(startHeight, currentBlockHeight - 1))
                    .and(TRANSACTION.RECIPIENT_ID.isNotNull())
                    .orderBy(TRANSACTION.HEIGHT, TRANSACTION.ID)
                    .fetchSize(1000)
                    .fetchLazy()) {

                // Phase 1: Collect records only.
                // This avoids executing nested queries while the DB cursor is open.
                List<TransactionRecord> recordsToProcess = new ArrayList<>();
                for (TransactionRecord r : cursor) {
                    recordsToProcess.add(r);
                }

                // Phase 2: Now that the cursor is closed, we can safely load full transactions
                TransactionDb db = Db.getDbsByDatabaseType().getTransactionDb();
                for (TransactionRecord r : recordsToProcess) {
                    Long recipientId = r.getRecipientId();

                    ATContext context = this.atMap.get(recipientId);
                    if (context == null) {
                        // Collect transactions for any potential AT to ensure complete history
                        context = new ATContext();
                        this.atMap.put(recipientId, context);
                    }

                    long txId = r.getId();

                    // Quick duplicate check: only check the last transaction in the list
                    // since the DB query is ordered by height and ID.
                    int size = context.transactions.size();
                    boolean alreadyExists = size > 0 && context.transactions.get(size - 1).getId() == txId;

                    if (!alreadyExists) {
                        try {
                            context.transactions.add(db.loadTransaction(r));
                        } catch (SignumException.ValidationException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                if (shallRemoveOldest) {
                    int minimumHeightToKeep = currentBlockHeight - numberOfBlocksToCache - 1;

                    // Iterating the whole map to avoid memory leak of old recipient IDs
                    Iterator<Map.Entry<Long, ATContext>> it = atMap.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<Long, ATContext> entry = it.next();
                        Long id = entry.getKey();
                        ATContext context = entry.getValue();

                        // Remove old transactions
                        context.transactions.removeIf(t -> t.getHeight() < minimumHeightToKeep);

                        // Cleanup: if it's not an active AT in this block AND has no transactions in
                        // window
                        // we can safely remove the context to save memory.
                        if (context.at == null && context.transactions.isEmpty() && !currentBlockAtIds.contains(id)) {
                            it.remove();
                        }
                    }
                }
            }
        });
    }

    public Long findTransactionId(int startHeight, int endHeight, Long atID, int numOfTx, long minAmount)
            throws CacheMissException {
        long startTime = System.nanoTime();

        ATContext atContext = this.atMap.get(atID);
        if (atContext == null) {
            logger.debug("AT {} not found", atID);
            throw new CacheMissException();
        }

        if (atContext.at == null) {
            // This might happen if querying history for a non-AT account or an AT not in
            // current block
            logger.debug(
                    "AT {} context found but AT object missing! Cross-contract history query or cache boundary reached.",
                    atID);
            throw new CacheMissException();
        }

        // can be -1
        if (startHeight <= 0) {
            startHeight = atContext.at.getCreationBlockHeight();
        }

        if (startHeight < startBlockHeight || endHeight > currentBlockHeight) {
            logger.debug("Out of range (start: {}, end: {} - wanted block: {})", startBlockHeight, currentBlockHeight,
                    startHeight);
            throw new CacheMissException();
        }

        long id = 0;
        final int finalStartHeight = startHeight;
        List<Transaction> collected = atContext.transactions.stream()
                .filter(t -> t.getHeight() >= finalStartHeight &&
                        t.getHeight() < endHeight &&
                        t.getAmountNqt() >= minAmount)
                .collect(Collectors.toList());

        if (collected.size() > numOfTx) {
            id = collected.get(numOfTx).getId();
        }

        long executionTime = (System.nanoTime() - startTime) / 1000000;
        logger.debug("txId: {} - Duration: {} milliseconds", id, executionTime);
        return id;
    }

    public int findTransactionHeight(Long transactionId, int height, Long atID, long minAmount)
            throws CacheMissException {
        long startTime = System.nanoTime();
        ATContext atContext = this.atMap.get(atID);
        if (atContext == null) {
            logger.debug("AT {} not found", atID);
            throw new CacheMissException();
        }

        if (atContext.at == null) {
            logger.debug("AT {} context found but AT object missing in findTransactionHeight!", atID);
            throw new CacheMissException();
        }

        int count = 0;
        Collection<Transaction> transactions = atContext.transactions;
        for (Transaction t : transactions) {
            if (t.getHeight() == height && t.getAmountNqt() >= minAmount) {
                ++count;
                if (t.getId() == transactionId) {
                    break;
                }
            }
        }

        long executionTime = (System.nanoTime() - startTime) / 1000000;
        logger.debug("Cache Hit: {}, Duration: {} milliseconds", count, executionTime);
        if (count == 0 || count >= transactions.size()) {
            throw new CacheMissException();
        }
        return count;
    }
}
