package brs.db.store;

import brs.Account;
import brs.Block;
import brs.Transaction;
import brs.schema.tables.records.BlockRecord;
import brs.schema.tables.records.TransactionRecord;
import org.jooq.DSLContext;
import org.jooq.Result;

import java.sql.ResultSet;
import java.util.Collection;
import java.util.List;

/**
 * Store for both BlockchainImpl and BlockchainProcessorImpl
 */

public interface BlockchainStore {


  Collection<Block> getBlocks(int from, int to);

  Collection<Block> getBlocks(Account account, int timestamp, int from, int to);

  Collection<Block> getBlocks(Result<BlockRecord> blockRecords);

  List<Long> getBlockIdsAfter(long blockId, int limit);

  List<Block> getBlocksAfter(long blockId, int limit);

  int getTransactionCount();

  Collection<Transaction> getAllTransactions();

  Collection<Transaction> getTransactions(Account account, int numberOfConfirmations, byte type, byte subtype,
                                                 int blockTimestamp, int from, int to, boolean includeIndirectIncoming);

  Collection<Transaction> getTransactions(DSLContext ctx, Result<TransactionRecord> rs);

  void addBlock(Block block);

  Collection<Block> getLatestBlocks(int amountBlocks);
}
