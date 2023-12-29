package brs;

import brs.db.BlockDb;
import brs.db.TransactionDb;
import brs.db.store.BlockchainStore;
import brs.props.PropertyService;
import brs.props.Props;
import brs.util.CollectionWithIndex;
import brs.util.StampedLockUtils;

import java.math.BigInteger;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Supplier;

public class BlockchainImpl implements Blockchain {

  private final TransactionDb transactionDb;
  private final BlockDb blockDb;
  private final BlockchainStore blockchainStore;
  private final PropertyService propertyService;

  private final StampedLock bcsl;

  BlockchainImpl(TransactionDb transactionDb, BlockDb blockDb, BlockchainStore blockchainStore, PropertyService propertyService) {
    this.transactionDb = transactionDb;
    this.blockDb = blockDb;
    this.blockchainStore = blockchainStore;
    this.propertyService = propertyService;
    this.bcsl = new StampedLock();
  }

  private final AtomicReference<Block> lastBlock = new AtomicReference<>();

  private <T> T bcslRead(Supplier<T> supplier) {
    return StampedLockUtils.stampedLockRead(bcsl, supplier);
  }

  @Override
  public Block getLastBlock() {
    return bcslRead(lastBlock::get);
  }

  @Override
  public void setLastBlock(Block block) {
    long stamp = bcsl.writeLock();
    try {
      lastBlock.set(block);
    } finally {
      bcsl.unlockWrite(stamp);
    }
  }

  void setLastBlock(Block previousBlock, Block block) {
    long stamp = bcsl.writeLock();
    try {
      if (! lastBlock.compareAndSet(previousBlock, block)) {
        throw new IllegalStateException("Last block is no longer previous block");
      }
    } finally {
      bcsl.unlockWrite(stamp);
    }
  }

  @Override
  public int getHeight() {
    Block last = getLastBlock();
    return last == null ? 0 : last.getHeight();
  }

  @Override
  public Block getLastBlock(int timestamp) {
    Block block = getLastBlock();
    if (timestamp >= block.getTimestamp()) {
      return block;
    }
    return blockDb.findLastBlock(timestamp);
  }

  @Override
  public Block getBlock(long blockId) {
    Block block = getLastBlock();
    if (block.getId() == blockId) {
      return block;
    }
    return blockDb.findBlock(blockId);
  }

  @Override
  public boolean hasBlock(long blockId) {
    return getLastBlock().getId() == blockId || blockDb.hasBlock(blockId);
  }

  @Override
  public Collection<Block> getBlocks(int from, int to) {
    return blockchainStore.getBlocks(from, to);
  }

  @Override
  public Collection<Block> getBlocks(Account account, int timestamp) {
    return getBlocks(account, timestamp, 0, -1).getCollection();
  }

  @Override
  public CollectionWithIndex<Block> getBlocks(Account account, int timestamp, int from, int to) {
    return new CollectionWithIndex<Block>(blockchainStore.getBlocks(account, timestamp, from, to), from, to);
  }

  @Override
  public int getBlocksCount(long accountId, int from, int to) {
    return blockchainStore.getBlocksCount(accountId, from, to);
  }

  @Override
  public Collection<Long> getBlockIdsAfter(long blockId, int limit) {
    return blockchainStore.getBlockIdsAfter(blockId, limit);
  }

  @Override
  public Collection<Block> getBlocksAfter(long blockId, int limit) {
    return blockchainStore.getBlocksAfter(blockId, limit);
  }

  @Override
  public long getBlockIdAtHeight(int height) {
    Block block = getLastBlock();
    if (height > block.getHeight()) {
      throw new IllegalArgumentException("Invalid height " + height + ", current blockchain is at " + block.getHeight());
    }
    if (height == block.getHeight()) {
      return block.getId();
    }
    return blockDb.findBlockIdAtHeight(height);
  }

  @Override
  public Block getBlockAtHeight(int height) {
    Block block = getLastBlock();
    if (height > block.getHeight()) {
      throw new IllegalArgumentException("Invalid height " + height + ", current blockchain is at " + block.getHeight());
    }
    if (height == block.getHeight()) {
      return block;
    }
    return blockDb.findBlockAtHeight(height);
  }

  @Override
  public Transaction getTransaction(long transactionId) {
    return transactionDb.findTransaction(transactionId);
  }

  @Override
  public Transaction getTransactionByFullHash(String fullHash) {
    return transactionDb.findTransactionByFullHash(fullHash);
  }

  @Override
  public boolean hasTransaction(long transactionId) {
    return transactionDb.hasTransaction(transactionId);
  }

  @Override
  public boolean hasTransactionByFullHash(String fullHash) {
    return transactionDb.hasTransactionByFullHash(fullHash);
  }

  @Override
  public int getTransactionCount() {
    return blockchainStore.getTransactionCount();
  }

  @Override
  public Collection<Transaction> getAllTransactions() {
    return blockchainStore.getAllTransactions();
  }

  @Override
  public long getAtBurnTotal(){
    return blockchainStore.getAtBurnTotal();
  }

  @Override
  public long getBlockReward(int height) {
    if (height == 0) {
      return 0;
    }

    long ONE_COIN = propertyService.getInt(Props.ONE_COIN_NQT);

    if (height >= propertyService.getInt(Props.BLOCK_REWARD_LIMIT_HEIGHT)) {
      // Minimum incentive, lower than 0.6 % per year
      return propertyService.getInt(Props.BLOCK_REWARD_LIMIT_AMOUNT) * ONE_COIN;
    }
    int month = height / propertyService.getInt(Props.BLOCK_REWARD_CYCLE);
    int percentage = propertyService.getInt(Props.BLOCK_REWARD_CYCLE_PERCENTAGE);
    int start = propertyService.getInt(Props.BLOCK_REWARD_START);
    return BigInteger.valueOf(start).multiply(BigInteger.valueOf(percentage).pow(month))
      .divide(BigInteger.valueOf(100).pow(month)).longValue() * ONE_COIN;
  }

  @Override
  public long getTotalMined() {
    long totalMined = 0;
    int height = getHeight();
    long blockReward = getBlockReward(1);
    int blockMonth = 0;
    int rewardCycle = propertyService.getInt(Props.BLOCK_REWARD_CYCLE);
    int decreaseStopHeight = propertyService.getInt(Props.BLOCK_REWARD_LIMIT_HEIGHT);
    long ONE_COIN = propertyService.getInt(Props.ONE_COIN_NQT);
    long LIMIT_AMOUNT = propertyService.getInt(Props.BLOCK_REWARD_LIMIT_AMOUNT) * ONE_COIN;

    for (int i=1; i <= height; i++) {
      if (i >= decreaseStopHeight) {
        blockReward = LIMIT_AMOUNT;
      }
      else {
        int cycle = i / rewardCycle;
        if(cycle != blockMonth) {
          blockReward = getBlockReward(i);
          blockMonth = cycle;
        }
      }
      totalMined += blockReward;
    }

    return totalMined;
  }

  @Override
  public Collection<Transaction> getTransactions(Account account, byte type, byte subtype, int blockTimestamp, boolean includeIndirectIncoming) {
    return getTransactions(account, 0, type, subtype, blockTimestamp, 0, -1, includeIndirectIncoming).getCollection();
  }

  @Override
  public CollectionWithIndex<Transaction> getTransactions(Account account, int numberOfConfirmations, byte type, byte subtype,
                                                 int blockTimestamp, int from, int to, boolean includeIndirectIncoming) {
    return new CollectionWithIndex<>(blockchainStore.getTransactions(account, numberOfConfirmations, type, subtype, blockTimestamp, from, to, includeIndirectIncoming), from, to);
  }

  @Override
  public CollectionWithIndex<Transaction> getTransactions(Long senderId, Long recipientId, int numberOfConfirmations, byte type, byte subtype, int blockTimestamp, int from, int to, boolean includeIndirectIncoming, boolean bidirectional) {
    return new CollectionWithIndex<>(blockchainStore.getTransactions(senderId, recipientId, numberOfConfirmations, type, subtype, blockTimestamp, from, to, includeIndirectIncoming, bidirectional), from, to);
  }

  @Override
  public Collection<Transaction> getTransactions(long senderId, byte type, byte subtypeStart, byte subtypeEnd, int from, int to) {
    return blockchainStore.getTransactions(senderId, type, subtypeStart, subtypeEnd, from, to);
  }

  @Override
  public int countTransactions(byte type, byte subtypeStart, byte subtypeEnd) {
    return blockchainStore.countTransactions(type, subtypeStart, subtypeEnd);
  }

  @Override
  public Collection<Transaction> getTransactionsWithFullHashReference(String fullHash, int numberOfConfirmations, byte type, byte subtypeStart, byte subtypeEnd, int from, int to) {
    return blockchainStore.getTransactionsWithFullHashReference(fullHash, numberOfConfirmations, type, subtypeStart, subtypeEnd, from, to);
  }

  @Override
  public Collection<Long> getTransactionIds(Long sender, Long recipient, int numberOfConfirmations, byte type,
      byte subtype, int blockTimestamp, int from, int to, boolean includeIndirectIncoming) {
    return blockchainStore.getTransactionIds(sender, recipient, numberOfConfirmations, type, subtype, blockTimestamp, from, to, includeIndirectIncoming);
  }

  @Override
  public long getCommittedAmount(long accountId, int height, int endHeight, Transaction skipTransaction) {
    return blockchainStore.getCommittedAmount(accountId, height, endHeight, skipTransaction);
  }
}
