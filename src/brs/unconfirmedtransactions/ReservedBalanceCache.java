package brs.unconfirmedtransactions;

import brs.Account;
import brs.Signum;
import brs.SignumException;
import brs.Constants;
import brs.SignumException.ValidationException;
import brs.Transaction;
import brs.TransactionType;
import brs.Attachment.CommitmentRemove;
import brs.Blockchain;
import brs.db.store.AccountStore;
import brs.util.Convert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

class ReservedBalanceCache {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReservedBalanceCache.class);

  private final AccountStore accountStore;

  private final HashMap<Long, Long> reservedBalanceCache;

  public ReservedBalanceCache(AccountStore accountStore) {
    this.accountStore = accountStore;

    this.reservedBalanceCache = new HashMap<>();
  }

  void reserveBalanceAndPut(Transaction transaction) throws SignumException.ValidationException {
    Account senderAccount = null;

    if (transaction.getSenderId() != 0) {
      senderAccount = accountStore.getAccountTable().get(accountStore.getAccountKeyFactory().newKey(transaction.getSenderId()));
    }

    final long thisTransactionAmountNQT = transaction.getType().calculateTotalAmountNQT(transaction);
    final Long amountNQT = Convert.safeAdd(
        reservedBalanceCache.getOrDefault(transaction.getSenderId(), 0L),
        thisTransactionAmountNQT
    );

    if (senderAccount == null) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(String.format("Transaction %d: Account %d does not exist and has no balance. Required funds: %d", transaction.getId(), transaction.getSenderId(), amountNQT));
      }
      throw new SignumException.NotCurrentlyValidException("Account unknown");
    }

    if ( amountNQT > senderAccount.getUnconfirmedBalanceNqt() ) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Transaction {} for {}: account {} balance too low. Total required {} > {} balance",
                Convert.toUnsignedLong(transaction.getId()), thisTransactionAmountNQT, Convert.toUnsignedLong(transaction.getSenderId()), amountNQT, senderAccount.getUnconfirmedBalanceNqt());
      }
      throw new SignumException.NotCurrentlyValidException("Insufficient funds");
    }

    if(transaction.getType() == TransactionType.SignaMining.COMMITMENT_REMOVE) {
      CommitmentRemove commitmentRemove = (CommitmentRemove) transaction.getAttachment();
      long totalAmountNQT = commitmentRemove.getAmountNqt();

      Blockchain blockchain = Signum.getBlockchain();
      int nBlocksMined = blockchain.getBlocksCount(senderAccount.getId(), blockchain.getHeight() - Constants.MAX_ROLLBACK, blockchain.getHeight());
      long amountCommitted = blockchain.getCommittedAmount(senderAccount.getId(), blockchain.getHeight(), blockchain.getHeight(), transaction);
      if (nBlocksMined > 0 || amountCommitted < totalAmountNQT ) {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Transaction {}: Account {} commitment remove not allowed. Blocks mined {}, amount commitment {}, amount removing {}",
              transaction.getId(), transaction.getSenderId(), nBlocksMined, amountCommitted, totalAmountNQT);
        }
        throw new SignumException.NotCurrentlyValidException("Commitment remove not allowed");        
      }
    }

    reservedBalanceCache.put(transaction.getSenderId(), amountNQT);
  }

  void refundBalance(Transaction transaction) {
    Long amountNQT = Convert.safeSubtract(
        reservedBalanceCache.getOrDefault(transaction.getSenderId(), 0L),
        transaction.getType().calculateTotalAmountNQT(transaction)
    );

    if (amountNQT > 0) {
      reservedBalanceCache.put(transaction.getSenderId(), amountNQT);
    } else {
      reservedBalanceCache.remove(transaction.getSenderId());
    }
  }

  public List<Transaction> rebuild(List<Transaction> transactions) {
    clear();

    final List<Transaction> insufficientFundsTransactions = new ArrayList<>();

    for(Transaction t : transactions) {
      try {
        this.reserveBalanceAndPut(t);
      } catch (ValidationException e) {
        insufficientFundsTransactions.add(t);
      }
    }

    return insufficientFundsTransactions;
  }

  public void clear() {
    reservedBalanceCache.clear();
  }

}
