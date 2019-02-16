package brs;

import brs.db.BurstIterator;

import java.util.List;

public interface Blockchain {

  Block getLastBlock();
    
  Block getLastBlock(int timestamp);

  void setLastBlock(Block blockImpl);

  int getHeight();

  Block getBlock(long blockImplId);

  Block getBlockAtHeight(int height);

  boolean hasBlock(long blockImplId);

  BurstIterator<Block> getBlocks(int from, int to);

  BurstIterator<Block> getBlocks(Account account, int timestamp);
    
  BurstIterator<Block> getBlocks(Account account, int timestamp, int from, int to);

  List<Long> getBlockIdsAfter(long blockImplId, int limit);

  List<? extends Block> getBlocksAfter(long blockImplId, int limit);

  long getBlockIdAtHeight(int height);

  Transaction getTransaction(long transactionId);

  Transaction getTransactionByFullHash(String fullHash); // TODO add byte[] method

  boolean hasTransaction(long transactionId);

  boolean hasTransactionByFullHash(String fullHash); // TODO add byte[] method

  int getTransactionCount();

  BurstIterator<Transaction> getAllTransactions();

  BurstIterator<Transaction> getTransactions(Account account, byte type, byte subtype, int blockImplTimestamp);

  BurstIterator<Transaction> getTransactions(Account account, int numberOfConfirmations, byte type, byte subtype, int blockImplTimestamp, int from, int to);

}
