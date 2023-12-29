package brs;

import java.util.Collection;

import brs.util.CollectionWithIndex;

public interface Blockchain {

  Block getLastBlock();

  Block getLastBlock(int timestamp);

  void setLastBlock(Block blockImpl);

  int getHeight();

  Block getBlock(long blockImplId);

  Block getBlockAtHeight(int height);

  boolean hasBlock(long blockImplId);

  Collection<Block> getBlocks(int from, int to);

  Collection<Block> getBlocks(Account account, int timestamp);

  CollectionWithIndex<Block> getBlocks(Account account, int timestamp, int from, int to);

  int getBlocksCount(long accountId, int from, int to);

  Collection<Long> getBlockIdsAfter(long blockImplId, int limit);

  Collection<Block> getBlocksAfter(long blockId, int limit);

  long getBlockIdAtHeight(int height);

  Transaction getTransaction(long transactionId);

  Transaction getTransactionByFullHash(String fullHash); // TODO add byte[] method

  boolean hasTransaction(long transactionId);

  boolean hasTransactionByFullHash(String fullHash); // TODO add byte[] method

  int getTransactionCount();

  Collection<Transaction> getAllTransactions();

  public long getAtBurnTotal();

  public long getTotalMined();

  long getBlockReward(int height);

  Collection<Transaction> getTransactions(Account account, byte type, byte subtype, int blockTimestamp, boolean includeIndirectIncoming);

  CollectionWithIndex<Transaction> getTransactions(Account account, int numberOfConfirmations, byte type, byte subtype, int blockTimestamp, int from, int to, boolean includeIndirectIncoming);
  CollectionWithIndex<Transaction> getTransactions(Long senderId, Long recipientId, int numberOfConfirmations, byte type, byte subtype, int blockTimestamp, int from, int to, boolean includeIndirectIncoming, boolean bidirectional);

  Collection<Transaction> getTransactions(long senderId, byte type, byte subtypeStart, byte subtypeEnd, int from, int to);

  int countTransactions(byte type, byte subtypeStart, byte subtypeEnd);

  Collection<Transaction> getTransactionsWithFullHashReference(String fullHash, int numberOfConfirmations, byte type, byte subtypeStart, byte subtypeEnd, int from, int to);

  Collection<Long> getTransactionIds(Long sender, Long recipient, int numberOfConfirmations, byte type, byte subtype, int blockTimestamp, int from, int to, boolean includeIndirectIncoming);

  long getCommittedAmount(long accountId, int height, int endHeight, Transaction skipTransaction);
}
