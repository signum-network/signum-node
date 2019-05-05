package brs.db;

import org.ehcache.Cache;
import org.jooq.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface VersionedBatchEntityTable<T> extends DerivedTable, EntityTable<T> {
  boolean delete(T t);

  @Override
  T get(BurstKey dbKey);

  @Override
  void insert(T t);

  @Override
  void finish();

  @Override
  T get(BurstKey dbKey, int height);

  @Override
  T getBy(Condition condition);

  @Override
  T getBy(Condition condition, int height);

  @Override
  BurstIterator<T> getManyBy(Condition condition, int from, int to, List<SortField<?>> sort);

  @Override
  BurstIterator<T> getManyBy(Condition condition, int height, int from, int to);

  @Override
  BurstIterator<T> getManyBy(Condition condition, int height, int from, int to, List<SortField<?>> sort);

  @Override
  BurstIterator<T> getManyBy(DSLContext ctx, SelectQuery<? extends Record> query, boolean cache);

  @Override
  BurstIterator<T> getAll(int from, int to);

  @Override
  BurstIterator<T> getAll(int from, int to, List<SortField<?>> sort);

  @Override
  BurstIterator<T> getAll(int height, int from, int to);

  @Override
  BurstIterator<T> getAll(int height, int from, int to, List<SortField<?>> sort);

  @Override
  int getCount();

  @Override
  int getRowCount();

  @Override
  void rollback(int height);

  @Override
  void truncate();

  Map<BurstKey, T> getBatch();

  Cache getCache();

  void flushCache();

  void fillCache(Set<Long> ids);
}
