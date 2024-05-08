package brs.db.sql;

import brs.*;
import brs.Transaction;
import brs.at.*;
import brs.at.AT.AtMapEntry;
import brs.db.SignumKey;
import brs.db.VersionedEntityTable;
import brs.db.store.ATStore;
import brs.db.store.DerivedTableManager;
import brs.schema.tables.records.AtRecord;
import brs.schema.tables.records.AtStateRecord;

import brs.util.CollectionWithIndex;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static brs.schema.Tables.*;

public class SqlATStore implements ATStore {
  private static final Logger logger = LoggerFactory.getLogger(SqlATStore.class);

  private final SignumKey.LongKeyFactory<brs.at.AT> atDbKeyFactory = new DbKey.LongKeyFactory<brs.at.AT>(AT.ID) {
    @Override
    public SignumKey newKey(brs.at.AT at) {
      return at.dbKey;
    }
  };
  private final VersionedEntityTable<brs.at.AT> atTable;

  private final SignumKey.LongKeyFactory<brs.at.AT.ATState> atStateDbKeyFactory = new DbKey.LongKeyFactory<brs.at.AT.ATState>(AT_STATE.AT_ID) {
    @Override
    public SignumKey newKey(brs.at.AT.ATState atState) {
      return atState.dbKey;
    }
  };

  private final VersionedEntityTable<brs.at.AT.ATState> atStateTable;


  private final DbKey.LinkKey3Factory<brs.at.AT.AtMapEntry> atMapKeyFactory = new DbKey.LinkKey3Factory<brs.at.AT.AtMapEntry>("at_id", "key1", "key2") {
    @Override
    public SignumKey newKey(brs.at.AT.AtMapEntry atDb) {
      return newKey(atDb.getAtId(), atDb.getKey1(), atDb.getKey2());
    }
  };

  private final VersionedEntityTable<brs.at.AT.AtMapEntry> atMapTable;


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

    atMapTable = new VersionedEntitySqlTable<brs.at.AT.AtMapEntry>("at_map", brs.schema.Tables.AT_MAP, atMapKeyFactory, derivedTableManager) {
      @Override
      protected brs.at.AT.AtMapEntry load(DSLContext ctx, Record rs) {
        return new SqlAtMapEntry(rs);
      }

      @Override
      protected void save(DSLContext ctx, brs.at.AT.AtMapEntry atDbEntry) {
        saveATMapEntry(ctx, atDbEntry);
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
    ctx.insertInto(
        AT_STATE, AT_STATE.AT_ID, AT_STATE.STATE, AT_STATE.PREV_HEIGHT, AT_STATE.NEXT_HEIGHT, AT_STATE.SLEEP_BETWEEN, AT_STATE.PREV_BALANCE, AT_STATE.FREEZE_WHEN_SAME_BALANCE, AT_STATE.MIN_ACTIVATE_AMOUNT, AT_STATE.HEIGHT, AT_STATE.LATEST)
      .values(atState.getATId(), brs.at.AT.compressState(atState.getState()), atState.getPrevHeight(), atState.getNextHeight(), atState.getSleepBetween(), atState.getPrevBalance(), atState.getFreezeWhenSameBalance(), atState.getMinActivationAmount(), Signum.getBlockchain().getHeight(), true)
      .execute();
  }

  private void saveATMapEntry(DSLContext ctx, brs.at.AT.AtMapEntry atEntry) {
    ctx.insertInto(AT_MAP, AT_MAP.AT_ID, AT_MAP.KEY1, AT_MAP.KEY2, AT_MAP.VALUE, AT_STATE.HEIGHT, AT_STATE.LATEST)
      .values(atEntry.getAtId(), atEntry.getKey1(), atEntry.getKey2(), atEntry.getValue(), Signum.getBlockchain().getHeight(), true)
      .execute();
  }

  private void saveAT(DSLContext ctx, brs.at.AT at) {
    ctx.insertInto(
      AT,
      AT.ID, AT.CREATOR_ID, AT.NAME, AT.DESCRIPTION,
      AT.VERSION, AT.CSIZE, AT.DSIZE, AT.C_USER_STACK_BYTES,
      AT.C_CALL_STACK_BYTES, AT.CREATION_HEIGHT,
      AT.AP_CODE, AT.HEIGHT, AT.AP_CODE_HASH_ID
    ).values(
      AtApiHelper.getLong(at.getId()), AtApiHelper.getLong(at.getCreator()), at.getName(), at.getDescription(),
      at.getVersion(), at.getcSize(), at.getdSize(), at.getcUserStackBytes(),
      at.getcCallStackBytes(), at.getCreationBlockHeight(),
      brs.at.AT.compressState(at.getApCodeBytes()), Signum.getBlockchain().getHeight(), at.getApCodeHashId()
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
      AtConstants atConstants = AtConstants.getInstance();
      return ctx.selectFrom(
        AT.join(AT_STATE).on(AT.ID.eq(AT_STATE.AT_ID)).join(ACCOUNT_BALANCE).on(AT.ID.eq(ACCOUNT_BALANCE.ID))
      ).where(
        AT.LATEST.isTrue()
      ).and(
        AT_STATE.LATEST.isTrue()
      ).and(
        ACCOUNT_BALANCE.LATEST.isTrue()
      ).and(
        AT_STATE.NEXT_HEIGHT.lessOrEqual(Signum.getBlockchain().getHeight() + 1)
      ).and(
        ACCOUNT_BALANCE.BALANCE.greaterOrEqual(
          atConstants.stepFee(atConstants.atVersion(Signum.getBlockchain().getHeight()))
            * atConstants.apiStepMultiplier(atConstants.atVersion(Signum.getBlockchain().getHeight()))
        )
      ).and(
        AT_STATE.FREEZE_WHEN_SAME_BALANCE.isFalse().or(
          "account_balance.balance - at_state.prev_balance >= at_state.min_activate_amount"
        )
      ).orderBy(
        AT_STATE.PREV_HEIGHT.asc(), AT_STATE.NEXT_HEIGHT.asc(), AT.ID.asc()
      ).fetch().getValues(AT.ID);
    });
  }

  @Override
  public brs.at.AT getAT(Long id) {
    return getAT(id, -1);
  }

  @Override
  public brs.at.AT getAT(Long id, int height) {
    return Db.useDSLContext(ctx -> {
      SelectJoinStep<Record> select = ctx.select(AT.fields()).select(AT_STATE.fields()).from(AT.join(AT_STATE)
        .on(AT.ID.eq(AT_STATE.AT_ID)));
      ResultQuery<Record> where = null;
      if (height > 0) {
        where = select.where(AT_STATE.HEIGHT.le(height)).and(AT.ID.eq(id)).orderBy(AT_STATE.HEIGHT.desc()).maxRows(1);
      } else {
        where = select.where(AT.LATEST.isTrue()).and(AT_STATE.LATEST.isTrue()).and(AT.ID.eq(id));
      }
      Record record = where.fetchOne();
      if (record == null) {
        return null;
      }

      AtRecord at = record.into(AT);
      AtStateRecord atState = record.into(AT_STATE);

      return createAT(at, atState, height);
    });
  }

  @Override
  public Collection<brs.at.AT> getATs(Collection<Long> ids) {
    return Db.useDSLContext(ctx -> {
      Result<Record> result = ctx.select(AT.fields()).select(AT_STATE.fields())
        .from(AT.join(AT_STATE).on(AT.ID.eq(AT_STATE.AT_ID)))
        .where(AT.LATEST.isTrue()).and(AT_STATE.LATEST.isTrue()).and(AT.ID.in(ids))
        .fetch();

      return result.stream().map(record -> createAT(record.into(AT), record.into(AT_STATE), -1)).collect(Collectors.toList());
    });
  }

  @Override
  public AtMapEntry getMapValueEntry(long atId, long key1, long key2) {
    return this.atMapTable.get(this.atMapKeyFactory.newKey(atId, key1, key2));
  }

  @Override
  public long getMapValue(long atId, long key1, long key2) {
    AtMapEntry entry = getMapValueEntry(atId, key1, key2);
    if (entry == null)
      return 0;
    return entry.getValue();
  }

  @Override
  public CollectionWithIndex<AtMapEntry> getMapValues(long atId, long key1, Long value, int from, int to) {
    Result<Record> result = Db.useDSLContext(ctx -> {
      SelectConditionStep<Record> request = ctx.select(AT_MAP.fields()).from(AT_MAP).where(AT_MAP.LATEST.isTrue()).and(AT_MAP.AT_ID.eq(atId))
        .and(AT_MAP.KEY1.eq(key1));
      if (value != null) {
        request = request.and(AT_MAP.VALUE.eq(value));
      }
      SelectQuery<Record> query = request.orderBy(AT_MAP.HEIGHT.desc()).getQuery();
      DbUtils.applyLimits(query, from, to);
      return query.fetch();
    });

    ArrayList<brs.at.AT.AtMapEntry> list = new ArrayList<>();
    for (Record r : result) {
      list.add(new brs.at.AT.AtMapEntry(atId, key1, r.get(AT_MAP.KEY2), r.get(AT_MAP.VALUE)));
    }

    return new CollectionWithIndex<>(list, from, to);
  }

  private brs.at.AT createAT(AtRecord at, AtStateRecord atState, int height) {
    byte[] code = brs.at.AT.decompressState(at.getApCode());
    long codeHashId = at.getApCodeHashId();
    int codeSize = at.getCsize();
    if (code == null) {
      // Check the creation transaction for the reference code
      Transaction atCreationTransaction = Signum.getBlockchain().getTransaction(at.getId());
      Transaction transaction = Signum.getBlockchain().getTransactionByFullHash(atCreationTransaction.getReferencedTransactionFullHash());
      if (transaction != null && transaction.getAttachment() instanceof Attachment.AutomatedTransactionsCreation) {
        Attachment.AutomatedTransactionsCreation atCreationAttachment = (Attachment.AutomatedTransactionsCreation) transaction.getAttachment();
        AtMachineState atCreation = new AtMachineState(null, null, atCreationAttachment.getCreationBytes(), 0);
        code = atCreation.getApCodeBytes();
        codeSize = atCreation.getcSize();
        codeHashId = atCreation.getApCodeHashId();
      }
    }
    return new AT(AtApiHelper.getByteArray(at.getId()), AtApiHelper.getByteArray(at.getCreatorId()), at.getName(), at.getDescription(), at.getVersion(),
      height,
      brs.at.AT.decompressState(atState.getState()), codeSize, at.getDsize(), at.getCUserStackBytes(), at.getCCallStackBytes(), at.getCreationHeight(), atState.getSleepBetween(), atState.getNextHeight(),
      atState.getFreezeWhenSameBalance(), atState.getMinActivateAmount(), code, codeHashId);
  }

  @Override
  public List<Long> getATsIssuedBy(Long accountId, Long codeHashId, int from, int to) {
    return Db.useDSLContext(ctx -> {
      SelectConditionStep<Record1<Long>> request = ctx.select(AT.ID).from(AT).where(AT.LATEST.isTrue());
      if (accountId != null) {
        request = request.and(AT.CREATOR_ID.eq(accountId));
      }
      if (codeHashId != null) {
        request = request.and(AT.AP_CODE_HASH_ID.eq(codeHashId));
      }
      SelectQuery<Record1<Long>> query = request.orderBy(AT.CREATION_HEIGHT.desc(), AT.ID.asc()).getQuery();
      DbUtils.applyLimits(query, from, to);

      return query.fetch().getValues(AT.ID);
    });
  }

  @Override
  public Collection<Long> getAllATIds(Long codeHashId) {
    return Db.useDSLContext(ctx -> {
      SelectConditionStep<AtRecord> request = ctx.selectFrom(AT).where(AT.LATEST.isTrue());
      if (codeHashId != null)
        request = request.and(AT.AP_CODE_HASH_ID.eq(codeHashId));
      return request.fetch().getValues(AT.ID);
    });
  }

  @Override
  public SignumKey.LongKeyFactory<brs.at.AT> getAtDbKeyFactory() {
    return atDbKeyFactory;
  }

  @Override
  public VersionedEntityTable<brs.at.AT> getAtTable() {
    return atTable;
  }

  @Override
  public VersionedEntityTable<brs.at.AT.AtMapEntry> getAtMapTable() {
    return atMapTable;
  }

  @Override
  public SignumKey.LongKeyFactory<brs.at.AT.ATState> getAtStateDbKeyFactory() {
    return atStateDbKeyFactory;
  }

  @Override
  public VersionedEntityTable<brs.at.AT.ATState> getAtStateTable() {
    return atStateTable;
  }


  @Override
  public Long findTransaction(int startHeight, int endHeight, Long atID, int numOfTx, long minActivationAmount) {
    return Db.useDSLContext(ctx -> {
      long startTime = System.nanoTime();

      SelectQuery<Record1<Long>> query = ctx.select(TRANSACTION.ID)
        .from(TRANSACTION)
        .where(TRANSACTION.HEIGHT.between(startHeight, endHeight - 1))
        .and(TRANSACTION.RECIPIENT_ID.eq(atID))
        .and(TRANSACTION.AMOUNT.greaterOrEqual(minActivationAmount))
        .orderBy(TRANSACTION.HEIGHT, TRANSACTION.ID)
        .getQuery();
      DbUtils.applyLimits(query, numOfTx, numOfTx + 1);
      Result<Record1<Long>> result = query.fetch();

      long executionTime = (System.nanoTime() - startTime) / 1000000;
      logger.debug("Find Duration: {} milliseconds", executionTime);
      return result.isEmpty() ? 0L : result.get(0).value1();
    });
  }

  @Override
  public int findTransactionHeight(Long transactionId, int height, Long atID, long minActivationAmount) {
    return Db.useDSLContext(ctx -> {
      long startTime = System.nanoTime();
      try {
        List<Long> transactionIds = ctx.select(TRANSACTION.ID)
          .from(TRANSACTION)
          .where(TRANSACTION.HEIGHT.eq(height))
          .and(TRANSACTION.RECIPIENT_ID.eq(atID))
          .and(TRANSACTION.AMOUNT.greaterOrEqual(minActivationAmount))
          .orderBy(TRANSACTION.HEIGHT, TRANSACTION.ID)
          .fetchInto(Long.class);

        int counter = 0;
        for (Long currentTransactionId : transactionIds) {
          counter++;
          if (currentTransactionId.equals(transactionId)) {
            break;
          }
        }
        long executionTime = (System.nanoTime() - startTime) / 1000000;
        logger.debug("Find Duration: {} milliseconds", executionTime);
        return counter;
      } catch (DataAccessException e) {
        throw new RuntimeException(e.toString(), e);
      }
    });
  }


//  Previous version
//  @Override
//  public int findTransactionHeight(Long transactionId, int height, Long atID, long minActivationAmount) {
//    return Db.useDSLContext(ctx -> {
//      try {
//        Iterator<Record1<Long>> fetch = ctx.select(TRANSACTION.ID)
//                .from(TRANSACTION)
//                .where(TRANSACTION.HEIGHT.eq(height))
//                .and(TRANSACTION.RECIPIENT_ID.eq(atID))
//                .and(TRANSACTION.AMOUNT.greaterOrEqual(minActivationAmount))
//                .orderBy(TRANSACTION.HEIGHT, TRANSACTION.ID)
//                .fetch()
//                .iterator();
//        int counter = 0;
//        while (fetch.hasNext()) {
//          counter++;
//          long currentTransactionId = fetch.next().value1();
//          if (currentTransactionId == transactionId) break;
//        }
//        return counter;
//      } catch (DataAccessException e) {
//        throw new RuntimeException(e.toString(), e);
//      }
//    });
//  }

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

  class SqlAtMapEntry extends brs.at.AT.AtMapEntry {
    public SqlAtMapEntry(Record record) {
      super(record.get(AT_MAP.AT_ID), record.get(AT_MAP.KEY1), record.get(AT_MAP.KEY2), record.get(AT_MAP.VALUE));
    }
  }

}
