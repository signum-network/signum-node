package brs.db.sql;

import brs.db.BurstKey;
import brs.db.VersionedBatchEntityTable;
import brs.db.cache.DBCacheManagerImpl;
import brs.db.store.DerivedTableManager;
import org.ehcache.Cache;
import org.jooq.*;
import org.jooq.impl.TableImpl;

import java.util.*;

public abstract class VersionedBatchEntitySqlTable<T> extends VersionedEntitySqlTable<T> implements VersionedBatchEntityTable<T> {

  private final DBCacheManagerImpl dbCacheManager;
  private final Class<T> tClass;

  VersionedBatchEntitySqlTable(String table, TableImpl<?> tableClass, DbKey.Factory<T> dbKeyFactory, DerivedTableManager derivedTableManager, DBCacheManagerImpl dbCacheManager, Class<T> tClass) {
    super(table, tableClass, dbKeyFactory, derivedTableManager);
    this.dbCacheManager = dbCacheManager;
    this.tClass = tClass;
  }
  
  private void assertInTransaction() {
    if(Db.isInTransaction()) {
      throw new IllegalStateException("Cannot use in batch table transaction");
    }
  }

  private void assertNotInTransaction() {
    if(!Db.isInTransaction()) {
      throw new IllegalStateException("Not in transaction");
    }
  }

  protected abstract void bulkInsert(DSLContext ctx, Collection<T> t);

  @Override
  public boolean delete(T t) {
    assertNotInTransaction();
    DbKey dbKey = (DbKey)dbKeyFactory.newKey(t);
    getCache().remove(dbKey);
    getBatch().remove(dbKey);
    return true;
  }

  @Override
  public T get(BurstKey dbKey) {
    if (getCache().containsKey(dbKey)) {
      return getCache().get(dbKey);
    }
    else if (Db.isInTransaction() && getBatch().containsKey(dbKey)) {
      return getBatch().get(dbKey);
    }
    T item = super.get(dbKey);
    if (item != null) {
      getCache().put(dbKey, item);
    }
    return item;
  }

  @Override
  public void insert(T t) {
    assertNotInTransaction();
    BurstKey key = dbKeyFactory.newKey(t);
    getBatch().put(key, t);
    getCache().put(key, t);
  }

  @Override
  public void finish() {
    assertNotInTransaction();
    Set<BurstKey> keySet = getBatch().keySet();
    if (keySet.isEmpty()) {
      return;
    }

    Db.useDSLContext(ctx -> {
      UpdateQuery updateQuery = ctx.updateQuery(tableClass);
      updateQuery.addValue(latestField, false);
      for (String idColumn : dbKeyFactory.getPKColumns()) {
        updateQuery.addConditions(tableClass.field(idColumn, Long.class).eq(0L));
      }
      updateQuery.addConditions(latestField.isTrue());

      BatchBindStep updateBatch = ctx.batch(updateQuery);
      for (BurstKey dbKey : keySet) {
        List<Object> bindArgs = new ArrayList<>();
        bindArgs.add(false);
        for (long pkValue : dbKey.getPKValues()) {
          bindArgs.add(pkValue);
        }
        updateBatch.bind(bindArgs.toArray());
      }
      updateBatch.execute();

      bulkInsert(ctx, getBatch().values());
      getBatch().clear();
    });
  }

  @Override
  public T get(BurstKey dbKey, int height) {
    assertInTransaction();
    return super.get(dbKey, height);
  }

  @Override
  public T getBy(Condition condition) {
    assertInTransaction();
    return super.getBy(condition);
  }

  @Override
  public T getBy(Condition condition, int height) {
    assertInTransaction();
    return super.getBy(condition, height);
  }

  @Override
  public Collection<T> getManyBy(Condition condition, int from, int to) {
    assertInTransaction();
    return super.getManyBy(condition, from, to);
  }

  @Override
  public Collection<T> getManyBy(Condition condition, int from, int to, List<SortField<?>> sort) {
    assertInTransaction();
    return super.getManyBy(condition, from, to, sort);
  }

  @Override
  public Collection<T> getManyBy(Condition condition, int height, int from, int to) {
    assertInTransaction();
    return super.getManyBy(condition, height, from, to);
  }

  @Override
  public Collection<T> getManyBy(Condition condition, int height, int from, int to, List<SortField<?>> sort) {
    assertInTransaction();
    return super.getManyBy(condition, height, from, to, sort);
  }

  @Override
  public Collection<T> getManyBy(DSLContext ctx, SelectQuery<? extends Record> query, boolean cache) {
    assertInTransaction();
    return super.getManyBy(ctx, query, cache);
  }

  @Override
  public Collection<T> getAll(int from, int to) {
    assertInTransaction();
    return super.getAll(from, to);
  }

  @Override
  public Collection<T> getAll(int from, int to, List<SortField<?>> sort) {
    assertInTransaction();
    return super.getAll(from, to, sort);
  }

  @Override
  public Collection<T> getAll(int height, int from, int to) {
    assertInTransaction();
    return super.getAll(height, from, to);
  }

  @Override
  public Collection<T> getAll(int height, int from, int to, List<SortField<?>> sort) {
    assertInTransaction();
    return super.getAll(height, from, to, sort);
  }

  @Override
  public int getCount() {
    assertInTransaction();
    return super.getCount();
  }

  @Override
  public int getRowCount() {
    assertInTransaction();
    return super.getRowCount();
  }

  @Override
  public void rollback(int height) {
    super.rollback(height);
    getBatch().clear();
  }

  @Override
  public void truncate() {
    super.truncate();
    getBatch().clear();
  }

  @Override
  public Map<BurstKey, T> getBatch() {
    return Db.getBatch(table);
  }

  @Override
  public Cache<BurstKey, T> getCache() {
    return dbCacheManager.getCache(table, tClass);
  }

  @Override
  public void flushCache() {
    getCache().clear();
  }
}
