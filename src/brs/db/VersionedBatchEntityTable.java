package brs.db;

import org.ehcache.Cache;
import org.jooq.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;

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
  Collection<T> getManyBy(Condition condition, int from, int to, List<SortField<?>> sort);

  @Override
  Collection<T> getManyBy(Condition condition, int height, int from, int to);

  @Override
  Collection<T> getManyBy(Condition condition, int height, int from, int to, List<SortField<?>> sort);

  @Override
  Collection<T> getManyBy(DSLContext ctx, SelectQuery<? extends Record> query, boolean cache);

  @Override
  Collection<T> getAll(int from, int to);

  @Override
  Collection<T> getAll(int from, int to, List<SortField<?>> sort);

  @Override
  Collection<T> getAll(int height, int from, int to);

  @Override
  Collection<T> getAll(int height, int from, int to, List<SortField<?>> sort);

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
}
