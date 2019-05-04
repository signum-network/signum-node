package brs.db.sql;

import brs.db.BurstIterator;
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

  protected abstract void bulkInsert(DSLContext ctx, Collection<T> t);

  @Override
  public boolean delete(T t) {
    if(!Db.isInTransaction()) {
      throw new IllegalStateException("Not in transaction");
    }
    DbKey dbKey = (DbKey)dbKeyFactory.newKey(t);
    getCache().remove(dbKey);
    getBatch().remove(dbKey, null);
    getBatch().remove(dbKey);

    return true;
  }

  @Override
  public T get(BurstKey dbKey) {
    if (getCache().containsKey(dbKey)) {
      return getCache().get(dbKey);
    }
    else if(Db.isInTransaction()) {
      if(getBatch().containsKey(dbKey)) {
        return getBatch().get(dbKey);
      }
    }
    T item = super.get(dbKey);
    if ( item != null ) {
      getCache().put(dbKey, item);
    }
    return item;
  }

  @Override
  public void insert(T t) {
    if(!Db.isInTransaction()) {
      throw new IllegalStateException("Not in transaction");
    }
    DbKey dbKey = (DbKey)dbKeyFactory.newKey(t);
    getBatch().put(dbKey, t);
    getBatch().put(dbKey, t);
    getCache().put(dbKey, t);
  }

  @Override
  public void finish() {
    if (!Db.isInTransaction()) {
      throw new IllegalStateException("Not in transaction");
    }
    DSLContext ctx = Db.getDSLContext();
    Set<BurstKey> keySet = getBatch().keySet();
    if (!keySet.isEmpty()) {
      UpdateQuery updateQuery = ctx.updateQuery(tableClass);
      updateQuery.addValue(tableClass.field("latest", Boolean.class), false);
      Arrays.asList(dbKeyFactory.getPKColumns()).forEach(idColumn->updateQuery.addConditions(tableClass.field(idColumn, Long.class).eq(0L)));
      updateQuery.addConditions(tableClass.field("latest", Boolean.class).isTrue());

      BatchBindStep updateBatch = ctx.batch(updateQuery);
      for (BurstKey dbKey : keySet) {
        ArrayList<Object> bindArgs = new ArrayList<>();
        bindArgs.add(false);
        Arrays.stream(dbKey.getPKValues()).forEach(bindArgs::add);
        updateBatch = updateBatch.bind(bindArgs.toArray());
      }
      updateBatch.execute();
    }

    Map<BurstKey, T> entries = getBatch();
    HashMap<BurstKey, T> itemOf = new HashMap<>();
    for (Map.Entry<BurstKey, T> entry : entries.entrySet()) {
      if (entry.getValue() != null) {
        itemOf.put(entry.getKey(), entry.getValue());
      }
    }
    if (itemOf.size() > 0 ) {
      bulkInsert(ctx, new ArrayList<>(itemOf.values()));
    }
    getBatch().clear();
  }

  @Override
  public T get(BurstKey dbKey, int height) {
    if(Db.isInTransaction()) {
      throw new IllegalStateException("Cannot use in batch table transaction");
    }
    return super.get(dbKey, height);
  }

  @Override
  public T getBy(Condition condition) {
    if(Db.isInTransaction()) {
      throw new IllegalStateException("Cannot use in batch table transaction");
    }
    return super.getBy(condition);
  }

  @Override
  public T getBy(Condition condition, int height) {
    if(Db.isInTransaction()) {
      throw new IllegalStateException("Cannot use in batch table transaction");
    }
    return super.getBy(condition, height);
  }

  @Override
  public BurstIterator<T> getManyBy(Condition condition, int from, int to) {
    if(Db.isInTransaction()) {
      throw new IllegalStateException("Cannot use in batch table transaction");
    }
    return super.getManyBy(condition, from, to);
  }

  @Override
  public BurstIterator<T> getManyBy(Condition condition, int from, int to, List<SortField<?>> sort) {
    if(Db.isInTransaction()) {
      throw new IllegalStateException("Cannot use in batch table transaction");
    }
    return super.getManyBy(condition, from, to, sort);
  }

  @Override
  public BurstIterator<T> getManyBy(Condition condition, int height, int from, int to) {
    if(Db.isInTransaction()) {
      throw new IllegalStateException("Cannot use in batch table transaction");
    }
    return super.getManyBy(condition, height, from, to);
  }

  @Override
  public BurstIterator<T> getManyBy(Condition condition, int height, int from, int to, List<SortField<?>> sort) {
    if(Db.isInTransaction()) {
      throw new IllegalStateException("Cannot use in batch table transaction");
    }
    return super.getManyBy(condition, height, from, to, sort);
  }

  @Override
  public BurstIterator<T> getManyBy(DSLContext ctx, SelectQuery query, boolean cache) {
    if(Db.isInTransaction()) {
      throw new IllegalStateException("Cannot use in batch table transaction");
    }
    return super.getManyBy(ctx, query, cache);
  }

  @Override
  public BurstIterator<T> getAll(int from, int to) {
    if(Db.isInTransaction()) {
      throw new IllegalStateException("Cannot use in batch table transaction");
    }
    return super.getAll(from, to);
  }

  @Override
  public BurstIterator<T> getAll(int from, int to, List<SortField<?>> sort) {
    if(Db.isInTransaction()) {
      throw new IllegalStateException("Cannot use in batch table transaction");
    }
    return super.getAll(from, to, sort);
  }

  @Override
  public BurstIterator<T> getAll(int height, int from, int to) {
    if(Db.isInTransaction()) {
      throw new IllegalStateException("Cannot use in batch table transaction");
    }
    return super.getAll(height, from, to);
  }

  @Override
  public BurstIterator<T> getAll(int height, int from, int to, List<SortField<?>> sort) {
    if(Db.isInTransaction()) {
      throw new IllegalStateException("Cannot use in batch table transaction");
    }
    return super.getAll(height, from, to, sort);
  }

  @Override
  public int getCount() {
    if(Db.isInTransaction()) {
      throw new IllegalStateException("Cannot use in batch table transaction");
    }
    return super.getCount();
  }

  @Override
  public int getRowCount() {
    if(Db.isInTransaction()) {
      throw new IllegalStateException("Cannot use in batch table transaction");
    }
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

  @Override
  public void fillCache(ArrayList<Long> ids) {
  }
}
