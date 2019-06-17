package brs.unconfirmedtransactions;

import brs.BurstException.ValidationException;
import brs.Constants;
import brs.Transaction;
import brs.db.TransactionDb;
import brs.db.store.AccountStore;
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

  private final SortedMap<Long, List<Transaction>> internalStore;

  private int totalSize;
  private final int maxSize;

  private final int maxRawUTBytesToSend;

  private int numberUnconfirmedTransactionsFullHash;
  private final int maxPercentageUnconfirmedTransactionsFullHash;

  public UnconfirmedTransactionStoreImpl(TimeService timeService, PropertyService propertyService, AccountStore accountStore, TransactionDb transactionDb) {
    this.timeService = timeService;

    this.reservedBalanceCache = new ReservedBalanceCache(accountStore);

    this.maxSize = propertyService.getInt(Props.P2P_MAX_UNCONFIRMED_TRANSACTIONS);
    this.totalSize = 0;

    this.maxRawUTBytesToSend = propertyService.getInt(Props.P2P_MAX_UNCONFIRMED_TRANSACTIONS_RAW_SIZE_BYTES_TO_SEND);

    this.maxPercentageUnconfirmedTransactionsFullHash = propertyService.getInt(Props.P2P_MAX_PERCENTAGE_UNCONFIRMED_TRANSACTIONS_FULL_HASH_REFERENCE);
    this.numberUnconfirmedTransactionsFullHash = 0;

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
            logger.info("Transaction {}: Will not add a cheaper duplicate UT", transaction.getId());
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
      for (List<Transaction> amountSlot : internalStore.values()) {
        for (Transaction t : amountSlot) {
          if (t.getId() == transactionId) {
            return t;
          }
        }
      }
      return null;
    }
  }

  @Override
  public boolean exists(Long transactionId) {
    synchronized (internalStore) {
      return get(transactionId) != null;
    }
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
    final List<Transaction> amountSlot = internalStore.get(amountSlotForTransaction(transaction));
    return amountSlot != null && amountSlot.stream().anyMatch(t -> t.getId() == transaction.getId());
  }

  private boolean transactionCanBeAddedToCache(Transaction transaction) {
    return transactionIsCurrentlyNotExpired(transaction)
        && !cacheFullAndTransactionCheaperThanAllTheRest(transaction)
        && !tooManyTransactionsWithReferencedFullHash(transaction)
        && !tooManyTransactionsForSlotSize(transaction);
  }

  private boolean tooManyTransactionsForSlotSize(Transaction transaction) {
    final long slotHeight = this.amountSlotForTransaction(transaction);

    if (this.internalStore.containsKey(slotHeight) && this.internalStore.get(slotHeight).size() == slotHeight * 360) {
      logger.info("Transaction {}: Not added because slot {} is full", transaction.getId(), slotHeight);
      return true;
    }

    return false;
  }

  private boolean tooManyTransactionsWithReferencedFullHash(Transaction transaction) {
    if (!StringUtils.isEmpty(transaction.getReferencedTransactionFullHash()) && maxPercentageUnconfirmedTransactionsFullHash <= (((numberUnconfirmedTransactionsFullHash + 1) * 100) / maxSize)) {
      logger.info("Transaction {}: Not added because too many transactions with referenced full hash", transaction.getId());
      return true;
    }

    return false;
  }

  private boolean cacheFullAndTransactionCheaperThanAllTheRest(Transaction transaction) {
    if (totalSize == maxSize && internalStore.firstKey() > amountSlotForTransaction(transaction)) {
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

    if (!StringUtils.isEmpty(transaction.getReferencedTransactionFullHash())) {
      numberUnconfirmedTransactionsFullHash++;
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
    return transaction.getFeeNQT() / Constants.FEE_QUANT;
  }

  private void removeCheapestFirstToExpireTransaction() {
    final Optional<Transaction> cheapestFirstToExpireTransaction = this.internalStore.get(this.internalStore.firstKey()).stream()
        .sorted(Comparator.comparingLong(Transaction::getFeeNQT).thenComparing(Transaction::getExpiration).thenComparing(Transaction::getId))
        .findFirst();

    if (cheapestFirstToExpireTransaction.isPresent()) {
      reservedBalanceCache.refundBalance(cheapestFirstToExpireTransaction.get());
      removeTransaction(cheapestFirstToExpireTransaction.get());
    }
  }

  private void removeTransaction(Transaction transaction) {
    if (transaction == null) return;
    final long amountSlotNumber = amountSlotForTransaction(transaction);

    final List<Transaction> amountSlot = internalStore.get(amountSlotNumber);

    fingerPrintsOverview.remove(transaction);
    amountSlot.remove(transaction);
    totalSize--;
    transactionDuplicatesChecker.removeTransaction(transaction);

    if (!StringUtils.isEmpty(transaction.getReferencedTransactionFullHash())) {
      numberUnconfirmedTransactionsFullHash--;
    }

    if (amountSlot.isEmpty()) {
      this.internalStore.remove(amountSlotNumber);
    }
  }

}
