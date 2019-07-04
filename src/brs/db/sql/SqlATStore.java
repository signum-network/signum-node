package brs.db.sql;

import brs.Burst;
import brs.at.AT;
import brs.at.AtApiHelper;
import brs.at.AtConstants;
import brs.db.BurstKey;
import brs.db.VersionedEntityTable;
import brs.db.store.ATStore;
import brs.db.store.DerivedTableManager;
import brs.schema.tables.records.AtRecord;
import brs.schema.tables.records.AtStateRecord;
import org.jooq.*;
import org.jooq.exception.DataAccessException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static brs.schema.Tables.*;

public class SqlATStore implements ATStore {

  private final BurstKey.LongKeyFactory<brs.at.AT> atDbKeyFactory = new DbKey.LongKeyFactory<brs.at.AT>(AT.ID) {
      @Override
      public BurstKey newKey(brs.at.AT at) {
        return at.dbKey;
      }
    };
  private final VersionedEntityTable<brs.at.AT> atTable;

  private final BurstKey.LongKeyFactory<brs.at.AT.ATState> atStateDbKeyFactory = new DbKey.LongKeyFactory<brs.at.AT.ATState>(AT_STATE.AT_ID) {
      @Override
      public BurstKey newKey(brs.at.AT.ATState atState) {
        return atState.dbKey;
      }
    };

  private final VersionedEntityTable<brs.at.AT.ATState> atStateTable;

  public SqlATStore(DerivedTableManager derivedTableManager) {
    atTable = new VersionedEntitySqlTable<brs.at.AT>("at", brs.schema.Tables.AT, atDbKeyFactory, derivedTableManager) {
      @Override
      protected brs.at.AT load(DSLContext ctx, Record rs) {
        throw new RuntimeException("AT attempted to be created with atTable.load");
      }

      @Override
      protected void save(DSLContext ctx, brs.at.AT at) {
        saveAT(ctx, at);
      }

      @Override
      protected List<SortField<?>> defaultSort() {
        List<SortField<?>> sort = new ArrayList<>();
        sort.add(tableClass.field("id", Long.class).asc());
        return sort;
      }
    };

    atStateTable = new VersionedEntitySqlTable<brs.at.AT.ATState>("at_state", brs.schema.Tables.AT_STATE, atStateDbKeyFactory, derivedTableManager) {
      @Override
      protected brs.at.AT.ATState load(DSLContext ctx, Record rs) {
        return new SqlATState(rs);
      }

      @Override
      protected void save(DSLContext ctx, brs.at.AT.ATState atState) {
        saveATState(ctx, atState);
      }

      @Override
      protected List<SortField<?>> defaultSort() {
        List<SortField<?>> sort = new ArrayList<>();
        sort.add(tableClass.field("prev_height", Integer.class).asc());
        sort.add(heightField.asc());
        sort.add(tableClass.field("at_id", Long.class).asc());
        return sort;
      }
    };
  }

  private void saveATState(DSLContext ctx, brs.at.AT.ATState atState) {
    ctx.mergeInto(AT_STATE, AT_STATE.AT_ID, AT_STATE.STATE, AT_STATE.PREV_HEIGHT, AT_STATE.NEXT_HEIGHT, AT_STATE.SLEEP_BETWEEN, AT_STATE.PREV_BALANCE, AT_STATE.FREEZE_WHEN_SAME_BALANCE, AT_STATE.MIN_ACTIVATE_AMOUNT, AT_STATE.HEIGHT, AT_STATE.LATEST)
            .key(AT_STATE.AT_ID, AT_STATE.HEIGHT)
            .values(atState.getATId(), brs.at.AT.compressState(atState.getState()), atState.getPrevHeight(), atState.getNextHeight(), atState.getSleepBetween(), atState.getPrevBalance(), atState.getFreezeWhenSameBalance(), atState.getMinActivationAmount(), Burst.getBlockchain().getHeight(), true)
            .execute();
  }

  private void saveAT(DSLContext ctx, brs.at.AT at) {
    ctx.insertInto(
      AT,
      AT.ID, AT.CREATOR_ID, AT.NAME, AT.DESCRIPTION,
      AT.VERSION, AT.CSIZE, AT.DSIZE, AT.C_USER_STACK_BYTES,
      AT.C_CALL_STACK_BYTES, AT.CREATION_HEIGHT,
      AT.AP_CODE, AT.HEIGHT
    ).values(
      AtApiHelper.getLong(at.getId()), AtApiHelper.getLong(at.getCreator()), at.getName(), at.getDescription(),
      at.getVersion(), at.getcSize(), at.getdSize(), at.getcUserStackBytes(),
      at.getcCallStackBytes(), at.getCreationBlockHeight(),
      brs.at.AT.compressState(at.getApCodeBytes()), Burst.getBlockchain().getHeight()
    ).execute();
  }

  @Override
  public boolean isATAccountId(Long id) {
    return Db.useDSLContext(ctx -> {
      return ctx.fetchExists(ctx.selectOne().from(AT).where(AT.ID.eq(id)).and(AT.LATEST.isTrue()));
    });
  }

  @Override
  public List<Long> getOrderedATs() {
    return Db.useDSLContext(ctx -> {
      return ctx.selectFrom(
              AT.join(AT_STATE).on(AT.ID.eq(AT_STATE.AT_ID)).join(ACCOUNT).on(AT.ID.eq(ACCOUNT.ID))
      ).where(
              AT.LATEST.isTrue()
      ).and(
              AT_STATE.LATEST.isTrue()
      ).and(
              ACCOUNT.LATEST.isTrue()
      ).and(
              AT_STATE.NEXT_HEIGHT.lessOrEqual(Burst.getBlockchain().getHeight() + 1)
      ).and(
              ACCOUNT.BALANCE.greaterOrEqual(
                      AtConstants.getInstance().stepFee(Burst.getBlockchain().getHeight())
                              * AtConstants.getInstance().apiStepMultiplier(Burst.getBlockchain().getHeight())
              )
      ).and(
              AT_STATE.FREEZE_WHEN_SAME_BALANCE.isFalse().or(
                      "account.balance - at_state.prev_balance >= at_state.min_activate_amount"
              )
      ).orderBy(
              AT_STATE.PREV_HEIGHT.asc(), AT_STATE.NEXT_HEIGHT.asc(), AT.ID.asc()
      ).fetch().getValues(AT.ID);
    });
  }

  @Override
  public brs.at.AT getAT(Long id) {
    return Db.useDSLContext(ctx -> {
      Record record = ctx.select(AT.fields()).select(AT_STATE.fields()).from(AT.join(AT_STATE).on(AT.ID.eq(AT_STATE.AT_ID))).
              where(AT.LATEST.isTrue().
                      and(AT_STATE.LATEST.isTrue()).
                      and(AT.ID.eq(id))).fetchOne();
      if (record == null) {
        return null;
      }

      AtRecord at = record.into(AT);
      AtStateRecord atState = record.into(AT_STATE);

      return createAT(at, atState);
    });
  }

  private brs.at.AT createAT(AtRecord at, AtStateRecord atState) {
    return new AT(AtApiHelper.getByteArray(at.getId()), AtApiHelper.getByteArray(at.getCreatorId()), at.getName(), at.getDescription(), at.getVersion(),
            brs.at.AT.decompressState(atState.getState()), at.getCsize(), at.getDsize(), at.getCUserStackBytes(), at.getCCallStackBytes(), at.getCreationHeight(), atState.getSleepBetween(), atState.getNextHeight(),
            atState.getFreezeWhenSameBalance(), atState.getMinActivateAmount(), brs.at.AT.decompressState(at.getApCode()));
  }

  @Override
  public List<Long> getATsIssuedBy(Long accountId) {
    return Db.useDSLContext(ctx -> {
      return ctx.selectFrom(AT).where(AT.LATEST.isTrue()).and(AT.CREATOR_ID.eq(accountId)).orderBy(AT.CREATION_HEIGHT.desc(), AT.ID.asc()).fetch().getValues(AT.ID);
    });
  }

  @Override
  public Collection<Long> getAllATIds() {
    return Db.useDSLContext(ctx -> {
      return ctx.selectFrom(AT).where(AT.LATEST.isTrue()).fetch().getValues(AT.ID);
    });
  }

  @Override
  public BurstKey.LongKeyFactory<brs.at.AT> getAtDbKeyFactory() {
    return atDbKeyFactory;
  }

  @Override
  public VersionedEntityTable<brs.at.AT> getAtTable() {
    return atTable;
  }

  @Override
  public BurstKey.LongKeyFactory<brs.at.AT.ATState> getAtStateDbKeyFactory() {
    return atStateDbKeyFactory;
  }

  @Override
  public VersionedEntityTable<brs.at.AT.ATState> getAtStateTable() {
    return atStateTable;
  }

  @Override
  public Long findTransaction(int startHeight, int endHeight, Long atID, int numOfTx, long minAmount) {
    return Db.useDSLContext(ctx -> {
      SelectQuery<Record1<Long>> query = ctx.select(TRANSACTION.ID).from(TRANSACTION).where(
        TRANSACTION.HEIGHT.between(startHeight, endHeight - 1)
      ).and(
        TRANSACTION.RECIPIENT_ID.eq(atID)
      ).and(
        TRANSACTION.AMOUNT.greaterOrEqual(minAmount)
      ).orderBy(
        TRANSACTION.HEIGHT, TRANSACTION.ID
      ).getQuery();
      DbUtils.applyLimits(query, numOfTx, numOfTx + 1);
      Result<Record1<Long>> result = query.fetch();
      return result.isEmpty() ? 0L : result.get(0).value1();
    });
  }

  @Override
  public int findTransactionHeight(Long transactionId, int height, Long atID, long minAmount) {
    return Db.useDSLContext(ctx -> {
      try {
        Iterator<Record1<Long>> fetch = ctx.select(TRANSACTION.ID)
                .from(TRANSACTION)
                .where(TRANSACTION.HEIGHT.eq(height))
                .and(TRANSACTION.RECIPIENT_ID.eq(atID))
                .and(TRANSACTION.AMOUNT.greaterOrEqual(minAmount))
                .orderBy(TRANSACTION.HEIGHT, TRANSACTION.ID)
                .fetch()
                .iterator();
        int counter = 0;
        while (fetch.hasNext()) {
          counter++;
          long currentTransactionId = fetch.next().value1();
          if (currentTransactionId == transactionId) break;
        }
        return counter;
      } catch (DataAccessException e) {
        throw new RuntimeException(e.toString(), e);
      }
    });
  }

  class SqlATState extends brs.at.AT.ATState {
    private SqlATState(Record record) {
      super(
            record.get(AT_STATE.AT_ID),
            record.get(AT_STATE.STATE),
            record.get(AT_STATE.NEXT_HEIGHT),
            record.get(AT_STATE.SLEEP_BETWEEN),
            record.get(AT_STATE.PREV_BALANCE),
            record.get(AT_STATE.FREEZE_WHEN_SAME_BALANCE),
            record.get(AT_STATE.MIN_ACTIVATE_AMOUNT)
            );
    }
  }
}
