package brs.db.sql;

import brs.db.SignumKey;
import brs.db.VersionedBatchEntityTable;
import brs.db.cache.DBCacheManagerImpl;
import brs.db.store.DerivedTableManager;
import org.ehcache.Cache;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;

import java.util.*;
import java.util.stream.Collectors;

public abstract class VersionedBatchEntitySqlTable<T> extends VersionedEntitySqlTable<T> implements VersionedBatchEntityTable<T> {

  private final DBCacheManagerImpl dbCacheManager;
  private final Class<T> tClass;

  VersionedBatchEntitySqlTable(String table, TableImpl<?> tableClass, DbKey.Factory<T> dbKeyFactory, DerivedTableManager derivedTableManager, DBCacheManagerImpl dbCacheManager, Class<T> tClass) {
    super(table, tableClass, dbKeyFactory, derivedTableManager);
    this.dbCacheManager = dbCacheManager;
    this.tClass = tClass;
  }

  private void assertInTransaction() {
    if (Db.isInTransaction()) {
      throw new IllegalStateException("Cannot use in batch table transaction");
    }
  }

  private void assertNotInTransaction() {
    if (!Db.isInTransaction()) {
      throw new IllegalStateException("Not in transaction");
    }
  }

  protected abstract void bulkInsert(DSLContext ctx, Collection<T> t);

  @Override
  public boolean delete(T t) {
    assertNotInTransaction();
    DbKey dbKey = (DbKey) dbKeyFactory.newKey(t);
    getCache().remove(dbKey);
    getBatch().remove(dbKey);
    return true;
  }

  @Override
  public T get(SignumKey dbKey) {
    if (getCache().containsKey(dbKey)) {
      return getCache().get(dbKey);
    } else if (Db.isInTransaction() && getBatch().containsKey(dbKey)) {
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
    // read-only op...good to use getBatch outside transactional scope
    Set<SignumKey> keySet = getBatch().keySet();
    if (keySet.isEmpty()) {
      return;
    }

    // As recommended for databases,
    // not more than 1000 items should be put in subqueries
    int UpdateMaxBatchSize = 1_000;

    // As recommended for databases,
    // not more than 1000 items should be inserted in a single batch
    /**
     * The batch size is set to 1000, which is a common practice for bulk inserts.
     */
    int InsertMaxBatchSize = 1_000;

    Db.useDSLContext(ctx -> {

      Field<Long> idField = tableClass.field(dbKeyFactory.getPKColumns()[0], Long.class);
      List<Long> ids = keySet.stream()
        .map(k -> k.getPKValues()[0])
        .collect(Collectors.toList());
      // transactional scope avoids auto-commits, giving us even more performance back
      ctx.transaction(configuration -> {
        DSLContext txContext = DSL.using(configuration);
        int idsListSize = ids.size();
        for (int from = 0; from < idsListSize; from += UpdateMaxBatchSize) {
          int to = Math.min(from + UpdateMaxBatchSize, idsListSize);
          txContext.update(tableClass)
            .set(latestField, false)
            .where(latestField.isTrue())
            .and(idField.in(ids.subList(from, to)))
            .execute();
        }

        /**
         * Bulk insert of new entities.
         * This is done in batches to avoid memory issues with large datasets.
         * The entities are collected from the batch and inserted in sublists of size InsertMaxBatchSize.
         * This allows us to insert a large number of entities without running into memory issues.
         * This is a mutation operation, so it must be inside the transactional scope.
         */
        List<T> entitiesToInsert = new ArrayList<>(getBatch().values());

        int entitiesListSize = entitiesToInsert.size();
        for (int from = 0; from < entitiesListSize; from += InsertMaxBatchSize) {
          int to = Math.min(from + InsertMaxBatchSize, entitiesListSize);
          bulkInsert(txContext, entitiesToInsert.subList(from, to));
        }

        // mutation, so must be inside the transactional scope
        getBatch().clear();
      });
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
