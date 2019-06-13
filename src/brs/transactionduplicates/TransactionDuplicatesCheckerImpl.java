package brs.transactionduplicates;

import brs.Transaction;
import brs.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Objects;

public class TransactionDuplicatesCheckerImpl {

  private final Logger logger = LoggerFactory.getLogger(TransactionDuplicatesCheckerImpl.class);

  private final HashMap<TransactionType, HashMap<String, Transaction>> duplicates = new HashMap<>();

  public void clear() {
    duplicates.clear();
  }

  public TransactionDuplicationResult removeCheaperDuplicate(Transaction transaction) {
    final TransactionDuplicationKey transactionDuplicateKey = transaction.getDuplicationKey();

    if (transactionDuplicateKey.equals(TransactionDuplicationKey.IS_ALWAYS_DUPLICATE)) {
      return new TransactionDuplicationResult(true, null);
    } else if (transactionDuplicateKey.equals(TransactionDuplicationKey.IS_NEVER_DUPLICATE)) {
      return new TransactionDuplicationResult(false, null);
    }

    duplicates.computeIfAbsent(transactionDuplicateKey.transactionType, n -> new HashMap<>());

    final HashMap<String, Transaction> transactionOverview = duplicates.get(transactionDuplicateKey.transactionType);

    final Transaction possiblyExistingTransaction = transactionOverview.get(transactionDuplicateKey.key);

    if (possiblyExistingTransaction != null && possiblyExistingTransaction.getFeeNQT() >= transaction.getFeeNQT()) {
      logger.debug("Transaction {}: is a duplicate of {} (Type: {})", transaction.getId(), possiblyExistingTransaction.getId(), transaction.getType());
      return new TransactionDuplicationResult(true, transaction);
    } else {
      transactionOverview.put(transactionDuplicateKey.key, transaction);
      return new TransactionDuplicationResult(possiblyExistingTransaction != null, possiblyExistingTransaction);
    }
  }

  public boolean hasAnyDuplicate(Transaction transaction) {
    final TransactionDuplicationKey transactionDuplicateKey = transaction.getDuplicationKey();

    if (transactionDuplicateKey.equals(TransactionDuplicationKey.IS_ALWAYS_DUPLICATE)) {
      return true;
    } else if (transactionDuplicateKey.equals(TransactionDuplicationKey.IS_NEVER_DUPLICATE)) {
      return false;
    }

    duplicates.computeIfAbsent(transactionDuplicateKey.transactionType, n -> new HashMap<>());

    final HashMap<String, Transaction> transactionOverview = duplicates.get(transactionDuplicateKey.transactionType);

    if (transactionOverview.containsKey(transactionDuplicateKey.key)) {
      return true;
    } else {
      transactionOverview.put(transactionDuplicateKey.key, transaction);
      return false;
    }
  }

  public void removeTransaction(Transaction transaction) {
    final TransactionDuplicationKey transactionDuplicateKey = transaction.getDuplicationKey();

    if (!transactionDuplicateKey.equals(TransactionDuplicationKey.IS_ALWAYS_DUPLICATE) && !transactionDuplicateKey.equals(TransactionDuplicationKey.IS_NEVER_DUPLICATE) && duplicates.containsKey(transactionDuplicateKey.transactionType) && Objects.equals(duplicates.get(transactionDuplicateKey.transactionType).get(transactionDuplicateKey.key), transaction)) {
      duplicates.get(transactionDuplicateKey.transactionType).remove(transactionDuplicateKey.key);
    }
  }
}
