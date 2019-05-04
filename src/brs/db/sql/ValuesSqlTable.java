package brs.db.sql;

import brs.db.BurstKey;
import brs.db.ValuesTable;
import brs.db.store.DerivedTableManager;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectQuery;
import org.jooq.UpdateQuery;
import org.jooq.impl.TableImpl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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

  protected abstract V load(DSLContext ctx, ResultSet rs) throws SQLException;

  protected abstract void save(DSLContext ctx, T t, V v);

  @SuppressWarnings("unchecked")
  @Override
  public final List<V> get(BurstKey nxtKey) {
    DbKey dbKey = (DbKey) nxtKey;
    List<V> values;
    if (Db.isInTransaction()) {
      values = (List<V>) Db.getCache(table).get(dbKey);
      if (values != null) {
        return values;
      }
    }
    DSLContext ctx = Db.getDSLContext();
    SelectQuery<Record> query = ctx.selectQuery();
    query.addFrom(tableClass);
    query.addConditions(dbKey.getPKConditions(tableClass));
    if ( multiversion ) {
      query.addConditions(latestField.isTrue());
    }
    query.addOrderBy(tableClass.field("db_id").desc());
    values = get(ctx, query.fetchResultSet());
    if (Db.isInTransaction()) {
      Db.getCache(table).put(dbKey, values);
    }
    return values;
  }

  private List<V> get(DSLContext ctx, ResultSet rs) {
    try {
      List<V> result = new ArrayList<>();
      while (rs.next()) {
        result.add(load(ctx, rs));
      }
      return result;
    } catch (SQLException e) {
      throw new RuntimeException(e.toString(), e);
    }
  }

  @Override
  public final void insert(T t, List<V> values) {
    if (!Db.isInTransaction()) {
      throw new IllegalStateException("Not in transaction");
    }
    DbKey dbKey = (DbKey)dbKeyFactory.newKey(t);
    Db.getCache(table).put(dbKey, values);
    try ( DSLContext ctx = Db.getDSLContext() ) {
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
      for (V v : values) {
        save(ctx, t, v);
      }
    }
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
