package brs.db.sql;

import brs.*;
import brs.Block;
import brs.Transaction;
import brs.db.BlockDb;
import brs.db.TransactionDb;
import brs.db.store.BlockchainStore;
import brs.db.store.IndirectIncomingStore;
import brs.schema.tables.records.BlockRecord;
import brs.schema.tables.records.TransactionRecord;
import org.jooq.*;

import java.util.ArrayList;
import java.util.Collection;

import static brs.schema.Tables.BLOCK;
import static brs.schema.Tables.TRANSACTION;

public class SqlBlockchainStore implements BlockchainStore {

  private final TransactionDb transactionDb = Burst.getDbs().getTransactionDb();
  private final BlockDb blockDb = Burst.getDbs().getBlockDb();
  private final IndirectIncomingStore indirectIncomingStore;

  public SqlBlockchainStore(IndirectIncomingStore indirectIncomingStore) {
    this.indirectIncomingStore = indirectIncomingStore;
  }

  @Override
  public Collection<Block> getBlocks(int from, int to) {
    return Db.useDSLContext(ctx -> {
      int blockchainHeight = Burst.getBlockchain().getHeight();
      return
        getBlocks(ctx.selectFrom(BLOCK)
                .where(BLOCK.HEIGHT.between(to > 0 ? blockchainHeight - to : 0).and(blockchainHeight - Math.max(from, 0)))
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
      return getBlocks(query.orderBy(BLOCK.HEIGHT.desc()).fetch());
    });
  }

  @Override
  public Collection<Block> getBlocks(Result<BlockRecord> blockRecords) {
    return blockRecords.map(blockRecord -> {
      try {
        return blockDb.loadBlock(blockRecord);
      } catch (BurstException.ValidationException e) {
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
          BLOCK.HEIGHT.gt( ctx.select(BLOCK.HEIGHT).from(BLOCK).where(BLOCK.ID.eq(blockId) ) )
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
                } catch (BurstException.ValidationException e) {
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
  public Collection<Transaction> getTransactions(Account account, int numberOfConfirmations, byte type, byte subtype, int blockTimestamp, int from, int to, boolean includeIndirectIncoming) {
    int height = numberOfConfirmations > 0 ? Burst.getBlockchain().getHeight() - numberOfConfirmations : Integer.MAX_VALUE;
    if (height < 0) {
      throw new IllegalArgumentException("Number of confirmations required " + numberOfConfirmations + " exceeds current blockchain height " + Burst.getBlockchain().getHeight());
    }
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

      SelectOrderByStep<TransactionRecord> select = ctx.selectFrom(TRANSACTION).where(conditions).and(
              TRANSACTION.RECIPIENT_ID.eq(account.getId()).and(
                      TRANSACTION.SENDER_ID.ne(account.getId())
              )
      ).unionAll(
              ctx.selectFrom(TRANSACTION).where(conditions).and(
                      TRANSACTION.SENDER_ID.eq(account.getId())
              )
      );

      if (includeIndirectIncoming) {
        select = select.unionAll(ctx.selectFrom(TRANSACTION)
                .where(conditions)
                .and(TRANSACTION.ID.in(indirectIncomingStore.getIndirectIncomings(account.getId(), from, to))));
      }

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
      } catch (BurstException.ValidationException e) {
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
}
