package brs.db.sql;

import brs.*;
import brs.Block;
import brs.Transaction;
import brs.Attachment.CommitmentAdd;
import brs.Attachment.CommitmentRemove;
import brs.db.BlockDb;
import brs.db.TransactionDb;
import brs.db.store.BlockchainStore;
import brs.fluxcapacitor.FluxValues;
import brs.schema.tables.records.BlockRecord;
import brs.schema.tables.records.TransactionRecord;
import brs.util.Convert;

import org.jooq.*;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;

import static brs.schema.Tables.BLOCK;
import static brs.schema.Tables.TRANSACTION;
import static brs.schema.Tables.INDIRECT_INCOMING;

public class SqlBlockchainStore implements BlockchainStore {

  private final Logger logger = LoggerFactory.getLogger(SqlBlockchainStore.class);

  private final TransactionDb transactionDb = Signum.getDbs().getTransactionDb();
  private final BlockDb blockDb = Signum.getDbs().getBlockDb();

  public SqlBlockchainStore() {
  }

  @Override
  public Collection<Block> getBlocks(int from, int to) {
    return Db.useDSLContext(ctx -> {
      int blockchainHeight = Signum.getBlockchain().getHeight();
      return
        getBlocks(ctx.selectFrom(BLOCK)
          .where(BLOCK.HEIGHT.between(blockchainHeight - Math.max(to, 0)).and(blockchainHeight - Math.max(from, 0)))
          .orderBy(BLOCK.HEIGHT.desc())
          .fetch());
    });
  }

  @Override
  public Collection<Block> getBlocks(Account account, int timestamp, int from, int to) {
    return Db.useDSLContext(ctx -> {

      SelectConditionStep<BlockRecord> query = ctx.selectFrom(BLOCK).where(BLOCK.GENERATOR_ID.eq(account.getId()));
      if (timestamp > 0) {
        query.and(BLOCK.TIMESTAMP.ge(timestamp));
      }
      SelectQuery<BlockRecord> selectQuery = query.orderBy(BLOCK.HEIGHT.desc()).getQuery();
      DbUtils.applyLimits(selectQuery, from, to);
      return getBlocks(selectQuery.fetch());
    });
  }

  @Override
  public int getBlocksCount(long accountId, int from, int to) {
    if (from > to) {
      return 0;
    }
    return Db.useDSLContext(ctx -> {
      SelectConditionStep<BlockRecord> query = ctx.selectFrom(BLOCK).where(BLOCK.GENERATOR_ID.eq(accountId))
        .and(BLOCK.HEIGHT.between(from).and(to));

      return ctx.fetchCount(query);
    });
  }

  @Override
  public Collection<Block> getBlocks(Result<BlockRecord> blockRecords) {
    return blockRecords.map(blockRecord -> {
      try {
        return blockDb.loadBlock(blockRecord);
      } catch (SignumException.ValidationException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Override
  public Collection<Long> getBlockIdsAfter(long blockId, int limit) {
    if (limit > 1440) {
      throw new IllegalArgumentException("Can't get more than 1440 blocks at a time");
    }

    return Db.useDSLContext(ctx -> {
      return
        ctx.selectFrom(BLOCK).where(
          BLOCK.HEIGHT.gt(ctx.select(BLOCK.HEIGHT).from(BLOCK).where(BLOCK.ID.eq(blockId)))
        ).orderBy(BLOCK.HEIGHT.asc()).limit(limit).fetch(BLOCK.ID, Long.class);
    });
  }

  @Override
  public Collection<Block> getBlocksAfter(long blockId, int limit) {
    if (limit > 1440) {
      throw new IllegalArgumentException("Can't get more than 1440 blocks at a time");
    }
    return Db.useDSLContext(ctx -> {
      return ctx.selectFrom(BLOCK)
        .where(BLOCK.HEIGHT.gt(ctx.select(BLOCK.HEIGHT)
          .from(BLOCK)
          .where(BLOCK.ID.eq(blockId))))
        .orderBy(BLOCK.HEIGHT.asc())
        .limit(limit)
        .fetch(result -> {
          try {
            return blockDb.loadBlock(result);
          } catch (SignumException.ValidationException e) {
            throw new RuntimeException(e.toString(), e);
          }
        });
    });
  }

  @Override
  public int getTransactionCount() {
    return Db.useDSLContext(ctx -> {
      return ctx.selectCount().from(TRANSACTION).fetchOne(0, int.class);
    });
  }

  @Override
  public Collection<Transaction> getAllTransactions() {
    return Db.useDSLContext(ctx -> {
      return getTransactions(ctx, ctx.selectFrom(TRANSACTION).orderBy(TRANSACTION.DB_ID.asc()).fetch());
    });
  }

  @Override
  public long getAtBurnTotal() {
    return Db.useDSLContext(ctx -> {
      return ctx.select(DSL.sum(TRANSACTION.AMOUNT)).from(TRANSACTION)
        .where(TRANSACTION.RECIPIENT_ID.isNull())
        .and(TRANSACTION.AMOUNT.gt(0L))
        .and(TRANSACTION.TYPE.equal(TransactionType.TYPE_AUTOMATED_TRANSACTIONS.getType()))
        .fetchOneInto(long.class);
    });
  }


  @Override
  public Collection<Transaction> getTransactions(Account account, int numberOfConfirmations, byte type, byte subtype, int blockTimestamp, int from, int to, boolean includeIndirectIncoming) {

    System.out.println("Modified getTransactions....");
    int height = getHeightForNumberOfConfirmations(numberOfConfirmations);
    return Db.useDSLContext(ctx -> {
      ArrayList<Condition> conditions = new ArrayList<>();

      // Common conditions
      if (blockTimestamp > 0) {
        conditions.add(TRANSACTION.BLOCK_TIMESTAMP.ge(blockTimestamp));
      }
      if (type >= 0) {
        conditions.add(TRANSACTION.TYPE.eq(type));
        if (subtype >= 0) {
          conditions.add(TRANSACTION.SUBTYPE.eq(subtype));
        }
      }
      if (height < Integer.MAX_VALUE) {
        conditions.add(TRANSACTION.HEIGHT.le(height));
      }

      // Single query with comprehensive conditions
      Condition accountCondition = DSL.trueCondition();
      if (account != null) {
        accountCondition = TRANSACTION.RECIPIENT_ID.eq(account.getId())
          .and(TRANSACTION.SENDER_ID.ne(account.getId()))
          .or(TRANSACTION.SENDER_ID.eq(account.getId()));

        if (includeIndirectIncoming) {
          accountCondition = accountCondition.or(
            TRANSACTION.ID.in(
              DSL.select(INDIRECT_INCOMING.TRANSACTION_ID)
                .from(INDIRECT_INCOMING)
                .where(INDIRECT_INCOMING.ACCOUNT_ID.eq(account.getId()))
            )
          );
        }
      }

      SelectQuery<TransactionRecord> selectQuery = ctx.selectFrom(TRANSACTION)
        .where(conditions)
        .and(accountCondition)
        .orderBy(TRANSACTION.BLOCK_TIMESTAMP.desc(), TRANSACTION.ID.desc())
        .getQuery();

      DbUtils.applyLimits(selectQuery, from, to);

      return getTransactions(ctx, selectQuery.fetch());
    });
  }

  private static int getHeightForNumberOfConfirmations(int numberOfConfirmations) {
    int height = numberOfConfirmations > 0 ? Signum.getBlockchain().getHeight() - numberOfConfirmations : Integer.MAX_VALUE;
    if (height < 0) {
      throw new IllegalArgumentException("Number of confirmations required " + numberOfConfirmations + " exceeds current blockchain height " + Signum.getBlockchain().getHeight());
    }
    return height;
  }

  // TODO: better introduce a dedicated bySender, byRecipient endpoint to reduce complexity
  @Override
  public Collection<Transaction> getTransactions(Long senderId, Long recipientId, int numberOfConfirmations, byte type, byte subtype, int blockTimestamp, int from, int to, boolean includeIndirectIncoming, boolean bidirectional) {
    int height = getHeightForNumberOfConfirmations(numberOfConfirmations);
    return Db.useDSLContext(ctx -> {
      ArrayList<Condition> conditions = new ArrayList<>();

      boolean hasSender = senderId != null;
      boolean hasRecipient = recipientId != null; // consider burn address also

      if (blockTimestamp > 0) {
        conditions.add(TRANSACTION.BLOCK_TIMESTAMP.ge(blockTimestamp));
      }
      if (type >= 0) {
        conditions.add(TRANSACTION.TYPE.eq(type));
        if (subtype >= 0) {
          conditions.add(TRANSACTION.SUBTYPE.eq(subtype));
        }
      }
      if (height < Integer.MAX_VALUE) {
        conditions.add(TRANSACTION.HEIGHT.le(height));
      }

      SelectOrderByStep<TransactionRecord> select = null;
      if (!bidirectional) {
        select = ctx
          .selectFrom(TRANSACTION)
          .where(conditions)
          .and(hasSender ? TRANSACTION.SENDER_ID.eq(senderId) : null)
          .and(hasRecipient ? TRANSACTION.RECIPIENT_ID.eq(recipientId) : null);
      } else {
        select = ctx
          .selectFrom(TRANSACTION)
          .where(conditions)
          .and(hasSender ? TRANSACTION.SENDER_ID.eq(senderId).or(TRANSACTION.RECIPIENT_ID.eq(senderId)) : null)
          .and(hasRecipient ? TRANSACTION.RECIPIENT_ID.eq(recipientId).or(TRANSACTION.SENDER_ID.eq(recipientId)) : null);
      }

      if (includeIndirectIncoming) {
        // makes only sense if for recipient. Sender is implicitely included.
        if (!bidirectional && hasRecipient) {
          select = select.unionAll(ctx
            .selectFrom(TRANSACTION)
            .where(conditions)
            .and(TRANSACTION.ID.in(ctx
                .select(INDIRECT_INCOMING.TRANSACTION_ID)
                .from(INDIRECT_INCOMING)
                .where(INDIRECT_INCOMING.ACCOUNT_ID.eq(recipientId))
              )
            ));
        }

        if (bidirectional) {
          select = select.unionAll(ctx
            .selectFrom(TRANSACTION)
            .where(conditions)
            .and(TRANSACTION.ID.in(ctx
                .select(INDIRECT_INCOMING.TRANSACTION_ID)
                .from(INDIRECT_INCOMING)
                .where(hasRecipient ? INDIRECT_INCOMING.ACCOUNT_ID.eq(recipientId) : null)
                .or(hasSender ? INDIRECT_INCOMING.ACCOUNT_ID.eq(senderId) : null)
              )
            )
          );
        }
      }

      SelectQuery<TransactionRecord> selectQuery = select
        .orderBy(TRANSACTION.BLOCK_TIMESTAMP.desc(), TRANSACTION.ID.desc())
        .getQuery();

      DbUtils.applyLimits(selectQuery, from, to);

      return getTransactions(ctx, selectQuery.fetch());
    });
  }

  @Override
  public Collection<Transaction> getTransactions(long senderId, byte type, byte subtypeStart, byte subtypeEnd, int from, int to) {
    return Db.useDSLContext(ctx -> {
      ArrayList<Condition> conditions = new ArrayList<>();
      if (type >= 0) {
        conditions.add(TRANSACTION.TYPE.eq(type));
        if (subtypeStart >= 0) {
          conditions.add(TRANSACTION.SUBTYPE.ge(subtypeStart));
        }
        if (subtypeEnd >= 0) {
          conditions.add(TRANSACTION.SUBTYPE.le(subtypeEnd));
        }
      }

      SelectOrderByStep<TransactionRecord> select = ctx.selectFrom(TRANSACTION).where(conditions).and(
        TRANSACTION.SENDER_ID.eq(senderId));

      SelectQuery<TransactionRecord> selectQuery = select
        .orderBy(TRANSACTION.BLOCK_TIMESTAMP.desc(), TRANSACTION.ID.desc())
        .getQuery();

      DbUtils.applyLimits(selectQuery, from, to);

      return getTransactions(ctx, selectQuery.fetch());
    });
  }

  @Override
  public int countTransactions(byte type, byte subtypeStart, byte subtypeEnd) {
    return Db.useDSLContext(ctx -> {
      ArrayList<Condition> conditions = new ArrayList<>();
      if (type >= 0) {
        conditions.add(TRANSACTION.TYPE.eq(type));
        if (subtypeStart >= 0) {
          conditions.add(TRANSACTION.SUBTYPE.ge(subtypeStart));
        }
        if (subtypeEnd >= 0) {
          conditions.add(TRANSACTION.SUBTYPE.le(subtypeEnd));
        }
      }

      SelectOrderByStep<TransactionRecord> select = ctx.selectFrom(TRANSACTION).where(conditions);

      return ctx.fetchCount(select);
    });
  }

  @Override
  public Collection<Transaction> getTransactionsWithFullHashReference(String fullHash, int numberOfConfirmations, byte type, byte subtypeStart, byte subtypeEnd, int from, int to) {
    return Db.useDSLContext(ctx -> {
      ArrayList<Condition> conditions = new ArrayList<>();

      // must be confirmed already
      int height = Signum.getBlockchain().getHeight() - numberOfConfirmations;
      conditions.add(TRANSACTION.HEIGHT.le(height));
      if (type >= 0) {
        conditions.add(TRANSACTION.TYPE.eq(type));
        if (subtypeStart >= 0) {
          conditions.add(TRANSACTION.SUBTYPE.ge(subtypeStart));
        }
        if (subtypeEnd >= 0) {
          conditions.add(TRANSACTION.SUBTYPE.le(subtypeEnd));
        }
      }

      SelectOrderByStep<TransactionRecord> select = ctx.selectFrom(TRANSACTION).where(conditions).and(
        TRANSACTION.REFERENCED_TRANSACTION_FULLHASH.eq(Convert.parseHexString(fullHash)));

      SelectQuery<TransactionRecord> selectQuery = select
        .orderBy(TRANSACTION.BLOCK_TIMESTAMP.desc(), TRANSACTION.ID.desc())
        .getQuery();

      DbUtils.applyLimits(selectQuery, from, to);

      return getTransactions(ctx, selectQuery.fetch());
    });
  }

  @Override
  public Collection<Transaction> getTransactions(DSLContext ctx, Result<TransactionRecord> rs) {
    return rs.map(r -> {
      try {
        return transactionDb.loadTransaction(r);
      } catch (SignumException.ValidationException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Override
  public void addBlock(Block block) {
    Db.useDSLContext(ctx -> {
      blockDb.saveBlock(ctx, block);
    });
  }

  @Override
  public Collection<Block> getLatestBlocks(int amountBlocks) {
    final int latestBlockHeight = blockDb.findLastBlock().getHeight();

    final int firstLatestBlockHeight = Math.max(0, latestBlockHeight - amountBlocks);

    return Db.useDSLContext(ctx -> {
      return getBlocks(ctx.selectFrom(BLOCK)
        .where(BLOCK.HEIGHT.between(firstLatestBlockHeight).and(latestBlockHeight))
        .orderBy(BLOCK.HEIGHT.asc())
        .fetch());
    });
  }

  @Override
  public long getCommittedAmount(long accountId, int height, int endHeight, Transaction skipTransaction) {
    int commitmentWait = Signum.getFluxCapacitor().getValue(FluxValues.COMMITMENT_WAIT, height);
    int commitmentHeight = Math.min(height - commitmentWait, endHeight);

    Collection<byte[]> commitmmentAddBytes = Db.useDSLContext(ctx -> {
      SelectConditionStep<Record1<byte[]>> select = ctx.select(TRANSACTION.ATTACHMENT_BYTES).from(TRANSACTION).where(TRANSACTION.TYPE.eq(TransactionType.TYPE_SIGNA_MINING.getType()))
        .and(TRANSACTION.SUBTYPE.eq(TransactionType.SUBTYPE_SIGNA_MINING_COMMITMENT_ADD))
        .and(TRANSACTION.HEIGHT.le(commitmentHeight));
      if (accountId != 0L)
        select = select.and(TRANSACTION.SENDER_ID.equal(accountId));
      return select.fetch().getValues(TRANSACTION.ATTACHMENT_BYTES);
    });
    Collection<byte[]> commitmmentRemoveBytes = Db.useDSLContext(ctx -> {
      SelectConditionStep<Record1<byte[]>> select = ctx.select(TRANSACTION.ATTACHMENT_BYTES).from(TRANSACTION).where(TRANSACTION.TYPE.eq(TransactionType.TYPE_SIGNA_MINING.getType()))
        .and(TRANSACTION.SUBTYPE.eq(TransactionType.SUBTYPE_SIGNA_MINING_COMMITMENT_REMOVE))
        .and(TRANSACTION.HEIGHT.le(endHeight));
      if (accountId != 0L)
        select = select.and(TRANSACTION.SENDER_ID.equal(accountId));
      if (skipTransaction != null)
        select = select.and(TRANSACTION.ID.ne(skipTransaction.getId()));
      return select.fetch().getValues(TRANSACTION.ATTACHMENT_BYTES);
    });

    BigInteger amountCommitted = BigInteger.ZERO;
    for (byte[] bytes : commitmmentAddBytes) {
      try {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        CommitmentAdd txAttachment = (CommitmentAdd) TransactionType.SignaMining.COMMITMENT_ADD.parseAttachment(buffer, (byte) 1);
        amountCommitted = amountCommitted.add(BigInteger.valueOf(txAttachment.getAmountNqt()));
      } catch (Exception e) {
        logger.error(e.getMessage());
      }
    }
    for (byte[] bytes : commitmmentRemoveBytes) {
      try {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        CommitmentRemove txAttachment = (CommitmentRemove) TransactionType.SignaMining.COMMITMENT_REMOVE.parseAttachment(buffer, (byte) 1);
        amountCommitted = amountCommitted.subtract(BigInteger.valueOf(txAttachment.getAmountNqt()));
      } catch (Exception e) {
        logger.error(e.getMessage());
      }
    }
    if (amountCommitted.compareTo(BigInteger.ZERO) < 0) {
      // should never happen
      amountCommitted = BigInteger.ZERO;
    }
    return amountCommitted.longValue();
  }

  @Override
  public Collection<Long> getTransactionIds(Long sender, Long recipient, int numberOfConfirmations, byte type,
                                            byte subtype, int blockTimestamp, int from, int to, boolean includeIndirectIncoming) {

    int height = getHeightForNumberOfConfirmations(numberOfConfirmations);
    return Db.useDSLContext(ctx -> {
      ArrayList<Condition> conditions = new ArrayList<>();
      if (blockTimestamp > 0) {
        conditions.add(TRANSACTION.BLOCK_TIMESTAMP.ge(blockTimestamp));
      }
      if (type >= 0) {
        conditions.add(TRANSACTION.TYPE.eq(type));
        if (subtype >= 0) {
          conditions.add(TRANSACTION.SUBTYPE.eq(subtype));
        }
      }
      if (height < Integer.MAX_VALUE) {
        conditions.add(TRANSACTION.HEIGHT.le(height));
      }

      SelectConditionStep<TransactionRecord> select = ctx.selectFrom(TRANSACTION).where(conditions);

      if (recipient != null) {
        select = select.and(TRANSACTION.RECIPIENT_ID.eq(recipient));
      }
      if (sender != null) {
        select = select.and(TRANSACTION.SENDER_ID.eq(sender));
      }

      SelectOrderByStep<TransactionRecord> selectOrder = select;

      if (includeIndirectIncoming && recipient != null) {
        selectOrder = selectOrder.unionAll(ctx.selectFrom(TRANSACTION)
          .where(conditions)
          .and(TRANSACTION.ID.in(ctx.select(INDIRECT_INCOMING.TRANSACTION_ID).from(INDIRECT_INCOMING)
            .where(INDIRECT_INCOMING.ACCOUNT_ID.eq(recipient)))));
      }

      SelectQuery<TransactionRecord> selectQuery = selectOrder
        .orderBy(TRANSACTION.BLOCK_TIMESTAMP.desc(), TRANSACTION.ID.desc())
        .getQuery();

      DbUtils.applyLimits(selectQuery, from, to);

      return selectQuery.fetch(TRANSACTION.ID, Long.class);
    });
  }
}
