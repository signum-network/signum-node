package brs.db.sql;

import brs.Signum;
import brs.db.SignumKey;
import brs.db.VersionedBatchEntityTable;
import brs.db.cache.DBCacheManagerImpl;
import brs.db.store.DerivedTableManager;
import brs.props.Props;
import org.ehcache.Cache;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.TableImpl;

import java.util.*;
import java.util.stream.Collectors;

public abstract class VersionedBatchEntitySqlTable<T> extends VersionedEntitySqlTable<T> implements VersionedBatchEntityTable<T> {

  private final DBCacheManagerImpl dbCacheManager;
  private final Class<T> tClass;
  private static final Integer UpdateMaxBatchSize = Signum.getPropertyService().getInt(Props.DB_INSERT_BATCH_MAX_SIZE);

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
  public T get(SignumKey dbKey) {
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
    SignumKey key = dbKeyFactory.newKey(t);
    getBatch().put(key, t);
    getCache().put(key, t);
  }

  @Override
  public void finish() {
    assertNotInTransaction();
    Set<SignumKey> keySet = getBatch().keySet();
    if (keySet.isEmpty()) {
      return;
    }

    Db.useDSLContext(ctx -> {
      Field<Long> idField = tableClass.field(dbKeyFactory.getPKColumns()[0], Long.class);
      List<Long> ids = keySet.stream()
        .map(k -> k.getPKValues()[0])
        .collect(Collectors.toList());

      for (int from = 0; from < ids.size(); from += UpdateMaxBatchSize) {
        int to = Math.min(from + UpdateMaxBatchSize, ids.size());
        ctx.update(tableClass)
          .set(latestField, false)
          .where(latestField.isTrue())
          .and(idField.in(ids.subList(from, to)))
          .execute();
      }

      bulkInsert(ctx, getBatch().values());
      getBatch().clear();
    });
  }

  @Override
  public T get(SignumKey dbKey, int height) {
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
  public Map<SignumKey, T> getBatch() {
    return Db.getBatch(table);
  }

  @Override
  public Cache<SignumKey, T> getCache() {
    return dbCacheManager.getCache(table, tClass);
  }

  @Override
  public void flushCache() {
    getCache().clear();
  }
}
