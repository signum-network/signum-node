package brs.db.sql;

import brs.Burst;
import brs.db.BurstKey;
import brs.db.VersionedEntityTable;
import brs.db.store.DerivedTableManager;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;

import java.util.ArrayList;
import java.util.List;

public abstract class VersionedEntitySqlTable<T> extends EntitySqlTable<T> implements VersionedEntityTable<T> {

  VersionedEntitySqlTable(String table, TableImpl<?> tableClass, BurstKey.Factory<T> dbKeyFactory, DerivedTableManager derivedTableManager) {
    super(table, tableClass, dbKeyFactory, true, derivedTableManager);
  }

  @Override
  public void rollback(int height) {
    rollback(table, tableClass, heightField, latestField, height, dbKeyFactory);
  }

  static void rollback(final String table, final TableImpl<?> tableClass, Field<Integer> heightField, Field<Boolean> latestField, final int height, final DbKey.Factory<?> dbKeyFactory) {
    if (!Db.isInTransaction()) {
      throw new IllegalStateException("Not in transaction");
    }

    Db.useDSLContext(ctx -> {
      // get dbKey's for entries whose stuff newer than height would be deleted, to allow fixing
      // their latest flag of the "potential" remaining newest entry
      SelectQuery<Record> selectForDeleteQuery = ctx.selectQuery();
      selectForDeleteQuery.addFrom(tableClass);
      selectForDeleteQuery.addConditions(heightField.gt(height));
      for (String column : dbKeyFactory.getPKColumns()) {
        selectForDeleteQuery.addSelect(tableClass.field(column, Long.class));
      }
      selectForDeleteQuery.setDistinct(true);
      List<DbKey> dbKeys = selectForDeleteQuery.fetch(r -> (DbKey) dbKeyFactory.newKey(r));

      // delete all entries > height
      DeleteQuery deleteQuery = ctx.deleteQuery(tableClass);
      deleteQuery.addConditions(heightField.gt(height));
      deleteQuery.execute();

      // update latest flags for remaining entries, if there any remaining (per deleted dbKey)
      for (DbKey dbKey : dbKeys) {
        SelectQuery<Record> selectMaxHeightQuery = ctx.selectQuery();
        selectMaxHeightQuery.addFrom(tableClass);
        selectMaxHeightQuery.addConditions(dbKey.getPKConditions(tableClass));
        selectMaxHeightQuery.addSelect(DSL.max(heightField));
        Integer maxHeight = selectMaxHeightQuery.fetchOne().get(DSL.max(heightField));

        if (maxHeight != null) {
          UpdateQuery setLatestQuery = ctx.updateQuery(tableClass);
          setLatestQuery.addConditions(dbKey.getPKConditions(tableClass));
          setLatestQuery.addConditions(heightField.eq(maxHeight));
          setLatestQuery.addValue(latestField, true);
          setLatestQuery.execute();
        }
      }
    });
    Db.getCache(table).clear();
  }

  @Override
  public final void trim(int height) {
    trim(tableClass, heightField, height, dbKeyFactory);
  }

  static void trim(final TableImpl<?> tableClass, Field<Integer> heightField, final int height, final DbKey.Factory dbKeyFactory) {
    if (!Db.isInTransaction()) {
      throw new IllegalStateException("Not in transaction");
    }

    // "accounts" is just an example to make it easier to understand what the code does
    // select all accounts with multiple entries where height < trimToHeight[current height - 1440]
    Db.useDSLContext(ctx -> {
      SelectQuery<Record> selectMaxHeightQuery = ctx.selectQuery();
      selectMaxHeightQuery.addFrom(tableClass);
      selectMaxHeightQuery.addSelect(DSL.max(heightField).as("max_height"));
      for (String column : dbKeyFactory.getPKColumns()) {
        Field pkField = tableClass.field(column, Long.class);
        selectMaxHeightQuery.addSelect(pkField);
        selectMaxHeightQuery.addGroupBy(pkField);
      }
      selectMaxHeightQuery.addConditions(heightField.lt(height));
      selectMaxHeightQuery.addHaving(DSL.countDistinct(heightField).gt(1));

      // delete all fetched accounts, except if it's height is the max height we figured out
      DeleteQuery deleteLowerHeightQuery = ctx.deleteQuery(tableClass);
      deleteLowerHeightQuery.addConditions(heightField.lt((Integer) null));
      for (String column : dbKeyFactory.getPKColumns()) {
        Field<Long> pkField = tableClass.field(column, Long.class);
        deleteLowerHeightQuery.addConditions(pkField.eq((Long) null));
      }
      BatchBindStep deleteBatch = ctx.batch(deleteLowerHeightQuery);

      for (Record record : selectMaxHeightQuery.fetch()) {
        DbKey dbKey = (DbKey) dbKeyFactory.newKey(record);
        int maxHeight = record.get("max_height", Integer.class);
        List<Long> bindValues = new ArrayList<>();
        bindValues.add((long) maxHeight);
        for (Long pkValue : dbKey.getPKValues()) {
          bindValues.add(pkValue);
        }
        deleteBatch.bind(bindValues.toArray());
      }
      if (deleteBatch.size() > 0) {
        deleteBatch.execute();
      }
    });
  }

  @Override
  public boolean delete(T t) {
    if (t == null) {
      return false;
    }
    if (!Db.isInTransaction()) {
      throw new IllegalStateException("Not in transaction");
    }
    DbKey dbKey = (DbKey) dbKeyFactory.newKey(t);
    return Db.useDSLContext(ctx -> {
      try {
        SelectQuery<Record> countQuery = ctx.selectQuery();
        countQuery.addFrom(tableClass);
        countQuery.addConditions(dbKey.getPKConditions(tableClass));
        countQuery.addConditions(heightField.lt(Burst.getBlockchain().getHeight()));
        if (ctx.fetchCount(countQuery) > 0) {
          UpdateQuery updateQuery = ctx.updateQuery(tableClass);
          updateQuery.addValue(
                  latestField,
                  false
          );
          updateQuery.addConditions(dbKey.getPKConditions(tableClass));
          updateQuery.addConditions(latestField.isTrue());

          updateQuery.execute();
          save(ctx, t);
          // delete after the save
          updateQuery.execute();

          return true;
        } else {
          DeleteQuery deleteQuery = ctx.deleteQuery(tableClass);
          deleteQuery.addConditions(dbKey.getPKConditions(tableClass));
          return deleteQuery.execute() > 0;
        }
      } finally {
        Db.getCache(table).remove(dbKey);
      }
    });
  }
}
