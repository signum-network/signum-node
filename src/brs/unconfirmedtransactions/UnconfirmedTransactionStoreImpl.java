package brs.unconfirmedtransactions;

import brs.Burst;
import brs.BurstException.ValidationException;
import brs.Constants;
import brs.Transaction;
import brs.db.TransactionDb;
import brs.db.store.AccountStore;
import brs.fluxcapacitor.FluxValues;
import brs.peer.Peer;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.TimeService;
import brs.transactionduplicates.TransactionDuplicatesCheckerImpl;
import brs.transactionduplicates.TransactionDuplicationResult;
import brs.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class UnconfirmedTransactionStoreImpl implements UnconfirmedTransactionStore {

  private static final Logger logger = LoggerFactory.getLogger(UnconfirmedTransactionStoreImpl.class);

  private final TimeService timeService;
  private final ReservedBalanceCache reservedBalanceCache;
  private final TransactionDuplicatesCheckerImpl transactionDuplicatesChecker = new TransactionDuplicatesCheckerImpl();

  private final HashMap<Transaction, HashSet<Peer>> fingerPrintsOverview = new HashMap<>();
  private final TransactionDb transactionDb;

  private final SortedMap<Long, List<Transaction>> internalStore;

  private int totalSize;
  private final int maxSize;

  private final int maxRawUTBytesToSend;

  private List<Transaction> unconfirmedFullHash;
  private final int maxPercentageUnconfirmedTransactionsFullHash;

  public UnconfirmedTransactionStoreImpl(TimeService timeService, PropertyService propertyService, AccountStore accountStore, TransactionDb transactionDb) {
    this.timeService = timeService;
    this.transactionDb = transactionDb;

    this.reservedBalanceCache = new ReservedBalanceCache(accountStore);

    this.maxSize = propertyService.getInt(Props.P2P_MAX_UNCONFIRMED_TRANSACTIONS);
    this.totalSize = 0;

    this.maxRawUTBytesToSend = propertyService.getInt(Props.P2P_MAX_UNCONFIRMED_TRANSACTIONS_RAW_SIZE_BYTES_TO_SEND);

    this.maxPercentageUnconfirmedTransactionsFullHash = propertyService.getInt(Props.P2P_MAX_PERCENTAGE_UNCONFIRMED_TRANSACTIONS_FULL_HASH_REFERENCE);
    this.unconfirmedFullHash = new ArrayList<Transaction>();

    internalStore = new TreeMap<>();

      ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    Runnable cleanupExpiredTransactions = () -> {
      synchronized (internalStore) {
        final List<Transaction> expiredTransactions = getAll()
                .stream()
                .filter(t -> timeService.getEpochTime() > t.getExpiration() || transactionDb.hasTransaction(t.getId()))
                .collect(Collectors.toList());
        expiredTransactions.forEach(this::removeTransaction);
      }
    };
    scheduler.scheduleWithFixedDelay(cleanupExpiredTransactions, 1, 1, TimeUnit.MINUTES);
  }

  @Override
  public boolean put(Transaction transaction, Peer peer) throws ValidationException {
    synchronized (internalStore) {
      if (transactionIsCurrentlyInCache(transaction)) {
        if (peer != null) {
          logger.info("Transaction {}: Added fingerprint of {}", transaction.getId(), peer.getPeerAddress());
          fingerPrintsOverview.get(transaction).add(peer);
        }
      } else if (transactionCanBeAddedToCache(transaction)) {
        this.reservedBalanceCache.reserveBalanceAndPut(transaction);

        final TransactionDuplicationResult duplicationInformation = transactionDuplicatesChecker.removeCheaperDuplicate(transaction);

        if (duplicationInformation.isDuplicate()) {
          final Transaction duplicatedTransaction = duplicationInformation.getTransaction();

          if (duplicatedTransaction != null && duplicatedTransaction != transaction) {
            logger.info("Transaction {}: Adding more expensive duplicate transaction", transaction.getId());
            removeTransaction(duplicationInformation.getTransaction());
            this.reservedBalanceCache.refundBalance(duplicationInformation.getTransaction());

            addTransaction(transaction, peer);

            if (totalSize > maxSize) {
              removeCheapestFirstToExpireTransaction();
            }
          } else {
            logger.debug("Transaction {}: Will not add a cheaper duplicate UT", transaction.getId());
          }
        } else {
          addTransaction(transaction, peer);
          if (totalSize % 128 == 0) {
            logger.info("Cache size: {}/{} added {} from sender {}", totalSize, maxSize, transaction.getId(), transaction.getSenderId());
          } else {
            logger.debug("Cache size: {}/{} added {} from sender {}", totalSize, maxSize, transaction.getId(), transaction.getSenderId());
          }
        }

        if (totalSize > maxSize) {
          removeCheapestFirstToExpireTransaction();
        }

        return true;
      }

      return false;
    }
  }

  @Override
  public Transaction get(Long transactionId) {
    synchronized (internalStore) {
      return getTransactionInCache(transactionId);
    }
  }

  @Override
  public boolean exists(Long transactionId) {
    return get(transactionId) != null;
  }

  @Override
  public List<Transaction> getAll() {
    synchronized (internalStore) {
      final ArrayList<Transaction> flatTransactionList = new ArrayList<>();

      for (List<Transaction> amountSlot : internalStore.values()) {
        flatTransactionList.addAll(amountSlot);
      }

      return flatTransactionList;
    }
  }

  @Override
  public List<Transaction> getAllFor(Peer peer) {
    synchronized (internalStore) {
      final List<Transaction> untouchedTransactions = fingerPrintsOverview.entrySet().stream()
          .filter(e -> !e.getValue().contains(peer))
          .map(Map.Entry::getKey).collect(Collectors.toList());

      final ArrayList<Transaction> resultList = new ArrayList<>();

      long roomLeft = this.maxRawUTBytesToSend;

      for (Transaction t : untouchedTransactions) {
        roomLeft -= t.getSize();

        if (roomLeft > 0) {
          resultList.add(t);
        } else {
          break;
        }
      }

      return resultList;
    }
  }

  @Override
  public void remove(Transaction transaction) {
    synchronized (internalStore) {
      // Make sure that we are acting on our own copy of the transaction, as this is the one we want to remove.
      Transaction internalTransaction = get(transaction.getId());
      if (internalTransaction != null) {
        logger.debug("Removing {}", transaction.getId());
        removeTransaction(internalTransaction);
      }
    }
  }

  @Override
  public void clear() {
    synchronized (internalStore) {
      logger.info("Clearing UTStore");
      totalSize = 0;
      internalStore.clear();
      reservedBalanceCache.clear();
      transactionDuplicatesChecker.clear();
    }
  }

  @Override
  public void resetAccountBalances() {
    synchronized (internalStore) {
      for(Transaction insufficientFundsTransactions: reservedBalanceCache.rebuild(getAll())) {
        this.removeTransaction(insufficientFundsTransactions);
      }
    }
  }

  @Override
  public void markFingerPrintsOf(Peer peer, List<Transaction> transactions) {
    synchronized (internalStore) {
      for (Transaction transaction : transactions) {
        if (fingerPrintsOverview.containsKey(transaction)) {
          fingerPrintsOverview.get(transaction).add(peer);
        }
      }
    }
  }

  @Override
  public void removeForgedTransactions(List<Transaction> transactions) {
    synchronized (internalStore) {
      for (Transaction t : transactions) {
        remove(t);
      }
    }
  }

  @Override
  public int getAmount() {
    return totalSize;
  }

  private boolean transactionIsCurrentlyInCache(Transaction transaction) {
    return getTransactionInCache(transaction.getId()) != null;
  }
  
  private Transaction getTransactionInCache(Long transactionId) {
    for (List<Transaction> amountSlot : internalStore.values()) {
      for (Transaction t : amountSlot) {
        if (t.getId() == transactionId) {
          return t;
        }
      }
    }
    return null;    
  }
  
  private Transaction getTransactionInChache(String fullHash) {
    for (List<Transaction> amountSlot : internalStore.values()) {
      for (Transaction t : amountSlot) {
        if (fullHash.equals(t.getFullHash())) {
          return t;
        }
      }
    }
    return null;    
  }

  private boolean transactionCanBeAddedToCache(Transaction transaction) {
    return transactionIsCurrentlyNotExpired(transaction)
        && !cacheFullAndTransactionCheaperThanAllTheRest(transaction)
        && !tooManyTransactionsWithReferencedFullHash(transaction)
        && !tooManyTransactionsForSlotSize(transaction);
  }

  private boolean tooManyTransactionsForSlotSize(Transaction transaction) {
    final long slotHeight = this.amountSlotForTransaction(transaction);
    long slotUnconfirmedLimit = slotHeight * 360;
    if(Burst.getFluxCapacitor().getValue(FluxValues.SPEEDWAY, Burst.getBlockchain().getHeight())) {
      // Use a higher limit per slot, since most transactions will be on the same slot (first)
      slotUnconfirmedLimit = maxSize/4;
    }

    if (slotHeight <= 0) {
      logger.info("Transaction {}: Not added, not enough fee {} for it size {}", transaction.getId(), transaction.getFeeNQT(), transaction.getSize());
      return true;
    }
    if (this.internalStore.containsKey(slotHeight) && this.internalStore.get(slotHeight).size() >= slotUnconfirmedLimit) {
      logger.info("Transaction {}: Not added because slot {} is full", transaction.getId(), slotHeight);
      return true;
    }

    return false;
  }
  
  private boolean hasUnconfirmedFullHash(Transaction transaction) {
    if (!StringUtils.isEmpty(transaction.getReferencedTransactionFullHash())) {
      // When a transaction has a reference hash, assume as a regular transaction if the reference was already confirmed
      Transaction refTx = transactionDb.findTransactionByFullHash(transaction.getReferencedTransactionFullHash());
      if(refTx != null)
        return false;

      // Also assume as a regular transaction if the reference transaction is already available on cache
      // and that reference transaction does not depend on another one.
      refTx = getTransactionInChache(transaction.getReferencedTransactionFullHash());
      if(refTx != null && StringUtils.isEmpty(transaction.getReferencedTransactionFullHash()))
        return false;

      return true;
    }

    return false;    
  }

  private boolean tooManyTransactionsWithReferencedFullHash(Transaction transaction) {
    if(hasUnconfirmedFullHash(transaction) &&
        maxPercentageUnconfirmedTransactionsFullHash <= (((unconfirmedFullHash.size() + 1) * 100) / maxSize)) {
        logger.info("Transaction {}: Not added because too many transactions with referenced full hash", transaction.getId());
        return true;
    }
    return false;
  }

  private boolean cacheFullAndTransactionCheaperThanAllTheRest(Transaction transaction) {
    if (totalSize >= maxSize && internalStore.firstKey() > amountSlotForTransaction(transaction)) {
      logger.info("Transaction {}: Not added because cache is full and transaction is cheaper than all the rest", transaction.getId());
      return true;
    }

    return false;
  }

  private boolean transactionIsCurrentlyNotExpired(Transaction transaction) {
    if (timeService.getEpochTime() < transaction.getExpiration()) {
      return true;
    } else {
      logger.info("Transaction {} past expiration: {}", transaction.getId(), transaction.getExpiration());
      return false;
    }
  }

  private void addTransaction(Transaction transaction, Peer peer) {
    final List<Transaction> slot = getOrCreateAmountSlotForTransaction(transaction);
    slot.add(transaction);
    totalSize++;

    fingerPrintsOverview.put(transaction, new HashSet<>());

    if (peer != null) {
      fingerPrintsOverview.get(transaction).add(peer);
    }

    if (logger.isDebugEnabled()) {
      if (peer == null) {
        logger.debug("Adding Transaction {} from ourself", transaction.getId());
      } else {
        logger.debug("Adding Transaction {} from Peer {}", transaction.getId(), peer.getPeerAddress());
      }
    }

    if (hasUnconfirmedFullHash(transaction)) {
      unconfirmedFullHash.add(transaction);
    }
  }

  private List<Transaction> getOrCreateAmountSlotForTransaction(Transaction transaction) {
    final long amountSlotNumber = amountSlotForTransaction(transaction);

    if (!this.internalStore.containsKey(amountSlotNumber)) {
      this.internalStore.put(amountSlotNumber, new ArrayList<>());
    }

    return this.internalStore.get(amountSlotNumber);
  }


  private long amountSlotForTransaction(Transaction transaction) {
    long slot = transaction.getFeeNQT() / Constants.FEE_QUANT;
    if(Burst.getFluxCapacitor().getValue(FluxValues.SPEEDWAY)) {
      // Using the 'slot' now as a priority measure, not exactly as before
      long transactionSize = transaction.getSize() / Constants.ORDINARY_TRANSACTION_BYTES;
      slot /= transactionSize;
    }
    
    return slot;
  }

  private void removeCheapestFirstToExpireTransaction() {
    final Optional<Transaction> cheapestFirstToExpireTransaction = this.internalStore.get(this.internalStore.firstKey()).stream()
        .sorted(Comparator.comparingLong(Transaction::getFeeNQTPerByte).thenComparing(Transaction::getExpiration).thenComparing(Transaction::getId))
        .findFirst();

    if (cheapestFirstToExpireTransaction.isPresent()) {
      reservedBalanceCache.refundBalance(cheapestFirstToExpireTransaction.get());
      removeTransaction(cheapestFirstToExpireTransaction.get());
    }
  }

  private void removeTransaction(Transaction transaction) {
    if (transaction == null)
      return;

    for(Long amountSlotNumber : internalStore.keySet()) {
      List<Transaction> amountSlot = internalStore.get(amountSlotNumber);
      if(amountSlot.contains(transaction)) {
        amountSlot.remove(transaction);
        
        if (amountSlot.isEmpty()) {
          this.internalStore.remove(amountSlotNumber);
        }
        break;
      }
    }

    fingerPrintsOverview.remove(transaction);
    totalSize--;
    transactionDuplicatesChecker.removeTransaction(transaction);
    unconfirmedFullHash.remove(transaction);
  }

  @Override
  public long getFreeSlot(int numberOfBlocks) {
    long slotsAvailable = 0;
    long freeSlot = 1;
    
    long txsPerSlot = 1;
    if(Burst.getFluxCapacitor().getValue(FluxValues.SPEEDWAY)) {
      // transactions per slot, we assume transactions can occupy up to 2 times the ordinary size
      txsPerSlot = Burst.getFluxCapacitor().getValue(FluxValues.MAX_NUMBER_TRANSACTIONS, Burst.getBlockchain().getHeight())
          /(2 * Constants.ORDINARY_TRANSACTION_BYTES);
    }
    
    synchronized (internalStore) {

      for (Long currentSlot : internalStore.keySet()) {
        List<Transaction> txInSlot = internalStore.get(currentSlot);

        if(txsPerSlot == 1) {
          slotsAvailable += numberOfBlocks*currentSlot - txInSlot.size();
        }
        else {
          // In this mode the slots are per transaction size, so they occupy more bytes and we have less per 
          slotsAvailable += numberOfBlocks*txsPerSlot/currentSlot - txInSlot.size();
        }

        if(slotsAvailable < 0) {
          freeSlot = currentSlot + 1;
        }
      }
    }
    return freeSlot;
  }

}
