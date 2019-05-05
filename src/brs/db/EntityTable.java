package brs.db;

import org.jooq.*;

import java.util.Collection;
import java.util.List;

public interface EntityTable<T> extends DerivedTable {
  void checkAvailable(int height);

  T get(BurstKey dbKey);

  T get(BurstKey dbKey, int height);

  T getBy(Condition condition);

  T getBy(Condition condition, int height);

  Collection<T> getManyBy(Condition condition, int from, int to);

  Collection<T> getManyBy(Condition condition, int from, int to, List<SortField<?>> sort);

  Collection<T> getManyBy(Condition condition, int height, int from, int to);

  Collection<T> getManyBy(Condition condition, int height, int from, int to, List<SortField<?>> sort);

  Collection<T> getManyBy(DSLContext ctx, SelectQuery<? extends Record> query, boolean cache);

  Collection<T> getAll(int from, int to);

  Collection<T> getAll(int from, int to, List<SortField<?>> sort);

  Collection<T> getAll(int height, int from, int to);

  Collection<T> getAll(int height, int from, int to, List<SortField<?>> sort);

  int getCount();

  int getRowCount();

  void insert(T t);

  @Override
  void rollback(int height);

  @Override
  void truncate();
}
