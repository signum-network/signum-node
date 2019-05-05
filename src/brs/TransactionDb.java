package brs;

import brs.schema.tables.records.TransactionRecord;
import org.jooq.DSLContext;

import java.sql.ResultSet;
import java.util.List;

public interface TransactionDb {
  Transaction findTransaction(long transactionId);

  Transaction findTransactionByFullHash(String fullHash); // TODO add byte[] method

  boolean hasTransaction(long transactionId);

  boolean hasTransactionByFullHash(String fullHash); // TODO add byte[] method

  Transaction loadTransaction(TransactionRecord transactionRecord) throws BurstException.ValidationException;

  List<Transaction> findBlockTransactions(long blockId);

  void saveTransactions(List<Transaction> transactions);

}
