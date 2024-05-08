package brs.at;

import brs.Signum;
import brs.SignumException;
import brs.Transaction;
import brs.db.TransactionDb;
import brs.db.sql.Db;
import brs.props.PropertyService;
import brs.props.Props;
import brs.schema.tables.records.TransactionRecord;
import org.jooq.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.stream.Collectors;

import static brs.schema.Tables.TRANSACTION;

/**
 * This class is used to cache the transactions of past x (Props.BRS_AT_PROCESSOR_CACHE_BLOCK_COUNT) blocks
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
  private final ArrayList<Long> currentBlockAtIds = new ArrayList<>();
  private int startBlockHeight = Integer.MAX_VALUE;
  private long minimumActivationAmount = Long.MAX_VALUE;
  private final int numberOfBlocksToCache;
  private int lastLoadedBlockHeight = 0;

  public static class ATContext {
    public byte[] md5;
    public AT at;
    public LinkedList<Transaction> transactions = new LinkedList<>();
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

  public ArrayList<Long> getCurrentBlockAtIds() {
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

  private void loadTransactionsPerATs() {
    logger.debug("Loading tx for lo: {}, hi: {}, amount: {}, no ATs: {}", startBlockHeight, currentBlockHeight, minimumActivationAmount, getAtMap().size());
    Result<TransactionRecord> result = Db.useDSLContext(ctx -> {
      return ctx.selectFrom(TRANSACTION)
        .where(TRANSACTION.HEIGHT.between(startBlockHeight, currentBlockHeight))
        .and(TRANSACTION.RECIPIENT_ID.in(getCurrentBlockAtIds()))
        .and(TRANSACTION.AMOUNT.greaterOrEqual(minimumActivationAmount))
        .orderBy(TRANSACTION.HEIGHT, TRANSACTION.ID)
        .fetch();
    });
    logger.debug("Fetched {} tx", result.size());

    TransactionDb db = Db.getDbsByDatabaseType().getTransactionDb();
    for (TransactionRecord r : result) {
      try {
        ATContext context = this.atMap.get(r.getRecipientId());
        if (context != null) {
          context.transactions.add(db.loadTransaction(r));
        }
      } catch (SignumException.ValidationException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void loadTransactionsFromHeightUntilCurrentBlock(int startHeight, boolean shallRemoveOldest) {
    logger.debug("Loading all tx for heights from {} to {}", startHeight, currentBlockHeight - 1);
    Result<TransactionRecord> result = Db.useDSLContext(ctx -> {
      return ctx.selectFrom(TRANSACTION)
        .where(TRANSACTION.HEIGHT.between(startHeight, currentBlockHeight - 1))
        .and(TRANSACTION.RECIPIENT_ID.isNotNull())
        .orderBy(TRANSACTION.HEIGHT, TRANSACTION.ID)
        .fetch();
    });

    HashSet<Long> processedRecipients = new HashSet<>();
    TransactionDb db = Db.getDbsByDatabaseType().getTransactionDb();
    for (TransactionRecord r : result) {
      Long recipientId = r.getRecipientId();
      try {
        ATContext context = this.atMap.get(recipientId);
        if (context != null) {
          //defensive: avoid double adding - maybe not the most elegant way...
          if (context.transactions.stream().anyMatch(t -> t.getId() == r.getId())) {
            logger.debug("Doubled Tx ({}) found for Recipient {}", r.getId(), recipientId);
          } else {
            context.transactions.add(db.loadTransaction(r));
          }
        } else {
          ATContext newContext = new ATContext();
          newContext.transactions.add(db.loadTransaction(r));
          this.atMap.put(recipientId, newContext);
        }
        processedRecipients.add(recipientId);
      } catch (SignumException.ValidationException e) {
        throw new RuntimeException(e);
      }
    }

    if (shallRemoveOldest) {
      processedRecipients.forEach(this::pruneTransactionList);
    }

  }

  private void pruneTransactionList(long recipientId) {
    ATContext context = this.atMap.get(recipientId);
    if (context == null || context.transactions.isEmpty()) {
      return;
    }

    int minimumHeightToKeep = currentBlockHeight - numberOfBlocksToCache - 1;
    Transaction oldest = context.transactions.peekFirst();
    int count = 0;
    while(!context.transactions.isEmpty() && oldest.getHeight() < minimumHeightToKeep){
      context.transactions.removeFirst();
      logger.debug("Removed tx {}", oldest.getId());
      oldest = context.transactions.peekFirst();
      ++count;
    }
    if(count > 0){
      logger.debug("Removed {} old transactions lower than height {} for recipient {}", count, minimumHeightToKeep, recipientId);
    }
  }

  public Long findTransactionId(int startHeight, int endHeight, Long atID, int numOfTx, long minAmount) throws CacheMissException {
    long startTime = System.nanoTime();

    ATContext atContext = this.atMap.get(atID);
    if (atContext == null) {
      logger.debug("AT {} not found", atID);
      throw new CacheMissException();
    }

    // can be -1
    if (startHeight <= 0) {
      startHeight = atContext.at.getCreationBlockHeight();
    }

    if (startHeight < startBlockHeight || endHeight > currentBlockHeight) {
      logger.debug("Out of range (start: {}, end: {} - wanted block: {})", startBlockHeight, currentBlockHeight, startHeight);
      throw new CacheMissException();
    }

    long id = 0;
    final int finalStartHeight = startHeight;
    List<Transaction> collected = atContext.transactions.stream()
      .filter(t ->
        t.getHeight() >= finalStartHeight &&
          t.getHeight() < endHeight &&
          t.getAmountNqt() >= minAmount
      )
      .collect(Collectors.toList());

    if (collected.size() > numOfTx) {
      id = collected.get(numOfTx).getId();
    }

    long executionTime = (System.nanoTime() - startTime) / 1000000;
    logger.debug("txId: {} - Duration: {} milliseconds", id, executionTime);
    return id;
  }


  public int findTransactionHeight(Long transactionId, int height, Long atID, long minAmount) throws CacheMissException {
    long startTime = System.nanoTime();
    ATContext atContext = this.atMap.get(atID);
    if (atContext == null) {
      logger.debug("AT {} not found", atID);
      throw new CacheMissException();
    }

    // TODO: there must be another way how this works. This is very fragile
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

