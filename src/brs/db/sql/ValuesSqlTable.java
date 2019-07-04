package brs.db.sql;

import brs.db.BurstKey;
import brs.db.ValuesTable;
import brs.db.store.DerivedTableManager;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;

import java.util.List;

public abstract class ValuesSqlTable<T,V> extends DerivedSqlTable implements ValuesTable<T, V> {

  private final boolean multiversion;
  final DbKey.Factory<T> dbKeyFactory;

  protected ValuesSqlTable(String table, TableImpl<?> tableClass, DbKey.Factory<T> dbKeyFactory, DerivedTableManager derivedTableManager) {
    this(table, tableClass, dbKeyFactory, false, derivedTableManager);
  }

  ValuesSqlTable(String table, TableImpl<?> tableClass, DbKey.Factory<T> dbKeyFactory, boolean multiversion, DerivedTableManager derivedTableManager) {
    super(table, tableClass, derivedTableManager);
    this.dbKeyFactory = dbKeyFactory;
    this.multiversion = multiversion;
  }

  protected abstract V load(DSLContext ctx, Record record);

  protected abstract void save(DSLContext ctx, T t, V v);

  @SuppressWarnings("unchecked")
  @Override
  public final List<V> get(BurstKey nxtKey) {
    return Db.useDSLContext(ctx -> {
      DbKey dbKey = (DbKey) nxtKey;
      List<V> values;
      if (Db.isInTransaction()) {
        values = (List<V>) Db.getCache(table).get(dbKey);
        if (values != null) {
          return values;
        }
      }
      values = ctx.selectFrom(tableClass)
              .where(dbKey.getPKConditions(tableClass))
              .and(multiversion ? latestField.isTrue() : DSL.noCondition())
              .orderBy(tableClass.field("db_id").desc())
              .fetch(record -> load(ctx, record));
      if (Db.isInTransaction()) {
        Db.getCache(table).put(dbKey, values);
      }
      return values;
    });
  }

  @Override
  public final void insert(T t, List<V> values) {
    if (!Db.isInTransaction()) {
      throw new IllegalStateException("Not in transaction");
    }
    Db.useDSLContext(ctx -> {
      DbKey dbKey = (DbKey) dbKeyFactory.newKey(t);
      Db.getCache(table).put(dbKey, values);
      if (multiversion) {
        ctx.update(tableClass)
                .set(latestField, false)
                .where(dbKey.getPKConditions(tableClass))
                .and(latestField.isTrue())
                .execute();
      }
      for (V v : values) {
        save(ctx, t, v);
      }
    });
  }

  @Override
  public void rollback(int height) {
    super.rollback(height);
    Db.getCache(table).clear();
  }

  @Override
  public final void truncate() {
    super.truncate();
    Db.getCache(table).clear();
  }
}
