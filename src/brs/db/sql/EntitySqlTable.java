package brs.db.sql;

import brs.Burst;
import brs.db.BurstKey;
import brs.db.EntityTable;
import brs.db.store.DerivedTableManager;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class EntitySqlTable<T> extends DerivedSqlTable implements EntityTable<T> {
  final DbKey.Factory<T> dbKeyFactory;
  private final boolean multiversion;
  private final List<SortField<?>> defaultSort;

  final Field<Integer> heightField;
  final Field<Boolean> latestField;

  EntitySqlTable(String table, TableImpl<?> tableClass, BurstKey.Factory<T> dbKeyFactory, DerivedTableManager derivedTableManager) {
    this(table, tableClass, dbKeyFactory, false, derivedTableManager);
  }

  EntitySqlTable(String table, TableImpl<?> tableClass, BurstKey.Factory<T> dbKeyFactory, boolean multiversion, DerivedTableManager derivedTableManager) {
    super(table, tableClass, derivedTableManager);
    this.dbKeyFactory = (DbKey.Factory<T>) dbKeyFactory;
    this.multiversion = multiversion;
    this.defaultSort  = new ArrayList<>();
    this.heightField = tableClass.field("height", Integer.class);
    this.latestField = tableClass.field("latest", Boolean.class);
    if (multiversion) {
      for (String column : this.dbKeyFactory.getPKColumns()) {
        defaultSort.add(tableClass.field(column, Long.class).asc());
      }
    }
    defaultSort.add(heightField.desc());
  }

  private Map<BurstKey, T> getCache() {
    return Db.getCache(table);
  }

  protected abstract T load(DSLContext ctx, Record rs);

  void save(DSLContext ctx, T t) {
  }

  void save(DSLContext ctx, T[] ts) {
    for (T t : ts) {
      save(ctx, t);
    }
  }

  List<SortField<?>> defaultSort() {
    return defaultSort;
  }

  @Override
  public final void checkAvailable(int height) {
    if (multiversion && height < Burst.getBlockchainProcessor().getMinRollbackHeight()) {
      throw new IllegalArgumentException("Historical data as of height " + height + " not available, set brs.trimDerivedTables=false and re-scan");
    }
  }

  @Override
  public T get(BurstKey nxtKey) {
    DbKey dbKey = (DbKey) nxtKey;
    if (Db.isInTransaction()) {
      T t = getCache().get(dbKey);
      if (t != null) {
        return t;
      }
    }
    return Db.useDSLContext(ctx -> {
      SelectQuery<Record> query = ctx.selectQuery();
      query.addFrom(tableClass);
      query.addConditions(dbKey.getPKConditions(tableClass));
      if ( multiversion ) {
        query.addConditions(latestField.isTrue());
      }
      query.addLimit(1);

      return get(ctx, query, true);
    });
  }

  @Override
  public T get(BurstKey nxtKey, int height) {
    DbKey dbKey = (DbKey) nxtKey;
    checkAvailable(height);

    return Db.useDSLContext(ctx -> {
      SelectQuery<Record> query = ctx.selectQuery();
      query.addFrom(tableClass);
      query.addConditions(dbKey.getPKConditions(tableClass));
      query.addConditions(heightField.le(height));
      if ( multiversion ) {
        Table<?> innerTable = tableClass.as("b");
        SelectQuery<Record> innerQuery = ctx.selectQuery();
        innerQuery.addConditions(innerTable.field("height", Integer.class).gt(height));
        innerQuery.addConditions(dbKey.getPKConditions(innerTable));
        query.addConditions(
          latestField.isTrue().or(
            DSL.field(DSL.exists(innerQuery))
          )
        );
      }
      query.addOrderBy(heightField.desc());
      query.addLimit(1);

      return get(ctx, query, false);
    });
  }

  @Override
  public T getBy(Condition condition) {
    return Db.useDSLContext(ctx -> {
      SelectQuery<Record> query = ctx.selectQuery();
      query.addFrom(tableClass);
      query.addConditions(condition);
      if ( multiversion ) {
        query.addConditions(latestField.isTrue());
      }
      query.addLimit(1);

      return get(ctx, query, true);
    });
  }

  @Override
  public T getBy(Condition condition, int height) {
    checkAvailable(height);

    return Db.useDSLContext(ctx -> {
      SelectQuery<Record> query = ctx.selectQuery();
      query.addFrom(tableClass);
      query.addConditions(condition);
      query.addConditions(heightField.le(height));
      if ( multiversion ) {
        Table<?> innerTable = tableClass.as("b");
        SelectQuery<Record> innerQuery = ctx.selectQuery();
        innerQuery.addConditions(innerTable.field("height", Integer.class).gt(height));
        dbKeyFactory.applySelfJoin(innerQuery, innerTable, tableClass);
        query.addConditions(
          latestField.isTrue().or(
            DSL.field(DSL.exists(innerQuery))
          )
        );
      }
      query.addOrderBy(heightField.desc());
      query.addLimit(1);

      return get(ctx, query, false);
    });
  }

  private T get(DSLContext ctx, SelectQuery<Record> query, boolean cache) {
    final boolean doCache = cache && Db.isInTransaction();
    Record record = query.fetchOne();
    if (record == null) return null;
    T t = null;
    DbKey dbKey = null;
    if (doCache) {
      dbKey = (DbKey) dbKeyFactory.newKey(record);
      t = getCache().get(dbKey);
    }
    if (t == null) {
      t = load(ctx, record);
      if (doCache) {
        Db.getCache(table).put(dbKey, t);
      }
    }
    return t;
  }

  @Override
  public Collection<T> getManyBy(Condition condition, int from, int to) {
    return getManyBy(condition, from, to, defaultSort());
  }

  @Override
  public Collection<T> getManyBy(Condition condition, int from, int to, List<SortField<?>> sort) {
    return Db.useDSLContext(ctx -> {
      SelectQuery<Record> query = ctx.selectQuery();
      query.addFrom(tableClass);
      query.addConditions(condition);
      query.addOrderBy(sort);
      if (multiversion) {
        query.addConditions(latestField.isTrue());
      }
      DbUtils.applyLimits(query, from, to);
      return getManyBy(ctx, query, true);
    });
  }

  @Override
  public Collection<T> getManyBy(Condition condition, int height, int from, int to) {
    return getManyBy(condition, height, from, to, defaultSort());
  }

  @Override
  public Collection<T> getManyBy(Condition condition, int height, int from, int to, List<SortField<?>> sort) {
    checkAvailable(height);
    return Db.useDSLContext(ctx -> {
      SelectQuery<Record> query = ctx.selectQuery();
      query.addFrom(tableClass);
      query.addConditions(condition);
      query.addConditions(heightField.le(height));
      if (multiversion) {
        Table<?> innerTableB = tableClass.as("b");
        SelectQuery<Record> innerQueryB = ctx.selectQuery();
        innerQueryB.addConditions(innerTableB.field("height", Integer.class).gt(height));
        dbKeyFactory.applySelfJoin(innerQueryB, innerTableB, tableClass);

        Table<?> innerTableC = tableClass.as("c");
        SelectQuery<Record> innerQueryC = ctx.selectQuery();
        innerQueryC.addConditions(
                innerTableC.field("height", Integer.class).le(height).and(
                        innerTableC.field("height", Integer.class).gt(heightField)
                )
        );
        dbKeyFactory.applySelfJoin(innerQueryC, innerTableC, tableClass);

        query.addConditions(
                latestField.isTrue().or(
                        DSL.field(
                                DSL.exists(innerQueryB).and(DSL.notExists(innerQueryC))
                        )
                )
        );
      }
      query.addOrderBy(sort);

      DbUtils.applyLimits(query, from, to);
      return getManyBy(ctx, query, true);
    });
  }

  @Override
  public Collection<T> getManyBy(DSLContext ctx, SelectQuery<? extends Record> query, boolean cache) {
    final boolean doCache = cache && Db.isInTransaction();
    return query.fetch(record -> {
      T t = null;
      DbKey dbKey = null;
      if (doCache) {
        dbKey = (DbKey) dbKeyFactory.newKey(record);
        t = getCache().get(dbKey);
      }
      if (t == null) {
        t = load(ctx, record);
        if (doCache) {
          Db.getCache(table).put(dbKey, t);
        }
      }
      return t;
    });
  }

  @Override
  public Collection<T> getAll(int from, int to) {
    return getAll(from, to, defaultSort());
  }

  @Override
  public Collection<T> getAll(int from, int to, List<SortField<?>> sort) {
    return Db.useDSLContext(ctx -> {
      SelectQuery<Record> query = ctx.selectQuery();
      query.addFrom(tableClass);
      if (multiversion) {
        query.addConditions(latestField.isTrue());
      }
      query.addOrderBy(sort);
      DbUtils.applyLimits(query, from, to);
      return getManyBy(ctx, query, true);
    });
  }

  @Override
  public Collection<T> getAll(int height, int from, int to) {
    return getAll(height, from, to, defaultSort());
  }

  @Override
  public Collection<T> getAll(int height, int from, int to, List<SortField<?>> sort) {
    checkAvailable(height);
    return Db.useDSLContext(ctx -> {
      SelectQuery<Record> query = ctx.selectQuery();
      query.addFrom(tableClass);
      query.addConditions(heightField.le(height));
      if (multiversion) {
        Table<?> innerTableB = tableClass.as("b");
        SelectQuery<Record> innerQueryB = ctx.selectQuery();
        innerQueryB.addConditions(innerTableB.field("height", Integer.class).gt(height));
        dbKeyFactory.applySelfJoin(innerQueryB, innerTableB, tableClass);

        Table<?> innerTableC = tableClass.as("c");
        SelectQuery<Record> innerQueryC = ctx.selectQuery();
        innerQueryC.addConditions(
                innerTableC.field("height", Integer.class).le(height).and(
                        innerTableC.field("height", Integer.class).gt(heightField)
                )
        );
        dbKeyFactory.applySelfJoin(innerQueryC, innerTableC, tableClass);

        query.addConditions(
                latestField.isTrue().or(
                        DSL.field(
                                DSL.exists(innerQueryB).and(DSL.notExists(innerQueryC))
                        )
                )
        );
      }
      query.addOrderBy(sort);
      query.addLimit(from, to);
      return getManyBy(ctx, query, true);
    });
  }

  @Override
  public int getCount() {
    return Db.useDSLContext(ctx -> {
      SelectJoinStep<?> r = ctx.selectCount().from(tableClass);
      return (multiversion ? r.where(latestField.isTrue()) : r).fetchOne(0, int.class);
    });
  }

  @Override
  public int getRowCount() {
    return Db.useDSLContext(ctx -> {
      return ctx.selectCount().from(tableClass).fetchOne(0, int.class);
    });
  }

  @Override
  public void insert(T t) {
    if (!Db.isInTransaction()) {
      throw new IllegalStateException("Not in transaction");
    }
    DbKey dbKey = (DbKey) dbKeyFactory.newKey(t);
    T cachedT = getCache().get(dbKey);
    if (cachedT == null) {
      Db.getCache(table).put(dbKey, t);
    } else if (t != cachedT) { // not a bug
      throw new IllegalStateException("Different instance found in Db cache, perhaps trying to save an object "
                                      + "that was read outside the current transaction");
    }
    Db.useDSLContext(ctx -> {
      if (multiversion) {
        UpdateQuery query = ctx.updateQuery(tableClass);
        query.addValue(
          latestField,
          false
        );
        query.addConditions(dbKey.getPKConditions(tableClass));
        query.addConditions(latestField.isTrue());
        query.execute();
      }
      save(ctx, t);
    });
  }

  @Override
  public void rollback(int height) {
    super.rollback(height);
    Db.getCache(table).clear();
  }

  @Override
  public void truncate() {
    super.truncate();
    Db.getCache(table).clear();
  }
}
